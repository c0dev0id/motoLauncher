package de.codevoid.motolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pause after repeated wrong PINs. It has to slow guessing down without ever locking
 * out a rider who mistypes with gloves, or one who has to keep trying.
 */
class ParkRetryDelayTest {

    private fun seconds(failedAttempts: Int) = ParkLockRules.retryDelayMs(failedAttempts) / 1000

    @Test
    fun theFirstFourWrongPinsCostNothing() {
        (0..4).forEach { assertEquals(0, seconds(it)) }
    }

    @Test
    fun thePauseDoublesFromThirtySeconds() {
        assertEquals(listOf(30L, 60L, 120L, 240L, 480L), (5..9).map(::seconds))
    }

    @Test
    fun thePauseStopsGrowingAtTenMinutes() {
        assertEquals(600, seconds(10))
        assertEquals(600, seconds(10_000))
        assertEquals(600, seconds(Int.MAX_VALUE))
    }
}
