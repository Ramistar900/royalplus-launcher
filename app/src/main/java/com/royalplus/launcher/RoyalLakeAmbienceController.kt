package com.royalplus.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Plays the natural stream recording only while a Lake wallpaper is visible.
 * The Compose owner stops it when the launcher leaves the foreground.
 */
internal object RoyalLakeAmbienceController {
    private const val AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.water.stream.ogg"
    private const val VOLUME = 0.20f

    private var player: MediaPlayer? = null

    fun sync(context: Context, shouldPlay: Boolean) {
        if (!shouldPlay) {
            stop()
            return
        }
        if (player != null) return

        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            created.setDataSource(AUDIO_URL)
            created.isLooping = true
            created.setVolume(VOLUME, VOLUME)
            created.setOnPreparedListener { prepared ->
                if (player !== prepared) {
                    runCatching { prepared.release() }
                } else {
                    runCatching { prepared.start() }
                        .onFailure { release(prepared) }
                }
            }
            created.setOnErrorListener { failed, _, _ ->
                release(failed)
                true
            }
            created.prepareAsync()
        } catch (_: Exception) {
            release(created)
        }
    }

    fun stop() {
        val active = player ?: return
        player = null
        runCatching {
            active.setOnPreparedListener(null)
            active.setOnCompletionListener(null)
            active.setOnErrorListener(null)
            if (active.isPlaying) active.stop()
            active.release()
        }
    }

    private fun release(target: MediaPlayer) {
        if (player === target) player = null
        runCatching {
            target.setOnPreparedListener(null)
            target.setOnCompletionListener(null)
            target.setOnErrorListener(null)
            target.release()
        }
    }
}
