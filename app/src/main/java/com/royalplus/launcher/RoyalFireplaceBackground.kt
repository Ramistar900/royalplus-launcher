package com.royalplus.launcher

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun RoyalFireplaceBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(context) { RoyalFireplaceVideoPlayer(context) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextureView(viewContext).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        player.attach(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        player.detach()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = {}
    )

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> player.resume()
                Lifecycle.Event.ON_PAUSE -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            player.resume()
        } else {
            player.pause()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
}

private class RoyalFireplaceVideoPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var videoSurface: Surface? = null
    private var isPrepared = false
    private var isResumed = true

    fun attach(surfaceTexture: SurfaceTexture) {
        releasePlayer()
        val surface = Surface(surfaceTexture)
        videoSurface = surface
        val player = MediaPlayer()
        mediaPlayer = player

        try {
            appContext.assets.openFd("fireplace_burning_fire_loop.mp4").use { asset ->
                player.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            player.setSurface(surface)
            player.isLooping = true
            player.setVolume(0f, 0f)
            player.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer === preparedPlayer) {
                    isPrepared = true
                    if (isResumed) runCatching { preparedPlayer.start() }
                }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                if (mediaPlayer === failedPlayer) releasePlayer()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    fun resume() {
        isResumed = true
        if (isPrepared) {
            mediaPlayer?.let { active ->
                if (!active.isPlaying) runCatching { active.start() }
            }
        }
    }

    fun pause() {
        isResumed = false
        if (isPrepared) {
            mediaPlayer?.let { active ->
                if (active.isPlaying) runCatching { active.pause() }
            }
        }
    }

    fun detach() {
        releasePlayer()
    }

    fun release() {
        releasePlayer()
    }

    private fun releasePlayer() {
        isPrepared = false
        val active = mediaPlayer
        mediaPlayer = null
        if (active != null) {
            runCatching {
                active.setOnPreparedListener(null)
                active.setOnErrorListener(null)
                if (active.isPlaying) active.stop()
                active.release()
            }
        }
        videoSurface?.release()
        videoSurface = null
    }
}

@Composable
internal fun RoyalFireplaceAudioEffect(enabled: Boolean) {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner, context) {
        var player: MediaPlayer? = null

        fun stopAudio() {
            val active = player
            player = null
            if (active != null) {
                runCatching {
                    active.setOnPreparedListener(null)
                    active.setOnErrorListener(null)
                    if (active.isPlaying) active.stop()
                    active.release()
                }
            }
        }

        fun startAudio() {
            if (!enabled || player != null) return
            val created = MediaPlayer()
            player = created
            try {
                created.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                val localAudio = File(context.cacheDir, "fireplace_audio.mp3")
                if (!localAudio.isFile || localAudio.length() == 0L) {
                    context.assets.open("fireplace_audio.mp3").use { input ->
                        FileOutputStream(localAudio).use { output -> input.copyTo(output) }
                    }
                }
                created.setDataSource(localAudio.absolutePath)
                created.isLooping = true
                created.setVolume(0.18f, 0.18f)
                created.setOnPreparedListener { preparedPlayer ->
                    if (player === preparedPlayer &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    ) {
                        runCatching { preparedPlayer.start() }
                    }
                }
                created.setOnErrorListener { failedPlayer, _, _ ->
                    if (player === failedPlayer) player = null
                    runCatching { failedPlayer.release() }
                    true
                }
                created.prepareAsync()
            } catch (_: Exception) {
                if (player === created) player = null
                runCatching { created.release() }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> startAudio()
                Lifecycle.Event.ON_PAUSE -> stopAudio()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            startAudio()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopAudio()
        }
    }
}

