package com.royalplus.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

internal object RoyalCastSenderStatus {
    var message by mutableStateOf("لم يبدأ البث بعد")
        private set

    fun update(value: String) {
        message = value
    }
}

class RoyalCastSenderService : Service() {
    companion object {
        const val ACTION_START = "com.royalplus.launcher.royalcast.START"
        const val ACTION_STOP = "com.royalplus.launcher.royalcast.STOP"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_PIN = "pin"
        const val EXTRA_RESULT_CODE = "projection_result_code"
        const val EXTRA_PROJECTION_DATA = "projection_result_data"
        private const val CHANNEL_ID = "royal_cast_screen_mirroring"
        private const val NOTIFICATION_ID = 7402
    }

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "RoyalCast-Sender").apply { isDaemon = true }
    }

    @Volatile private var peerConnection: PeerConnection? = null
    @Volatile private var capturer: ScreenCapturerAndroid? = null
    @Volatile private var videoSource: VideoSource? = null
    @Volatile private var videoTrack: VideoTrack? = null
    @Volatile private var textureHelper: SurfaceTextureHelper? = null
    @Volatile private var isStopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                RoyalCastSenderStatus.update("تم إيقاف بث الشاشة")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                if (isStopping) return START_NOT_STICKY
                startAsForeground()
                RoyalCastSenderStatus.update("يجري الاتصال بالتلفاز...")
                worker.execute { connectAndStream(intent) }
                return START_NOT_STICKY
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun startAsForeground() {
        createNotificationChannel()
        val stopIntent = Intent(this, RoyalCastSenderService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.royal_screen_cast)
            .setContentTitle("ROYAL CAST")
            .setContentText("يتم بث شاشة الهاتف إلى التلفاز")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "إيقاف البث", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "ROYAL CAST",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار مستمر أثناء بث شاشة الهاتف"
                setShowBadge(false)
            }
        )
    }

    private fun connectAndStream(startIntent: Intent) {
        var socket: Socket? = null
        try {
            val host = startIntent.getStringExtra(EXTRA_HOST).orEmpty()
            val port = startIntent.getIntExtra(EXTRA_PORT, -1)
            val token = startIntent.getStringExtra(EXTRA_TOKEN).orEmpty()
            val pin = startIntent.getStringExtra(EXTRA_PIN).orEmpty()
            val resultCode = startIntent.getIntExtra(EXTRA_RESULT_CODE, 0)
            @Suppress("DEPRECATION")
            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                startIntent.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                startIntent.getParcelableExtra(EXTRA_PROJECTION_DATA)
            }
            require(host.isNotBlank() && port in 1..65535) { "بيانات التلفاز غير صالحة." }
            require(token.matches(Regex("[a-fA-F0-9]{32}")) && pin.matches(Regex("[0-9]{6}"))) {
                "رمز الاقتران غير صالح."
            }
            require(resultCode == RESULT_OK_VALUE && projectionData != null) {
                "لم يصل إذن مشاركة الشاشة من النظام."
            }

            val rtcFactory = RoyalCastRtc.initialize(applicationContext)
            val connection = RoyalCastRtc.createPeerConnection(
                applicationContext,
                senderObserver()
            )
            peerConnection = connection

            val source = rtcFactory.createVideoSource(true)
            videoSource = source
            val capture = ScreenCapturerAndroid(
                projectionData,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        RoyalCastSenderStatus.update("تم إيقاف مشاركة الشاشة من النظام")
                        stopSelf()
                    }
                }
            )
            capturer = capture
            val eglContext = RoyalCastRtc.eglContext()
            val helper = SurfaceTextureHelper.create("RoyalCastScreenCapture", eglContext)
            textureHelper = helper
            capture.initialize(helper, applicationContext, source.capturerObserver)
            capture.startCapture(screenWidth(), screenHeight(), 24)

            val track = rtcFactory.createVideoTrack("royalcast-phone-screen", source)
            videoTrack = track
            track.setEnabled(true)
            connection.addTrack(track, listOf("royalcast-phone-screen"))

            val offer = createOffer(connection)
            setLocalDescription(connection, offer)
            waitForIceGathering(connection)

            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 12_000)
            socket.soTimeout = 30_000
            socket.tcpNoDelay = true
            val writer = PrintWriter(
                OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
            )
            val reader = BufferedReader(
                InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            )
            val sdp = connection.localDescription?.description.orEmpty()
            require(sdp.isNotBlank()) { "لم يتم تجهيز بيانات بث الهاتف." }
            val request = JSONObject()
                .put("type", "offer")
                .put("token", token)
                .put("pin", pin)
                .put("sdp", sdp)
            writer.println(request.toString())
            require(!writer.checkError()) { "تعذر إرسال طلب الاقتران إلى التلفاز." }

            val responseLine = reader.readLine() ?: error("لم يصل رد التلفاز.")
            require(responseLine.length <= 1_500_000) { "رد التلفاز أكبر من الحد المسموح." }
            val response = JSONObject(responseLine)
            require(response.optString("type") == "answer") { "رد التلفاز غير صالح." }
            val answerSdp = response.optString("sdp")
            require(answerSdp.isNotBlank()) { "لم يرسل التلفاز بيانات الاتصال." }
            setRemoteDescription(connection, SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
            runCatching { socket.close() }
            socket = null
            RoyalCastSenderStatus.update("تم الاقتران. يجري تشغيل بث الشاشة...")
        } catch (error: Exception) {
            if (!isStopping) {
                RoyalCastSenderStatus.update(error.message ?: "تعذر بدء بث الشاشة.")
                stopSelf()
            }
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun createOffer(connection: PeerConnection): SessionDescription {
        val latch = CountDownLatch(1)
        var result: SessionDescription? = null
        var failure: String? = null
        connection.createOffer(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) {
                result = description
                latch.countDown()
            }
            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String?) { failure = error; latch.countDown() }
            override fun onSetFailure(error: String?) { failure = error; latch.countDown() }
        }, MediaConstraints())
        check(RoyalCastRtc.await(latch)) { "انتهت مهلة إنشاء بث الهاتف." }
        check(failure == null && result != null) { failure ?: "تعذر تجهيز عرض الهاتف." }
        return checkNotNull(result)
    }

    private fun setLocalDescription(connection: PeerConnection, description: SessionDescription) {
        val latch = CountDownLatch(1)
        var failure: String? = null
        connection.setLocalDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) = Unit
            override fun onSetSuccess() { latch.countDown() }
            override fun onCreateFailure(error: String?) { failure = error; latch.countDown() }
            override fun onSetFailure(error: String?) { failure = error; latch.countDown() }
        }, description)
        check(RoyalCastRtc.await(latch)) { "انتهت مهلة تجهيز الهاتف للإرسال." }
        check(failure == null) { failure ?: "تعذر تجهيز الهاتف للإرسال." }
    }

    private fun setRemoteDescription(connection: PeerConnection, description: SessionDescription) {
        val latch = CountDownLatch(1)
        var failure: String? = null
        connection.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(description: SessionDescription?) = Unit
            override fun onSetSuccess() { latch.countDown() }
            override fun onCreateFailure(error: String?) { failure = error; latch.countDown() }
            override fun onSetFailure(error: String?) { failure = error; latch.countDown() }
        }, description)
        check(RoyalCastRtc.await(latch)) { "انتهت مهلة قبول رد التلفاز." }
        check(failure == null) { failure ?: "تعذر قبول رد التلفاز." }
    }

    private fun waitForIceGathering(connection: PeerConnection) {
        val observerLatch = iceGatheringLatch
        if (connection.iceGatheringState() == PeerConnection.IceGatheringState.COMPLETE) {
            observerLatch.countDown()
        }
        check(RoyalCastRtc.await(observerLatch, 25)) { "تعذر تجهيز الاتصال المحلي بين الهاتف والتلفاز." }
    }

    private val iceGatheringLatch = CountDownLatch(1)

    private fun senderObserver() = object : RoyalCastPeerObserver() {
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) iceGatheringLatch.countDown()
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED ->
                    RoyalCastSenderStatus.update("البث يعمل الآن على التلفاز")
                PeerConnection.IceConnectionState.FAILED ->
                    RoyalCastSenderStatus.update("فشل مسار البث. تأكد من اتصال الجهازين بالشبكة نفسها.")
                PeerConnection.IceConnectionState.DISCONNECTED ->
                    RoyalCastSenderStatus.update("انقطع الاتصال مؤقتاً، يجري إعادة المحاولة")
                else -> Unit
            }
        }
    }

    private fun screenWidth(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels.coerceAtLeast(640).coerceAtMost(1920)
    }

    private fun screenHeight(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics.heightPixels.coerceAtLeast(360).coerceAtMost(1080)
    }

    override fun onDestroy() {
        isStopping = true
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { videoTrack?.dispose() }
        videoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { textureHelper?.dispose() }
        textureHelper = null
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private companion object {
        const val RESULT_OK_VALUE = -1
    }
}
