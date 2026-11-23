package com.royalplus.launcher

import androidx.compose.ui.input.key.Key

internal enum class RemoteKey {
    MENU, BACK, SEARCH, VOICE, INFO, GUIDE, INPUT,
    PLAY_PAUSE, PLAY, PAUSE, STOP, REWIND, FAST_FORWARD, NEXT, PREVIOUS,
    CHANNEL_UP, CHANNEL_DOWN, RED, GREEN, YELLOW, BLUE
}

internal object RemoteKeys {
    fun fromComposeKey(key: Key): RemoteKey? = when (key) {
        Key.Menu -> RemoteKey.MENU
        Key.Back -> RemoteKey.BACK
        Key.Search -> RemoteKey.SEARCH
        Key.VoiceAssist -> RemoteKey.VOICE
        Key.Info -> RemoteKey.INFO
        Key.Guide -> RemoteKey.GUIDE
        Key.TvInput -> RemoteKey.INPUT
        Key.MediaPlayPause -> RemoteKey.PLAY_PAUSE
        Key.MediaPlay -> RemoteKey.PLAY
        Key.MediaPause -> RemoteKey.PAUSE
        Key.MediaStop -> RemoteKey.STOP
        Key.MediaRewind -> RemoteKey.REWIND
        Key.MediaFastForward -> RemoteKey.FAST_FORWARD
        Key.MediaNext -> RemoteKey.NEXT
        Key.MediaPrevious -> RemoteKey.PREVIOUS
        Key.ChannelUp -> RemoteKey.CHANNEL_UP
        Key.ChannelDown -> RemoteKey.CHANNEL_DOWN
        Key.ProgramRed -> RemoteKey.RED
        Key.ProgramGreen -> RemoteKey.GREEN
        Key.ProgramYellow -> RemoteKey.YELLOW
        Key.ProgramBlue -> RemoteKey.BLUE
        else -> null
    }

    fun digitOf(key: Key): Int? = when (key) {
        Key.Zero -> 0
        Key.One -> 1
        Key.Two -> 2
        Key.Three -> 3
        Key.Four -> 4
        Key.Five -> 5
        Key.Six -> 6
        Key.Seven -> 7
        Key.Eight -> 8
        Key.Nine -> 9
        else -> null
    }
}
