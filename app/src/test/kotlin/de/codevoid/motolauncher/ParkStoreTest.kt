package de.codevoid.motolauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ParkStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ParkStoreTest {

    private lateinit var store: ParkStore

    @Before
    fun setUp() {
        store = ParkStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun hasNoPinBeforeOneIsSet() {
        assertFalse(store.hasPin)
        assertFalse(store.verify("0000"))
    }

    @Test
    fun verifiesTheStoredPin() {
        store.setPin("1234")
        assertTrue(store.hasPin)
        assertTrue(store.verify("1234"))
        assertFalse(store.verify("4321"))
    }

    @Test
    fun settingANewPinReplacesTheOld() {
        store.setPin("1234")
        store.setPin("5678")
        assertFalse(store.verify("1234"))
        assertTrue(store.verify("5678"))
    }

    @Test
    fun parkedFlagRoundTrips() {
        assertFalse(store.isParked)
        store.isParked = true
        // A second instance reads the same prefs — this is the path a reboot takes.
        val reopened = ParkStore(ApplicationProvider.getApplicationContext())
        assertTrue(reopened.isParked)
        reopened.isParked = false
        assertFalse(ParkStore(ApplicationProvider.getApplicationContext()).isParked)
    }

    @Test
    fun thePinIsNotStoredInTheClear() {
        store.setPin("1234")
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        val values = prefs.all.values.map { it.toString() }
        assertFalse(values.any { it == "1234" })
    }

    @Test
    fun theSaltMakesIdenticalPinsHashDifferently() {
        store.setPin("1234")
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
        val first = prefs.getString("park_pin_hash", null)
        store.setPin("1234")
        assertNotEquals(first, prefs.getString("park_pin_hash", null))
    }
}
