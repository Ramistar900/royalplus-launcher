package com.royalplus.launcher

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.IceCandidateErrorEvent
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.VideoTrack
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared WebRTC setup. No STUN/TURN servers are configured: ROYAL CAST is
 * deliberately limited to direct connections on the same local network.
 */
internal object RoyalCastRtc {
    private val initialized = AtomicBoolean(false)
    @Volatile private var eglBase: EglBase? = null
    @Volatile private var peerFactory: PeerConnectionFactory? = null

    @Synchronized
    fun initialize(context: Context): PeerConnectionFactory {
        peerFactory?.let { return it }
        if (initialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions()
            )
        }
        val egl = EglBase.create()
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                org.webrtc.DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(
                org.webrtc.DefaultVideoDecoderFactory(egl.eglBaseContext)
            )
            .createPeerConnectionFactory()
        eglBase = egl
        peerFactory = factory
        return factory
    }

    fun eglContext(): EglBase.Context =
        checkNotNull(eglBase) { "ROYAL CAST video engine is not initialized." }.eglBaseContext

    fun createPeerConnection(
        context: Context,
        observer: PeerConnection.Observer
    ): PeerConnection {
        val factory = initialize(context)
        val configuration = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return checkNotNull(factory.createPeerConnection(configuration, observer)) {
            "Could not create a local WebRTC connection."
        }
    }

    fun await(latch: CountDownLatch, timeoutSeconds: Long = 20): Boolean =
        latch.await(timeoutSeconds, TimeUnit.SECONDS)
}

internal open class RoyalCastPeerObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
    override fun onIceCandidate(candidate: IceCandidate?) = Unit
    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
    override fun onAddStream(stream: MediaStream?) = Unit
    override fun onRemoveStream(stream: MediaStream?) = Unit
    override fun onDataChannel(channel: DataChannel?) = Unit
    override fun onRenegotiationNeeded() = Unit
    override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) = Unit
    override fun onTrack(transceiver: RtpTransceiver?) = Unit
    override fun onStandardizedIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
    override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) = Unit
    override fun onIceCandidateError(event: IceCandidateErrorEvent?) = Unit
    override fun onRemoveTrack(receiver: RtpReceiver?) = Unit
}
