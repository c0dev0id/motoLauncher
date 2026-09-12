package de.codevoid.motolauncher

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.SlotEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesStoreTest {

    private lateinit var store: FavoritesStore

    @Before
    fun setUp() {
        store = FavoritesStore(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun setThenGetRoundTrips() {
        val component = ComponentName("com.example", "com.example.MainActivity")
        store.setSlot(3, component)
        assertEquals(component, store.getSlot(3))
    }

    @Test
    fun clearRemovesTheSlot() {
        store.setSlot(1, ComponentName("a", "a.M"))
        store.clearSlot(1)
        assertNull(store.getSlot(1))
    }

    @Test
    fun clearSlotsForPackageClearsEverySlotHoldingIt() {
        val pkg = "com.example.gone"
        store.setSlot(0, ComponentName(pkg, "$pkg.Main"))
        store.setSlot(4, ComponentName(pkg, "$pkg.Other"))
        val keeper = ComponentName("com.example.stays", "com.example.stays.Main")
        store.setSlot(2, keeper)

        store.clearSlotsForPackage(pkg)

        assertNull(store.getSlot(0))
        assertNull(store.getSlot(4))
        // A different package sharing no name must survive.
        assertEquals(keeper, store.getSlot(2))
    }

    @Test
    fun clearSlotsForPackageIgnoresAPrefixMatch() {
        val keeper = ComponentName("com.example.gonefishing", "com.example.gonefishing.Main")
        store.setSlot(1, keeper)

        store.clearSlotsForPackage("com.example.gone")

        assertEquals(keeper, store.getSlot(1))
    }

    @Test
    fun allSlotsReportsConfiguredSize() {
        assertEquals(FavoritesStore.SLOT_COUNT, store.allSlots().size)
    }

    @Test
    fun linkRoundTrip() {
        store.setLink(5, "OpenStreetMap", "https://osm.org")
        val entry = store.getSlotEntry(5)
        assertTrue(entry is SlotEntry.Link)
        entry as SlotEntry.Link
        assertEquals("OpenStreetMap", entry.label)
        assertEquals("https://osm.org", entry.url)
    }

    @Test
    fun clearSlotAlsoRemovesLinkData() {
        store.setLink(3, "Test", "https://example.com")
        store.clearSlot(3)
        assertNull(store.getSlotEntry(3))
    }

    @Test
    fun setSlotAfterLinkClearsLinkData() {
        store.setLink(2, "Test", "https://example.com")
        val component = ComponentName("com.example", "com.example.Main")
        store.setSlot(2, component)
        assertTrue(store.getSlotEntry(2) is SlotEntry.App)
    }

    @Test
    fun clearSlotsForPackageLeavesLinkSlotsAlone() {
        store.setLink(0, "Link", "https://example.com")
        store.clearSlotsForPackage("example.com")
        assertTrue(store.getSlotEntry(0) is SlotEntry.Link)
    }
}
