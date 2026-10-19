package de.codevoid.motolauncher.park

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** When the park lock is offered. API levels are numbers, independent of the rule's constant. */
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
