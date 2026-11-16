package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyalInterfaceQualityTest {
    @Test
    fun automaticQualityFollowsScreenAndMemoryLimits() {
        val budget = resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.AUTO, 1920, 1080, 512)
        assertEquals(RoyalInterfaceQualityPreset.P1080, budget.effectivePreset)
        assertEquals(1920, budget.maxEdgePx)
    }

    @Test
    fun explicitHighQualityIsCappedToDisplayAndMemory() {
        val budget = resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.P4K, 1920, 1080, 192)
        assertEquals(RoyalInterfaceQualityPreset.P720, budget.effectivePreset)
    }

    @Test
    fun selectedLowQualityIsNotRaised() {
        val budget = resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.P480, 3840, 2160, 512)
        assertEquals(RoyalInterfaceQualityPreset.P480, budget.effectivePreset)
        assertEquals(854, budget.maxEdgePx)
    }

    @Test
    fun weatherVideosRequireAtLeast720p() {
        assertFalse(resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.P480, 3840, 2160, 512).weatherVideoEnabled)
        assertTrue(resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.P720, 3840, 2160, 512).weatherVideoEnabled)
    }

    @Test
    fun unknownMemoryClassUsesSafeFallback() {
        val budget = resolveRoyalInterfaceQualityBudget(RoyalInterfaceQualityPreset.P4K, 3840, 2160, 0)
        assertEquals(RoyalInterfaceQualityPreset.P720, budget.effectivePreset)
    }
}
