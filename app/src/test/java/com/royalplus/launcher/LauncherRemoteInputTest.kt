package com.royalplus.launcher

import android.view.KeyEvent
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
        assertEquals(RemoteKey.MENU, RemoteKeys.fromKeyCode(KeyEvent.KEYCODE_MENU))
        assertEquals(RemoteKey.SEARCH, RemoteKeys.fromKeyCode(KeyEvent.KEYCODE_SEARCH))
        assertEquals(RemoteKey.VOICE, RemoteKeys.fromKeyCode(KeyEvent.KEYCODE_VOICE_ASSIST))
        assertEquals(RemoteKey.RED, RemoteKeys.fromKeyCode(KeyEvent.KEYCODE_PROG_RED))
        assertEquals(RemoteKey.YELLOW, RemoteKeys.fromKeyCode(KeyEvent.KEYCODE_PROG_YELLOW))
    }

    @Test
    fun mapsDigitsAndRejectsNonDigitKeys() {
        assertEquals(7, RemoteKeys.digitOf(KeyEvent.KEYCODE_7))
        assertEquals(0, RemoteKeys.digitOf(KeyEvent.KEYCODE_0))
        assertNull(RemoteKeys.digitOf(KeyEvent.KEYCODE_MENU))
    }
}
