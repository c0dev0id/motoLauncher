package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.content.Context

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
        prefs.edit()
            .putString(key(index), component.flattenToString())
            .remove(labelKey(index))
            .remove(urlKey(index))
            .apply()
    }

    fun setLink(index: Int, label: String, url: String) {
        prefs.edit()
            .putString(key(index), LINK_MARKER)
            .putString(labelKey(index), label)
            .putString(urlKey(index), url)
            .apply()
    }

    fun clearSlot(index: Int) {
        prefs.edit()
            .remove(key(index))
            .remove(labelKey(index))
            .remove(urlKey(index))
            .apply()
    }

    fun allSlots(): List<SlotEntry?> = (0 until SLOT_COUNT).map { getSlotEntry(it) }

    // Called when a package is uninstalled: without this the slot keeps a component that
    // can never resolve again, drawn as an empty "+" that is not actually empty.
    // Link slots are unaffected — they have no package.
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

        // Home grid is 4x3 = 12 cells; the last cell is the fixed "All Apps" tile,
        // leaving 11 configurable favorite slots.
        const val SLOT_COUNT = 11
    }
}
