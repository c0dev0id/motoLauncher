package de.codevoid.motolauncher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rule that decides whether the park lock is offered at all. Both halves of it are
 * about a device the tests cannot have, so the rule is kept pure and pinned here: getting
 * it wrong either hides a working lock or offers one that asks the rider to confirm every
 * re-pin.
 *
 * API levels are written as numbers rather than `Build.VERSION_CODES` so the boundary is
 * stated independently of the constant the rule uses.
 */
@RunWith(RobolectricTestRunner::class)
class ParkLockAvailabilityTest {

    private fun offered(sdkInt: Int = 35, deviceSecure: Boolean = false) =
        ParkActivity.shouldOfferParkLock(sdkInt, deviceSecure)

    @Test
    fun offeredOnAndroid15AndUp() {
        assertTrue(offered(sdkInt = 35))
        assertTrue(offered(sdkInt = 36))
    }

    @Test
    fun hiddenBelowAndroid15() {
        // The system confirms every startLockTask() there, so the re-pinning guard would
        // raise a dialog rather than close the unpin hatch.
        assertTrue(offered(sdkInt = 35))
        assertFalse(offered(sdkInt = 34))
        assertFalse(offered(sdkInt = 30))
    }

    @Test
    fun hiddenWhenTheDeviceHasASecureLockScreen() {
        // The power button already parks such a device, and better.
        assertFalse(offered(deviceSecure = true))
    }

    @Test
    fun bothReasonsHideItIndependently() {
        assertFalse(offered(sdkInt = 34, deviceSecure = true))
    }
}
