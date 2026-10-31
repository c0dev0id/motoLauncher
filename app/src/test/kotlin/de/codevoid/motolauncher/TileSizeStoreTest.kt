package de.codevoid.motolauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.SETTINGS_PREFS
import de.codevoid.motolauncher.data.TileSize
import de.codevoid.motolauncher.data.TileSizeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileSizeStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun defaultsToMedium() {
        assertEquals(TileSize.MEDIUM, TileSizeStore(context).tileSize)
    }

    @Test
    fun choiceIsReadBackByAFreshInstance() {
        TileSizeStore(context).tileSize = TileSize.SMALL
        assertEquals(TileSize.SMALL, TileSizeStore(context).tileSize)
    }

    @Test
    fun unknownStoredValueFallsBackToMedium() {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit().putString("tile_size", "HUGE").commit()
        assertEquals(TileSize.MEDIUM, TileSizeStore(context).tileSize)
    }

    @Test
    fun homeTileCountsAreEightTwelveFifteen() {
        assertEquals(listOf(8, 12, 15), TileSize.entries.map { it.homeTileCount })
    }

    @Test
    fun homeKeepsItsTileCountWhenRotated() {
        TileSize.entries.forEach { size ->
            val landscape = size.homeColumns(isPortrait = false) * size.homeRows(isPortrait = false)
            val portrait = size.homeColumns(isPortrait = true) * size.homeRows(isPortrait = true)
            assertEquals(size.name, size.homeTileCount, landscape)
            assertEquals(size.name, size.homeTileCount, portrait)
        }
    }

    @Test
    fun portraitHomeIsTheLandscapeGridTransposed() {
        TileSize.entries.forEach { size ->
            assertEquals(size.name, size.homeRows(isPortrait = false), size.homeColumns(isPortrait = true))
            assertEquals(size.name, size.homeColumns(isPortrait = false), size.homeRows(isPortrait = true))
        }
    }

    @Test
    fun mediumIsTheOriginalLayout() {
        val medium = TileSize.MEDIUM
        assertEquals(4, medium.homeColumns(isPortrait = false))
        assertEquals(3, medium.homeRows(isPortrait = false))
        assertEquals(5, medium.listColumns(isPortrait = false))
        assertEquals(4, medium.listColumns(isPortrait = true))
    }

    @Test
    fun appListGainsAColumnPerStep() {
        assertEquals(listOf(4, 5, 6), TileSize.entries.map { it.listColumns(isPortrait = false) })
        assertEquals(listOf(3, 4, 5), TileSize.entries.map { it.listColumns(isPortrait = true) })
    }

    @Test
    fun favouriteSlotsFillEveryHomeGrid() {
        TileSize.entries.forEach { size ->
            assertTrue(size.name, size.homeTileCount - 1 <= FavoritesStore.SLOT_COUNT)
        }
        assertEquals(14, FavoritesStore.SLOT_COUNT)
    }
}
