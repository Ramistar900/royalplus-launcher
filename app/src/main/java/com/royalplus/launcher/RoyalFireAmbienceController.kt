package com.royalplus.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Bundled prepared-loop fireplace ambience from SFXMint (CC0 1.0):
 * https://sfxmint.com/sounds/ambience-fireplace-50
 * Keeping the audio in app assets avoids network-dependent playback.
 */
internal object RoyalFireAmbienceController {
    private const val AUDIO_ASSET = "fireplace_ambience.mp3"
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
            context.applicationContext.assets.openFd(AUDIO_ASSET).use { audio ->
                created.setDataSource(audio.fileDescriptor, audio.startOffset, audio.length)
            }
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
