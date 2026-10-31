package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.SlotEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class FavoritesStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val own = Process.myUserHandle()
    private val work = UserHandle.getUserHandleForUid(WORK_USER_ID * 100_000)
    private lateinit var store: FavoritesStore

    private fun app(pkg: String, user: UserHandle = own, cls: String = "Main") =
        ComponentKey(ComponentName(pkg, "$pkg.$cls"), user)

    @Before
    fun setUp() {
        shadowOf(context.getSystemService(UserManager::class.java))
            .addProfile(Process.myUid() / 100_000, WORK_USER_ID, "Work", 0)
        store = FavoritesStore(context)
    }

    @Test
    fun setThenGetRoundTrips() {
        store.setSlot(3, app("com.example"))
        assertEquals(app("com.example"), store.getSlot(3))
    }

    @Test
    fun aWorkProfileAppKeepsItsProfile() {
        store.setSlot(3, app("com.example", work))
        assertEquals(app("com.example", work), store.getSlot(3))
    }

    @Test
    fun aSlotWhoseProfileIsGoneReadsEmpty() {
        context.getSharedPreferences("favorites", Context.MODE_PRIVATE).edit()
            .putString("slot_0", "com.example/com.example.Main")
            .putLong("slot_0_user", 99)
            .commit()
        assertNull(store.getSlotEntry(0))
    }

    @Test
    fun replacingAWorkAppLeavesNoProfileBehind() {
        store.setSlot(2, app("com.example", work))
        store.setSlot(2, app("com.other"))
        assertEquals(own, store.getSlot(2)?.user)

        store.setSlot(2, app("com.example", work))
        store.setLink(2, "Weather", "https://example.com")
        val prefs = context.getSharedPreferences("favorites", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("slot_2_user"))
    }

    @Test
    fun clearRemovesTheSlot() {
        store.setSlot(1, app("a", work))
        store.clearSlot(1)
        assertNull(store.getSlot(1))
    }

    @Test
    fun clearSlotsForPackageClearsEverySlotHoldingItInThatProfile() {
        val pkg = "com.example.gone"
        store.setSlot(0, app(pkg))
        store.setSlot(4, app(pkg, cls = "Other"))
        store.setSlot(5, app(pkg, work))
        store.setSlot(2, app("com.example.stays"))

        store.clearSlotsForPackage(pkg, own)

        assertNull(store.getSlot(0))
        assertNull(store.getSlot(4))
        assertEquals(app(pkg, work), store.getSlot(5))
        assertEquals(app("com.example.stays"), store.getSlot(2))
    }

    @Test
    fun clearSlotsForPackageIgnoresAPrefixMatch() {
        store.setSlot(1, app("com.example.gonefishing"))
        store.clearSlotsForPackage("com.example.gone", own)
        assertEquals(app("com.example.gonefishing"), store.getSlot(1))
    }

    @Test
    fun allSlotsReportsConfiguredSize() {
        assertEquals(FavoritesStore.SLOT_COUNT, store.allSlots().size)
    }

    @Test
    fun linkRoundTrip() {
        store.setLink(5, "OpenStreetMap", "https://osm.org")
        assertEquals(SlotEntry.Link("OpenStreetMap", "https://osm.org"), store.getSlotEntry(5))
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
        store.setSlot(2, app("com.example"))
        assertTrue(store.getSlotEntry(2) is SlotEntry.App)
    }

    @Test
    fun clearSlotsForPackageLeavesLinkSlotsAlone() {
        store.setLink(0, "Link", "https://example.com")
        store.clearSlotsForPackage("example.com", own)
        assertTrue(store.getSlotEntry(0) is SlotEntry.Link)
    }

    private companion object {
        const val WORK_USER_ID = 10
    }
}
