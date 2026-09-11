package de.codevoid.motolauncher.data

import android.content.Context

class BatteryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var display: String
        get() = prefs.getString(KEY_BATTERY_DISPLAY, DISPLAY_BOTH) ?: DISPLAY_BOTH
        set(value) { prefs.edit().putString(KEY_BATTERY_DISPLAY, value).apply() }

    companion object {
        private const val PREFS = "settings"
        const val KEY_BATTERY_DISPLAY = "battery_display"
        const val DISPLAY_BOTH = "both"
        const val DISPLAY_ICON = "icon"
        const val DISPLAY_TEXT = "text"

        val OPTIONS = listOf(DISPLAY_BOTH, DISPLAY_ICON, DISPLAY_TEXT)
    }
}
