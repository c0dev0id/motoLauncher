package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Looper
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.FavoritesStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The process-wide package watch and app-list cache, driven through LauncherApps. */
@RunWith(RobolectricTestRunner::class)
class MotoLauncherAppTest {

    private val app = ApplicationProvider.getApplicationContext<MotoLauncherApp>()
    private val launcherApps = shadowOf(app.getSystemService(LauncherApps::class.java))

    @Test
    fun theAppListIsReusedUntilAPackageChanges() {
        val first = app.getApps()
        assertSame(first, app.getApps())

        launcherApps.notifyPackageAdded("com.example.new")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, app.packageGeneration.value)
        val second = app.getApps()
        assertNotSame(first, second)
        runBlocking { first.await(); second.await() }
    }

    @Test
    fun anUninstallClearsItsFavourite() {
        val favorites = FavoritesStore(app)
        favorites.setSlot(0, ComponentKey(ComponentName("com.example.gone", "com.example.gone.Main"), Process.myUserHandle()))

        launcherApps.notifyPackageRemoved("com.example.gone")
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(favorites.getSlot(0))
    }
}
