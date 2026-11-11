package com.royal.adaptive

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Test

class RoyalAdaptiveProfileTest {
    @Test
    fun televisionSignalsTakePriority() {
        assertEquals(
            RoyalDeviceClass.TELEVISION,
            resolveRoyalDeviceClass(
                Configuration.UI_MODE_TYPE_TELEVISION, false, false, true, 720, 1280f
            )
        )
    }

    @Test
    fun leanbackDetectsTelevisionEvenWithGenericUiMode() {
        assertEquals(
            RoyalDeviceClass.TELEVISION,
            resolveRoyalDeviceClass(
                Configuration.UI_MODE_TYPE_NORMAL, true, false, false, 0, 960f
            )
        )
    }

    @Test
    fun televisionHardwareFeatureDetectsTvWithGenericUiMode() {
        assertEquals(
            RoyalDeviceClass.TELEVISION,
            resolveRoyalDeviceClass(
                Configuration.UI_MODE_TYPE_NORMAL, false, true, true, 720, 1280f
            )
        )
    }

    @Test
    fun tabletUsesSmallestDeviceWidth() {
        assertEquals(
            RoyalDeviceClass.TABLET,
            resolveRoyalDeviceClass(
                Configuration.UI_MODE_TYPE_NORMAL, false, false, true, 600, 560f
            )
        )
    }

    @Test
    fun gridColumnsRespectAvailableWidthAndCaps() {
        assertEquals(2, calculateGridColumns(390f, 16f, 160f, 1, 6))
        assertEquals(4, calculateGridColumns(500f, 16f, 110f, 1, 6))
        assertEquals(6, calculateGridColumns(1280f, 40f, 100f, 1, 6))
    }
}
