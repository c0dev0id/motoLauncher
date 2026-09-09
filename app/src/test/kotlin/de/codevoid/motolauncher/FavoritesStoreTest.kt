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
    fun allSlotsReportsConfiguredSize() {
        assertEquals(FavoritesStore.SLOT_COUNT, store.allSlots().size)
    }
}
