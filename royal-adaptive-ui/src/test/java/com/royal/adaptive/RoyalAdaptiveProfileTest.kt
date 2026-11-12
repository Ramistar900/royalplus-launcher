package com.royal.adaptive

import android.content.res.Configuration
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun compactWindowOnTabletDoesNotForceWideLayout() {
        val tabletInCompactWindow = RoyalAdaptiveProfile.Default.copy(
            deviceClass = RoyalDeviceClass.TABLET,
            widthClass = RoyalWindowWidthClass.COMPACT
        )
        assertFalse(tabletInCompactWindow.prefersWideLayout)
    }

    @Test
    fun smallPhoneLauncherGridCanUseTwoColumns() {
        val phone = RoyalAdaptiveProfile.Default
        assertEquals(2, phone.launcherAppColumns(360.dp))
    }

    @Test
    fun gridColumnsRespectAvailableWidthAndCaps() {
        assertEquals(2, calculateGridColumns(390f, 16f, 160f, 1, 6))
        assertEquals(4, calculateGridColumns(500f, 16f, 110f, 1, 6))
        assertEquals(6, calculateGridColumns(1280f, 40f, 100f, 1, 6))
    }
 
    @Test
    fun launcherColumnsAdaptAcrossPhoneTabletAndTelevisionWidths() {
        val phone = RoyalAdaptiveProfile.Default
        assertEquals(2, phone.launcherAppColumns(320.dp))
        assertEquals(2, phone.launcherAppColumns(390.dp))

        val tablet = phone.copy(
            deviceClass = RoyalDeviceClass.TABLET,
            widthClass = RoyalWindowWidthClass.EXPANDED
        )
        assertEquals(4, tablet.launcherAppColumns(840.dp))

        val television = phone.copy(
            deviceClass = RoyalDeviceClass.TELEVISION,
            widthClass = RoyalWindowWidthClass.EXPANDED,
            minimumFocusTarget = 64.dp
        )
        assertEquals(6, television.launcherAppColumns(1280.dp))
    }

    @Test
    fun televisionProfilePreservesComfortableRemoteTargets() {
        val television = RoyalAdaptiveProfile.Default.copy(
            deviceClass = RoyalDeviceClass.TELEVISION,
            widthClass = RoyalWindowWidthClass.EXPANDED,
            minimumFocusTarget = 64.dp
        )
        assertEquals(64.dp, television.minimumFocusTarget)
        assertEquals(60.dp, television.controlHeight())
    }

}
