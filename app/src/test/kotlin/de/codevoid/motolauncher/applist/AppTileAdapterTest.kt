package de.codevoid.motolauncher.applist

import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
}
