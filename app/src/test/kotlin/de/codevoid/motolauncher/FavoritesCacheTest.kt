package de.codevoid.motolauncher

import android.content.ComponentName
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.SlotEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoritesCacheTest {

    private val cache = FavoritesCache()
    private val maps = ComponentName("com.maps", "com.maps.Main")
    private val music = ComponentName("com.music", "com.music.Main")
    private val loads = mutableListOf<List<ComponentName>>()

    private fun load(components: List<ComponentName>): Map<ComponentName, AppEntry> {
        loads += components
        return emptyMap()
    }

    @Test
    fun loadsOnlyTheAppSlots() {
        cache.resolve(listOf(SlotEntry.App(maps), SlotEntry.Link("Wiki", "https://w"), null), 0, ::load)
        assertEquals(listOf(listOf(maps)), loads)
    }

    @Test
    fun reusesTheResultWhileSlotsAndGenerationStayTheSame() {
        val slots = listOf(SlotEntry.App(maps))
        cache.resolve(slots, 3, ::load)
        cache.resolve(listOf(SlotEntry.App(maps)), 3, ::load)
        assertEquals(1, loads.size)
    }

    @Test
    fun reloadsWhenThePackageGenerationMoves() {
        val slots = listOf(SlotEntry.App(maps))
        cache.resolve(slots, 3, ::load)
        cache.resolve(slots, 4, ::load)
        assertEquals(2, loads.size)
    }

    @Test
    fun reloadsWhenTheSlotsChange() {
        cache.resolve(listOf(SlotEntry.App(maps)), 3, ::load)
        cache.resolve(listOf(SlotEntry.App(music)), 3, ::load)
        assertEquals(listOf(listOf(maps), listOf(music)), loads)
    }
}
