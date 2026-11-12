package com.royalplus.launcher

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherRemoteInputTest {
    @Test
    fun normalizesArabicSearchVariants() {
        assertEquals("اااهي", LauncherAppSearch.normalize("أإآةى"))
    }

    @Test
    fun normalizesLatinSearchWithoutLocaleDependence() {
        assertEquals("netflix", LauncherAppSearch.normalize("NETFLIX"))
    }

    @Test
    fun mapsRemoteNavigationAndColorKeys() {
        assertEquals(RemoteKey.MENU, RemoteKeys.fromComposeKey(Key.Menu))
        assertEquals(RemoteKey.BACK, RemoteKeys.fromComposeKey(Key.Back))
        assertEquals(RemoteKey.SEARCH, RemoteKeys.fromComposeKey(Key.Search))
        assertEquals(RemoteKey.VOICE, RemoteKeys.fromComposeKey(Key.VoiceAssist))
        assertEquals(RemoteKey.RED, RemoteKeys.fromComposeKey(Key.ProgramRed))
        assertEquals(RemoteKey.GREEN, RemoteKeys.fromComposeKey(Key.ProgramGreen))
        assertEquals(RemoteKey.YELLOW, RemoteKeys.fromComposeKey(Key.ProgramYellow))
        assertEquals(RemoteKey.BLUE, RemoteKeys.fromComposeKey(Key.ProgramBlue))
    }

    @Test
    fun mapsMediaAndTvRemoteKeys() {
        assertEquals(RemoteKey.INFO, RemoteKeys.fromComposeKey(Key.Info))
        assertEquals(RemoteKey.GUIDE, RemoteKeys.fromComposeKey(Key.Guide))
        assertEquals(RemoteKey.INPUT, RemoteKeys.fromComposeKey(Key.TvInput))
        assertEquals(RemoteKey.PLAY_PAUSE, RemoteKeys.fromComposeKey(Key.MediaPlayPause))
        assertEquals(RemoteKey.PLAY, RemoteKeys.fromComposeKey(Key.MediaPlay))
        assertEquals(RemoteKey.PAUSE, RemoteKeys.fromComposeKey(Key.MediaPause))
        assertEquals(RemoteKey.STOP, RemoteKeys.fromComposeKey(Key.MediaStop))
        assertEquals(RemoteKey.REWIND, RemoteKeys.fromComposeKey(Key.MediaRewind))
        assertEquals(RemoteKey.FAST_FORWARD, RemoteKeys.fromComposeKey(Key.MediaFastForward))
        assertEquals(RemoteKey.NEXT, RemoteKeys.fromComposeKey(Key.MediaNext))
        assertEquals(RemoteKey.PREVIOUS, RemoteKeys.fromComposeKey(Key.MediaPrevious))
        assertEquals(RemoteKey.CHANNEL_UP, RemoteKeys.fromComposeKey(Key.ChannelUp))
        assertEquals(RemoteKey.CHANNEL_DOWN, RemoteKeys.fromComposeKey(Key.ChannelDown))
    }

    @Test
    fun mapsDigitsAndRejectsNonDigitKeys() {
        assertEquals(7, RemoteKeys.digitOf(Key.Seven))
        assertEquals(0, RemoteKeys.digitOf(Key.Zero))
        assertNull(RemoteKeys.digitOf(Key.Menu))
    }
}
