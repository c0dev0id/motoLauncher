package de.codevoid.motolauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The guard's decision to re-pin. Lock task mode itself needs a device, but the rule that
 * drives it does not — and getting it wrong is how the park screen would either stop
 * re-pinning or re-pin the screen a correct PIN just released.
 */
@RunWith(RobolectricTestRunner::class)
class ParkLockTaskGuardTest {

    private fun shouldRequest(
        parked: Boolean = true,
        setMode: Boolean = false,
        finishing: Boolean = false,
        lockTaskActive: Boolean = false,
        sinceLastRequestMs: Long = 10_000,
    ) = ParkActivity.shouldRequestLockTask(
        parked, setMode, finishing, lockTaskActive, sinceLastRequestMs,
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
        assertFalse(shouldRequest(setMode = true))
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
