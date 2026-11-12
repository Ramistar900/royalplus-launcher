package com.royalplus.launcher

import android.view.KeyEvent

internal enum class RemoteKey {
    MENU, BACK, SEARCH, VOICE, INFO, GUIDE, INPUT,
    PLAY_PAUSE, PLAY, PAUSE, STOP, REWIND, FAST_FORWARD, NEXT, PREVIOUS,
    CHANNEL_UP, CHANNEL_DOWN, RED, GREEN, YELLOW, BLUE
}

internal object RemoteKeys {
    fun fromKeyCode(keyCode: Int): RemoteKey? = when (keyCode) {
        KeyEvent.KEYCODE_MENU -> RemoteKey.MENU
        KeyEvent.KEYCODE_BACK -> RemoteKey.BACK
        KeyEvent.KEYCODE_SEARCH -> RemoteKey.SEARCH
        KeyEvent.KEYCODE_VOICE_ASSIST -> RemoteKey.VOICE
        KeyEvent.KEYCODE_INFO -> RemoteKey.INFO
        KeyEvent.KEYCODE_GUIDE -> RemoteKey.GUIDE
        KeyEvent.KEYCODE_TV_INPUT -> RemoteKey.INPUT
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> RemoteKey.PLAY_PAUSE
        KeyEvent.KEYCODE_MEDIA_PLAY -> RemoteKey.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteKey.PAUSE
        KeyEvent.KEYCODE_MEDIA_STOP -> RemoteKey.STOP
        KeyEvent.KEYCODE_MEDIA_REWIND -> RemoteKey.REWIND
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> RemoteKey.FAST_FORWARD
        KeyEvent.KEYCODE_MEDIA_NEXT -> RemoteKey.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> RemoteKey.PREVIOUS
        KeyEvent.KEYCODE_CHANNEL_UP -> RemoteKey.CHANNEL_UP
        KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteKey.CHANNEL_DOWN
        KeyEvent.KEYCODE_PROG_RED -> RemoteKey.RED
        KeyEvent.KEYCODE_PROG_GREEN -> RemoteKey.GREEN
        KeyEvent.KEYCODE_PROG_YELLOW -> RemoteKey.YELLOW
        KeyEvent.KEYCODE_PROG_BLUE -> RemoteKey.BLUE
        else -> null
    }

    fun digitOf(keyCode: Int): Int? =
        if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
            keyCode - KeyEvent.KEYCODE_0
        } else null
}
