package de.codevoid.motolauncher

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.FavoritesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
