package de.codevoid.motolauncher.statusbar

import android.content.Context
import androidx.core.content.edit

class CellularStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_CELLULAR_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_CELLULAR_ENABLED, value) } }

    companion object {
        private const val PREFS = "settings"
        const val KEY_CELLULAR_ENABLED = "cellular_enabled"
    }
}
