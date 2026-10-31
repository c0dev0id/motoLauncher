package de.codevoid.motolauncher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.FavoritesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The Home grid as a real activity builds it. An empty "+" slot opens the picker, which is
 * configuration, so the remote must not be able to focus it.
 */
@RunWith(RobolectricTestRunner::class)
class HomeGridTest {

    private val favorites = FavoritesStore(ApplicationProvider.getApplicationContext<Context>())

    private fun HomeActivity.tiles(): List<View> {
        val grid = findViewById<ViewGroup>(R.id.homeGrid)
        return (0 until grid.childCount).flatMap { r ->
            val row = grid.getChildAt(r) as ViewGroup
            (0 until row.childCount).map { row.getChildAt(it) }
        }
    }

    @Test
    fun emptySlotsAreTouchOnlyAndAllAppsIsNot() {
        val tiles = Robolectric.buildActivity(HomeActivity::class.java).setup().get().tiles()
        assertEquals(12, tiles.size)
        assertTrue(tiles.dropLast(1).none { it.isFocusable })
        assertTrue(tiles.last().isFocusable)
    }

    // The tile views are reused across resumes, so each bind has to set focusability.
    @Test
    fun aSlotFollowsItsContentAcrossRebinds() {
        favorites.setLink(0, "Weather", "https://example.com")
        val home = Robolectric.buildActivity(HomeActivity::class.java).setup()
        assertTrue(home.get().tiles()[0].isFocusable)

        favorites.clearSlot(0)
        home.pause().resume()
        assertFalse(home.get().tiles()[0].isFocusable)

        favorites.setLink(0, "Weather", "https://example.com")
        home.pause().resume()
        assertTrue(home.get().tiles()[0].isFocusable)
    }
}
