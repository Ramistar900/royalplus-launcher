package com.royalplus.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Longer prepared-loop fireplace ambience from SFXMint, dedicated to CC0 1.0:
 * https://sfxmint.com/sounds/ambience-fireplace-50
 * The longer prepared loop reduces abrupt, repetitive short-fire restarts.
 */
internal object RoyalFireAmbienceController {
    private const val ASSET_NAME = "fireplace_audio.mp3"
    private const val VOLUME = 0.18f

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
            context.applicationContext.assets.openFd(ASSET_NAME).use { asset ->
                created.setDataSource(asset.fileDescriptor, asset.startOffset, asset.length)
            }
            created.isLooping = true
            created.setVolume(VOLUME * RoyalWeatherAmbienceController.volume(context), VOLUME * RoyalWeatherAmbienceController.volume(context))
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

    fun setVolume(value: Float) {
        val level = VOLUME * value.coerceIn(0f, 1f)
        runCatching { player?.setVolume(level, level) }
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
