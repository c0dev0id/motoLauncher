package de.codevoid.motolauncher.home

import android.content.ComponentName
import androidx.lifecycle.ViewModel
import de.codevoid.motolauncher.apps.AppEntry

/**
 * The favourites' resolved apps. Kept across activity recreation; reloaded only when the
 * slots or the installed apps change.
 */
class FavoritesCache : ViewModel() {
    private var apps: Map<ComponentName, AppEntry> = emptyMap()
    private var generation = -1
    private var slots: List<SlotEntry?> = emptyList()

    fun resolve(
        slots: List<SlotEntry?>,
        generation: Int,
        load: (List<ComponentName>) -> Map<ComponentName, AppEntry>,
    ): Map<ComponentName, AppEntry> {
        if (generation != this.generation || slots != this.slots) {
            apps = load(slots.filterIsInstance<SlotEntry.App>().map { it.component })
            this.generation = generation
            this.slots = slots
        }
        return apps
    }
}
