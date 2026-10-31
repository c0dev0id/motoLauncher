package de.codevoid.motolauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ParkStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

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
        val values = parkPrefs().all.values.map { it.toString() }
        assertFalse(values.any { it == "1234" })
    }

    @Test
    fun theSaltMakesIdenticalPinsHashDifferently() {
        store.setPin("1234")
        val first = parkPrefs().getString("park_pin_hash", null)
        store.setPin("1234")
        assertNotEquals(first, parkPrefs().getString("park_pin_hash", null))
    }

    // A restore runs on every install. A backup taken while parked would otherwise bring
    // the device back up parked behind a PIN the rider may have forgotten.
    @Test
    fun theParkStateIsLeftOutOfEveryBackup() {
        store.isParked = true
        store.setPin("1234")
        assertEquals(setOf("parked", "park_pin_salt", "park_pin_hash"), parkPrefs().all.keys)

        val file = "sharedpref/${ParkStore.PREFS}.xml"
        assertEquals(
            listOf("cloud-backup $file", "device-transfer $file"),
            excludes(R.xml.data_extraction_rules),
        )
        assertEquals(listOf("full-backup-content $file"), excludes(R.xml.backup_rules))
    }

    private fun parkPrefs() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(ParkStore.PREFS, Context.MODE_PRIVATE)

    // Each <exclude> as "<enclosing element> <domain>/<path>".
    private fun excludes(xml: Int): List<String> {
        val parser = ApplicationProvider.getApplicationContext<Context>().resources.getXml(xml)
        val open = mutableListOf<String>()
        val found = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "exclude") {
                        val domain = parser.getAttributeValue(null, "domain")
                        val path = parser.getAttributeValue(null, "path")
                        found += "${open.last()} $domain/$path"
                    }
                    open += parser.name
                }
                XmlPullParser.END_TAG -> open.removeAt(open.lastIndex)
            }
        }
        return found
    }
}
