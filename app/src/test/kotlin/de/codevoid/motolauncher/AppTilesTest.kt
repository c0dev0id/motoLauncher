package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.os.Process
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.ComponentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppTilesTest {

    private fun app(name: String) =
        AppEntry(ComponentKey(ComponentName("pkg.$name", "pkg.$name.Main"), Process.myUserHandle()), name, ColorDrawable())

    private val maps = app("Maps")
    private val radio = app("Radio")

    @Test
    fun hiddenAppsAreLeftOut() {
        val tiles = appTiles(listOf(maps, radio), setOf("pkg.Radio"), showHidden = false, onClick = {}, onLongClick = null)
        assertEquals(listOf("Maps"), tiles.map { it.label })
    }

    @Test
    fun shownHiddenAppsAreDimmed() {
        val tiles = appTiles(listOf(maps, radio), setOf("pkg.Radio"), showHidden = true, onClick = {}, onLongClick = null)
        assertEquals(listOf(false, true), tiles.map { it.dimmed })
    }

    @Test
    fun aTileIsKeyedByItsApp() {
        val tiles = appTiles(listOf(maps, radio), emptySet(), showHidden = false, onClick = {}, onLongClick = null)
        assertEquals(listOf(maps.key, radio.key), tiles.map { it.key })
    }

    @Test
    fun tapAndLongPressReachTheirApp() {
        var tapped: AppEntry? = null
        var longPressed: Pair<AppEntry, Boolean>? = null
        val tiles = appTiles(
            listOf(maps, radio),
            setOf("pkg.Radio"),
            showHidden = true,
            onClick = { tapped = it },
            onLongClick = { app, hidden -> longPressed = app to hidden },
        )

        tiles[0].onClick()
        assertTrue(tiles[1].onLongClick!!())

        assertEquals(maps, tapped)
        assertEquals(radio to true, longPressed)
    }

    @Test
    fun withoutALongPressActionNoTileHasOne() {
        val tiles = appTiles(listOf(maps, radio), emptySet(), showHidden = false, onClick = {}, onLongClick = null)
        assertTrue(tiles.all { it.onLongClick == null })
    }
}
