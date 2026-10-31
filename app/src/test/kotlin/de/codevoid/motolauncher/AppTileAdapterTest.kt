package de.codevoid.motolauncher

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppTileAdapterTest {

    private val adapter = AppTileAdapter(listOf(
        TileItem("Theme", subtitle = "Dark"),
        TileItem("Check for updates", subtitle = "1.0.0"),
    ))

    private fun shownSubtitles(): List<String> {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        return (0 until adapter.itemCount).map { position ->
            val holder = adapter.createViewHolder(parent, 0)
            adapter.bindViewHolder(holder, position)
            holder.binding.appSubtitle.text.toString()
        }
    }

    @Test
    fun updatesOnlyTheMatchingItem() {
        adapter.updateItem({ it.label == "Check for updates" }) { it.copy(subtitle = "45%") }
        assertEquals(listOf("Dark", "45%"), shownSubtitles())
    }

    @Test
    fun leavesTheListAloneWhenNothingMatches() {
        adapter.updateItem({ it.label == "Navigation bar" }) { it.copy(subtitle = "x") }
        assertEquals(listOf("Dark", "1.0.0"), shownSubtitles())
    }

    // Holders are shared between touch-only and browse tiles, so every bind sets it.
    @Test
    fun touchOnlyTilesCannotTakeFocusAndARecycledHolderCanAgain() {
        val holder = adapter.createViewHolder(FrameLayout(ApplicationProvider.getApplicationContext()), 0)

        adapter.submit(listOf(TileItem("Theme")), touchOnly = true)
        adapter.bindViewHolder(holder, 0)
        assertFalse(holder.binding.root.isFocusable)

        adapter.submit(listOf(TileItem("Maps")))
        adapter.bindViewHolder(holder, 0)
        assertTrue(holder.binding.root.isFocusable)
    }
}
