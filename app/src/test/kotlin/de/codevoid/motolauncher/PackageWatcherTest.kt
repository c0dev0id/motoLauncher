package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PackageWatcherTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val own = Process.myUserHandle()
    private val work = UserHandle.getUserHandleForUid(10 * 100_000)
    private val favorites = FavoritesStore(context)
    private val generation = MutableStateFlow(0)
    private val watcher = PackageWatcher(favorites, generation)

    private fun app(user: UserHandle) = ComponentKey(ComponentName(PKG, "$PKG.Main"), user)

    @Before
    fun addWorkProfile() {
        shadowOf(context.getSystemService(UserManager::class.java))
            .addProfile(own.identifier, 10, "Work", 0)
    }

    @Test
    fun aRemovalClearsTheAppOnlyInItsProfile() {
        favorites.setSlot(0, app(own))
        favorites.setSlot(1, app(work))

        watcher.onPackageRemoved(PKG, work)

        assertEquals(app(own), favorites.getSlot(0))
        assertNull(favorites.getSlot(1))
        assertEquals(1, generation.value)
    }

    // An app on unmounted storage is unavailable, not uninstalled, and keeps its slot.
    @Test
    fun everyOtherEventAdvancesTheGenerationAndKeepsTheFavourite() {
        favorites.setSlot(0, app(own))

        watcher.onPackageAdded(PKG, own)
        watcher.onPackageChanged(PKG, own)
        watcher.onPackagesAvailable(arrayOf(PKG), own, false)
        watcher.onPackagesUnavailable(arrayOf(PKG), own, false)

        assertEquals(4, generation.value)
        assertEquals(app(own), favorites.getSlot(0))
    }

    private companion object {
        const val PKG = "com.example.maps"
    }
}
