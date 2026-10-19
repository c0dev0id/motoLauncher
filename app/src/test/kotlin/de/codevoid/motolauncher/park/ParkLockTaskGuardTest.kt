package de.codevoid.motolauncher.park

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The rule behind the park lock's re-pinning guard. */
@RunWith(RobolectricTestRunner::class)
class ParkLockTaskGuardTest {

    private fun shouldRequest(
        parked: Boolean = true,
        isSettingPin: Boolean = false,
        finishing: Boolean = false,
        lockTaskActive: Boolean = false,
        sinceLastRequestMs: Long = 10_000,
    ) = ParkActivity.shouldRequestLockTask(
        parked, isSettingPin, finishing, lockTaskActive, sinceLastRequestMs,
    )

    @Test
    fun requestsWhenParkedAndUnpinned() {
        assertTrue(shouldRequest())
    }

    @Test
    fun doesNotRequestWhenAlreadyPinned() {
        assertFalse(shouldRequest(lockTaskActive = true))
    }

    @Test
    fun doesNotRequestWhenNoLongerParked() {
        // The correct PIN clears the flag before releasing the pin: an in-flight guard run
        // must not re-pin the screen behind it.
        assertFalse(shouldRequest(parked = false))
    }

    @Test
    fun doesNotRequestWhileFinishing() {
        assertFalse(shouldRequest(finishing = true))
    }

    @Test
    fun doesNotRequestWhileSettingAPin() {
        // PIN setup happens at a desk and never pins.
        assertFalse(shouldRequest(isSettingPin = true))
    }

    @Test
    fun waitsOutTheAsynchronousStateUpdateAfterARequest() {
        // lockTaskModeState still reads NONE straight after a successful request, so a
        // poll inside that window would fire a redundant second request and toast.
        assertFalse(shouldRequest(sinceLastRequestMs = 0))
        assertFalse(shouldRequest(sinceLastRequestMs = 399))
        assertTrue(shouldRequest(sinceLastRequestMs = 400))
    }
}
