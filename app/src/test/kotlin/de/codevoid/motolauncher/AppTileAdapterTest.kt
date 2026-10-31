package de.codevoid.motolauncher

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

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
    fun showsEachTilesSubtitle() {
        assertEquals(listOf("Dark", "1.0.0"), shownSubtitles())
    }

    // A recycled holder must not keep the previous tile's subtitle line.
    @Test
    fun aTileWithoutASubtitleHidesTheLine() {
        val holder = adapter.createViewHolder(FrameLayout(ApplicationProvider.getApplicationContext()), 0)
        adapter.bindViewHolder(holder, 0)

        adapter.submit(listOf(TileItem("Maps")))
        adapter.bindViewHolder(holder, 0)

        assertEquals(View.GONE, holder.binding.appSubtitle.visibility)
    }

    // A hidden app reads as dimmed, but its label keeps 4.5:1 and its focus ring stays opaque.
    @Test
    fun aDimmedTileFadesItsIconAndMutesItsLabelButNotTheTile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val holder = adapter.createViewHolder(FrameLayout(context), 0)

        adapter.submit(listOf(TileItem("Maps", icon = ColorDrawable(), dimmed = true)))
        adapter.bindViewHolder(holder, 0)
        assertEquals(1f, holder.binding.root.alpha)
        assertTrue(holder.binding.appIcon.alpha < 1f)
        assertEquals(ContextCompat.getColor(context, R.color.on_surface_muted), holder.binding.appLabel.currentTextColor)

        adapter.submit(listOf(TileItem("Maps", icon = ColorDrawable())))
        adapter.bindViewHolder(holder, 0)
        assertEquals(1f, holder.binding.appIcon.alpha)
        assertEquals(ContextCompat.getColor(context, R.color.on_surface), holder.binding.appLabel.currentTextColor)
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

    // The app list re-renders on every resume; the remote must stay on the tile it was on.
    @Test
    fun focusStaysOnTheSameTileWhenTheListIsSubmittedAgain() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val tiles = AppTileAdapter(emptyList())
        val grid = RecyclerView(activity).apply {
            layoutManager = GridLayoutManager(activity, 3)
            adapter = tiles
        }
        activity.setContentView(grid)
        fun render() {
            tiles.submit((1..6).map { TileItem("App $it") })
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }

        render()
        grid.findViewHolderForAdapterPosition(4)!!.itemView.requestFocus()
        render()

        val focused = grid.focusedChild
        assertNotNull(focused)
        assertEquals(4, grid.getChildAdapterPosition(focused))
    }
}
