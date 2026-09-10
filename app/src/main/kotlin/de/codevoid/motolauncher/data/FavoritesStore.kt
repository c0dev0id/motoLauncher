package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.content.Context

class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getSlot(index: Int): ComponentName? =
        prefs.getString(key(index), null)?.let { ComponentName.unflattenFromString(it) }

    fun setSlot(index: Int, component: ComponentName) {
        prefs.edit().putString(key(index), component.flattenToString()).apply()
    }

    fun clearSlot(index: Int) {
        prefs.edit().remove(key(index)).apply()
    }

    fun allSlots(): List<ComponentName?> = (0 until SLOT_COUNT).map { getSlot(it) }

    // Called when a package is uninstalled: without this the slot keeps a component that
    // can never resolve again, drawn as an empty "+" that is not actually empty.
    fun clearSlotsForPackage(packageName: String) {
        for (index in 0 until SLOT_COUNT) {
            if (getSlot(index)?.packageName == packageName) clearSlot(index)
        }
    }

    private fun key(index: Int) = "slot_$index"

    companion object {
        private const val PREFS = "favorites"

        // Home grid is 4x3 = 12 cells; the last cell is the fixed "All Apps" tile,
        // leaving 11 configurable favorite slots.
        const val SLOT_COUNT = 11
    }
}
