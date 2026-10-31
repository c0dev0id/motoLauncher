package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.NavAppStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NavAppStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val maps = ComponentName("com.example.maps", "com.example.maps.Main")

    @Test
    fun aNavAppInAnotherProfileIsReadBackInThatProfile() {
        shadowOf(context.getSystemService(UserManager::class.java))
            .addProfile(Process.myUid() / 100_000, 10, "Work", 0)
        val work = ComponentKey(maps, UserHandle.getUserHandleForUid(10 * 100_000))

        NavAppStore(context).navApp = work
        assertEquals(work, NavAppStore(context).navApp)

        val own = ComponentKey(maps, Process.myUserHandle())
        NavAppStore(context).navApp = own
        assertEquals(own, NavAppStore(context).navApp)
    }

    @Test
    fun clearingForgetsTheNavApp() {
        NavAppStore(context).navApp = ComponentKey(maps, Process.myUserHandle())
        NavAppStore(context).navApp = null
        assertNull(NavAppStore(context).navApp)
    }
}
