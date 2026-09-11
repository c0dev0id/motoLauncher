package de.codevoid.motolauncher.data

import android.content.Context

enum class BatteryDisplay { BOTH, ICON, TEXT }

class BatteryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var display: BatteryDisplay
        get() = prefs.getString(KEY_BATTERY_DISPLAY, null)
            ?.let { runCatching { BatteryDisplay.valueOf(it) }.getOrNull() }
            ?: BatteryDisplay.BOTH
        set(value) { prefs.edit().putString(KEY_BATTERY_DISPLAY, value.name).apply() }

    companion object {
        private const val PREFS = "settings"
        const val KEY_BATTERY_DISPLAY = "battery_display"
    }
}
