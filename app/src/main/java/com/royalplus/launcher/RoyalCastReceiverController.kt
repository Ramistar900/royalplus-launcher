package com.royalplus.launcher

import android.content.Context
import org.json.JSONObject
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class RoyalCastReceiverPhase {
    STARTING, WAITING, CONNECTING, CONNECTED, ERROR
}

internal data class RoyalCastReceiverState(
    val phase: RoyalCastReceiverPhase = RoyalCastReceiverPhase.STARTING,
    val message: String = "يجري تجهيز استقبال الشاشة",
    val host: String? = null,
    val port: Int? = null,
    val token: String? = null,
    val pin: String? = null
) {
    val pairingPayload: String?
        get() {
            val currentHost = host ?: return null
            val currentPort = port ?: return null
            val currentToken = token ?: return null
            val currentPin = pin ?: return null
            return RoyalCastPairing.createPayload(currentHost, currentPort, currentToken, currentPin)
        }
}

/**
 * The TV opens a TCP signaling socket on its active private LAN address.
 * Screen media is then carried by a direct WebRTC peer connection (DTLS-SRTP).
 */
internal class RoyalCastReceiverController(
    private val context: Context
) {
    private val mutableState = MutableStateFlow(RoyalCastReceiverState())
    val state: StateFlow<RoyalCastReceiverState> = mutableState.asStateFlow()
    private val stopped = AtomicBoolean(false)
    private val generation = AtomicInteger(0)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var remoteVideoTrack: VideoTrack? = null
    @Volatile private var renderer: SurfaceViewRenderer? = null
    @Volatile private var iceGatheringLatch: CountDownLatch? = null

    fun start() {
        if (serverSocket != null) return
        stopped.set(false)
        val attempt = generation.incrementAndGet()
        mutableState.value = RoyalCastReceiverState(
            phase = RoyalCastReceiverPhase.STARTING,
            message = "يجري تشغيل مستقبل ROYAL CAST"
        )
        Thread({ openReceiver(attempt) }, "RoyalCast-Receiver").apply {
            isDaemon = true
            start()
        }
    }

    fun attachRenderer(surface: SurfaceViewRenderer) {
        if (renderer === surface) return
        renderer?.let { old -> remoteVideoTrack?.removeSink(old) }
        renderer = surface
        remoteVideoTrack?.addSink(surface)
    }

    fun detachRenderer(surface: SurfaceViewRenderer? = renderer) {
        val current = renderer
        if (surface != null && current !== surface) return
        if (current != null) remoteVideoTrack?.removeSink(current)
        renderer = null
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        generation.incrementAndGet()
        val oldServer = serverSocket
        serverSocket = null
        runCatching { oldServer?.close() }
        detachRenderer()
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        remoteVideoTrack = null
        iceGatheringLatch = null
        mutableState.value = RoyalCastReceiverState(
            phase = RoyalCastReceiverPhase.STARTING,
            message = "تم إيقاف استقبال الشاشة"
        )
    }

    private fun isCurrent(attempt: Int): Boolean =
        !stopped.get() && generation.get() == attempt

    private fun openReceiver(attempt: Int) {
        var openedSocket: ServerSocket? = null
        try {
            val host = RoyalCastPairing.localIpv4Address(context)
                ?: error("لم نعثر على عنوان Wi-Fi أو Ethernet محلي. تأكد من اتصال التلفاز بالشبكة.")
            val token = RoyalCastPairing.newToken()
            val pin = RoyalCastPairing.newPin()
            val socket = ServerSocket()
            openedSocket = socket
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(InetAddress.getByName(host), 0), 2)
            socket.soTimeout = 0
            if (!isCurrent(attempt)) return
            serverSocket = socket
            mutableState.value = RoyalCastReceiverState(
                phase = RoyalCastReceiverPhase.WAITING,
                message = "افتح ROYAL CAST على الهاتف وامسح رمز الاقتران",
                host = host,
                port = socket.localPort,
                token = token,
                pin = pin
            )
            while (isCurrent(attempt)) {
                val client = socket.accept()
                client.tcpNoDelay = true
                client.soTimeout = 30_000
                if (!isCurrent(attempt)) {
                    runCatching { client.close() }
                    break
                }
                if (handleClient(client, token, pin, attempt)) return
                runCatching { client.close() }
            }
        } catch (error: Exception) {
            if (isCurrent(attempt)) {
                setError(error.message ?: "تعذر تشغيل مستقبل ROYAL CAST.")
            }
        } finally {
            if (serverSocket === openedSocket) serverSocket = null
            runCatching { openedSocket?.close() }
        }
    }

    private fun handleClient(
        client: Socket,
        expectedToken: String,
        expectedPin: String,
        attempt: Int
    ): Boolean {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
        val writer = PrintWriter(OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)
        return try {
            val raw = reader.readLine() ?: return false
            require(raw.length <= 1_500_000) { "طلب الاقتران أكبر من الحد المسموح." }
            val request = JSONObject(raw)
            require(request.optString("type") == "offer") { "نوع طلب البث غير صحيح." }
            require(request.optString("token") == expectedToken) { "رمز الاقتران غير صحيح." }
            require(request.optString("pin") == expectedPin) { "رقم التأكيد غير صحيح." }
            val offerSdp = request.optString("sdp")
            require(offerSdp.isNotBlank() && offerSdp.length <= 1_400_000) { "بيانات العرض غير صالحة." }

            if (!isCurrent(attempt)) return false
            mutableState.value = mutableState.value.copy(
                phase = RoyalCastReceiverPhase.CONNECTING,
                message = "تم الاقتران، يجري إنشاء اتصال الشاشة"
            )
            val connection = RoyalCastRtc.createPeerConnection(context, receiverObserver(attempt))
            peerConnection = connection

            val remoteSet = CountDownLatch(1)
            var remoteError: String? = null
            connection.setRemoteDescription(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onSetSuccess() { remoteSet.countDown() }
                override fun onCreateFailure(error: String?) { remoteError = error; remoteSet.countDown() }
                override fun onSetFailure(error: String?) { remoteError = error; remoteSet.countDown() }
            }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
            check(RoyalCastRtc.await(remoteSet)) { "انتهت مهلة قراءة عرض الهاتف." }
            check(remoteError == null) { remoteError ?: "تعذر قبول عرض الهاتف." }

            val answerLatch = CountDownLatch(1)
            var answer: SessionDescription? = null
            var answerError: String? = null
            connection.createAnswer(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) {
                    answer = description
                    answerLatch.countDown()
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String?) { answerError = error; answerLatch.countDown() }
                override fun onSetFailure(error: String?) { answerError = error; answerLatch.countDown() }
            }, org.webrtc.MediaConstraints())
            check(RoyalCastRtc.await(answerLatch)) { "انتهت مهلة إنشاء اتصال التلفاز." }
            check(answerError == null && answer != null) { answerError ?: "تعذر إنشاء اتصال التلفاز." }
            val generatedAnswer = checkNotNull(answer)

            val iceComplete = CountDownLatch(1)
            iceGatheringLatch = iceComplete
            val localSet = CountDownLatch(1)
            var localError: String? = null
            connection.setLocalDescription(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(description: SessionDescription?) = Unit
                override fun onSetSuccess() { localSet.countDown() }
                override fun onCreateFailure(error: String?) { localError = error; localSet.countDown() }
                override fun onSetFailure(error: String?) { localError = error; localSet.countDown() }
            }, generatedAnswer)
            check(RoyalCastRtc.await(localSet)) { "انتهت مهلة تجهيز استقبال الشاشة." }
            check(localError == null) { localError ?: "تعذر تجهيز استقبال الشاشة." }

            if (connection.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
                iceComplete.countDown()
            }
            check(RoyalCastRtc.await(iceComplete, 25)) { "تعذر تجهيز مسار Wi-Fi المحلي." }
            if (!isCurrent(attempt)) return false
            val finalSdp = connection.localDescription?.description.orEmpty()
            check(finalSdp.isNotBlank()) { "لم يتم إنشاء وصف الاتصال." }
            writer.println(JSONObject().put("type", "answer").put("sdp", finalSdp).toString())
            check(!writer.checkError()) { "انقطع اتصال الاقتران قبل اكتماله." }
            true
        } catch (error: Exception) {
            if (isCurrent(attempt)) {
                val message = error.message ?: "فشل اتصال الهاتف."
                if (message.contains("رمز الاقتران") || message.contains("رقم التأكيد")) {
                    mutableState.value = mutableState.value.copy(
                        phase = RoyalCastReceiverPhase.WAITING,
                        message = "رمز التأكيد غير صحيح. امسح رمز التلفاز الظاهر وحاول مجدداً."
                    )
                } else {
                    setError(message)
                }
            }
            false
        } finally {
            runCatching { client.close() }
        }
    }

    private fun receiverObserver(attempt: Int) = object : RoyalCastPeerObserver() {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (isCurrent(attempt) && state == PeerConnection.IceGatheringState.COMPLETE) {
                iceGatheringLatch?.countDown()
            }
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (!isCurrent(attempt)) return
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> mutableState.value =
                    mutableState.value.copy(
                        phase = RoyalCastReceiverPhase.CONNECTED,
                        message = "البث يعمل الآن من الهاتف"
                    )
                PeerConnection.IceConnectionState.FAILED -> setError(
                    "تعذر إنشاء اتصال مباشر. تحقق من أن الجهازين على شبكة Wi-Fi نفسها."
                )
                PeerConnection.IceConnectionState.DISCONNECTED -> mutableState.value =
                    mutableState.value.copy(
                        phase = RoyalCastReceiverPhase.CONNECTING,
                        message = "انقطع الاتصال مؤقتاً، يجري إعادة المحاولة"
                    )
                else -> Unit
            }
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            val track = receiver?.track() as? VideoTrack ?: return
            attachRemoteTrack(track, attempt)
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track() as? VideoTrack ?: return
            attachRemoteTrack(track, attempt)
        }
    }

    private fun attachRemoteTrack(track: VideoTrack, attempt: Int) {
        if (!isCurrent(attempt) || remoteVideoTrack === track) return
        remoteVideoTrack?.let { old -> renderer?.let(old::removeSink) }
        remoteVideoTrack = track
        renderer?.let(track::addSink)
    }

    private fun setError(message: String) {
        mutableState.value = mutableState.value.copy(
            phase = RoyalCastReceiverPhase.ERROR,
            message = message
        )
    }
}
