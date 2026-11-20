package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoyalSportsRankingMovementTest {
    @Test
    fun movingUpProducesPositiveMovement() {
        assertEquals(3, requireNotNull(calculateStandingMovement(currentRank = 2, previousRank = 5)))
    }

    @Test
    fun movingDownProducesNegativeMovement() {
        assertEquals(-2, requireNotNull(calculateStandingMovement(currentRank = 6, previousRank = 4)))
    }

    @Test
    fun unchangedRankProducesZeroMovement() {
        assertEquals(0, requireNotNull(calculateStandingMovement(currentRank = 4, previousRank = 4)))
    }

    @Test
    fun firstSnapshotHasNoMovement() {
        assertNull(calculateStandingMovement(currentRank = 4, previousRank = null))
    }

    @Test
    fun invalidPreviousRankHasNoMovement() {
        assertNull(calculateStandingMovement(currentRank = 4, previousRank = 0))
    }
}
