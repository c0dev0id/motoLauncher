package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.CellularStore
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.NavAppStore
import de.codevoid.motolauncher.ui.AppTileAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** The app list as a real activity runs it, in browse mode, settings mode and both pickers. */
@RunWith(RobolectricTestRunner::class)
class AppListActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // Every tile the adapter holds, bound to a fresh holder, whether or not it is laid out.
    private fun AppListActivity.tiles(): List<View> {
        val grid = findViewById<RecyclerView>(R.id.appGrid)
        val adapter = grid.adapter as AppTileAdapter
        return (0 until adapter.itemCount).map { position ->
            adapter.createViewHolder(grid, 0).also { adapter.bindViewHolder(it, position) }.itemView
        }
    }

    private fun View.label() = findViewById<TextView>(R.id.appLabel).text.toString()
    private fun View.subtitle() = findViewById<TextView>(R.id.appSubtitle).text.toString()
    private fun AppListActivity.tile(label: String) = tiles().first { it.label() == label }

    private fun AppListActivity.shortEscape() {
        val down = SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0))
        dispatchKeyEvent(KeyEvent(down, down + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0))
    }

    @Test
    fun settingsModeIsTouchOnlyAndAShortEscapeLeavesItBeforeTheScreen() {
        val list = Robolectric.buildActivity(AppListActivity::class.java).setup().get()

        list.findViewById<View>(R.id.settingsButton).performClick()
        assertTrue(list.tiles().any { it.label() == list.getString(R.string.theme_label) })
        assertTrue(list.tiles().none { it.isFocusable })

        list.shortEscape()
        assertTrue(list.tiles().none { it.label() == list.getString(R.string.theme_label) })
        assertFalse(list.isFinishing)

        list.shortEscape()
        assertTrue(list.isFinishing)
    }

    @Test
    fun changingASettingShowsAtOnce() {
        val list = Robolectric.buildActivity(AppListActivity::class.java).setup().get()
        list.findViewById<View>(R.id.settingsButton).performClick()
        val cellular = list.getString(R.string.cellular_indicator)

        list.tile(cellular).performClick()

        assertTrue(CellularStore(context).enabled)
        assertEquals(list.getString(R.string.setting_on), list.tile(cellular).subtitle())
    }

    @Test
    fun theNavAppIsNamedByItsPackageWhileTheAppIsNotListed() {
        NavAppStore(context).navApp =
            ComponentKey(ComponentName("com.example.maps", "com.example.maps.Main"), Process.myUserHandle())
        val list = Robolectric.buildActivity(AppListActivity::class.java).setup().get()
        list.findViewById<View>(R.id.settingsButton).performClick()

        assertEquals("com.example.maps", list.tile(list.getString(R.string.nav_app)).subtitle())
    }

    @Test
    fun theSlotPickerLeadsWithNoneAndAddLinkAndHasNoSettings() {
        val favorites = FavoritesStore(context)
        favorites.setLink(2, "Weather", "https://example.com")
        val list = Robolectric.buildActivity(AppListActivity::class.java, AppListActivity.pickIntent(context, 2))
            .setup().get()

        assertEquals(View.GONE, list.findViewById<View>(R.id.settingsButton).visibility)
        assertEquals(
            listOf(list.getString(R.string.pick_none), list.getString(R.string.add_link)),
            list.tiles().map { it.label() },
        )
        assertTrue(list.tiles().none { it.isFocusable })

        list.tile(list.getString(R.string.pick_none)).performClick()
        assertNull(favorites.getSlotEntry(2))
        assertTrue(list.isFinishing)
    }

    @Test
    fun theNavAppPickerLeadsWithNoneOnly() {
        val list = Robolectric.buildActivity(AppListActivity::class.java, AppListActivity.navAppPickerIntent(context))
            .setup().get()
        assertEquals(listOf(list.getString(R.string.pick_none)), list.tiles().map { it.label() })
    }
}
