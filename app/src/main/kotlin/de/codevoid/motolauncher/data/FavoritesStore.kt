package de.codevoid.motolauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserHandle
import android.os.UserManager

sealed class SlotEntry {
    data class App(val key: ComponentKey) : SlotEntry()
    data class Link(val label: String, val url: String) : SlotEntry()
}

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val users = context.getSystemService(Context.USER_SERVICE) as UserManager

    fun getSlotEntry(index: Int): SlotEntry? {
        val raw = prefs.getString(key(index), null) ?: return null
        return if (raw == LINK_MARKER) {
            val label = prefs.getString(labelKey(index), null) ?: return null
            val url = prefs.getString(urlKey(index), null) ?: return null
            SlotEntry.Link(label, url)
        } else {
            prefs.getComponentKey(key(index), users)?.let { SlotEntry.App(it) }
        }
    }

    fun getSlot(index: Int): ComponentKey? =
        (getSlotEntry(index) as? SlotEntry.App)?.key

    fun setSlot(index: Int, app: ComponentKey) = writeSlot(index) {
        putComponentKey(key(index), app, users)
    }

    fun setLink(index: Int, label: String, url: String) = writeSlot(index) {
        putString(key(index), LINK_MARKER)
        putString(labelKey(index), label)
        putString(urlKey(index), url)
    }

    fun clearSlot(index: Int) = writeSlot(index) {}

    fun allSlots(): List<SlotEntry?> = (0 until SLOT_COUNT).map { getSlotEntry(it) }

    /** Clears every slot holding an app of [packageName] in [user]; link slots have no package. */
    fun clearSlotsForPackage(packageName: String, user: UserHandle) {
        for (index in 0 until SLOT_COUNT) {
            val app = getSlot(index) ?: continue
            if (app.component.packageName == packageName && app.user == user) clearSlot(index)
        }
    }

    // Removes every key the slot may hold before [write] adds the new ones.
    private inline fun writeSlot(index: Int, write: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
            .removeComponentKey(key(index))
            .remove(labelKey(index))
            .remove(urlKey(index))
        editor.write()
        editor.apply()
    }

    private fun key(index: Int) = "slot_$index"
    private fun labelKey(index: Int) = "slot_${index}_label"
    private fun urlKey(index: Int) = "slot_${index}_url"

    companion object {
        private const val PREFS = "favorites"
        private const val LINK_MARKER = "link:"

        // Enough slots for the largest Home grid, less its fixed "All Apps" tile. A smaller
        // grid shows the leading slots and keeps the rest.
        val SLOT_COUNT = TileSize.entries.maxOf { it.homeTileCount } - 1
    }
}
