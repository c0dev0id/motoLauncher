package de.codevoid.motolauncher.home

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit

sealed class SlotEntry {
    data class App(val component: ComponentName) : SlotEntry()
    data class Link(val label: String, val url: String) : SlotEntry()
}

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSlotEntry(index: Int): SlotEntry? {
        val raw = prefs.getString(key(index), null) ?: return null
        return if (raw == LINK_MARKER) {
            val label = prefs.getString(labelKey(index), null) ?: return null
            val url = prefs.getString(urlKey(index), null) ?: return null
            SlotEntry.Link(label, url)
        } else {
            ComponentName.unflattenFromString(raw)?.let { SlotEntry.App(it) }
        }
    }

    fun getSlot(index: Int): ComponentName? =
        (getSlotEntry(index) as? SlotEntry.App)?.component

    fun setSlot(index: Int, component: ComponentName) {
        prefs.edit {
            putString(key(index), component.flattenToString())
            remove(labelKey(index))
            remove(urlKey(index))
        }
    }

    fun setLink(index: Int, label: String, url: String) {
        prefs.edit {
            putString(key(index), LINK_MARKER)
            putString(labelKey(index), label)
            putString(urlKey(index), url)
        }
    }

    fun clearSlot(index: Int) {
        prefs.edit {
            remove(key(index))
            remove(labelKey(index))
            remove(urlKey(index))
        }
    }

    fun allSlots(): List<SlotEntry?> = (0 until SLOT_COUNT).map { getSlotEntry(it) }

    /** Empties every app slot holding [packageName]; link slots are left alone. */
    fun clearSlotsForPackage(packageName: String) {
        for (index in 0 until SLOT_COUNT) {
            if (getSlot(index)?.packageName == packageName) clearSlot(index)
        }
    }

    private fun key(index: Int) = "slot_$index"
    private fun labelKey(index: Int) = "slot_${index}_label"
    private fun urlKey(index: Int) = "slot_${index}_url"

    companion object {
        private const val PREFS = "favorites"
        private const val LINK_MARKER = "link:"

        // The twelfth grid cell is the All Apps tile.
        const val SLOT_COUNT = 11
    }
}
