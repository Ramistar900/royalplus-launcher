package com.royalplus.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/** Plays non-human ocean-wave ambience only while an Ocean wallpaper is visible. */
internal object RoyalOceanAmbienceController {
    private const val AUDIO_URL =
        "https://ramistar900.github.io/royal-audio-player/assets/ogg/nature.ocean.waves.ogg"
    private const val VOLUME = 0.24f
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
                    runCatching { prepared.start() }.onFailure { release(prepared) }
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
