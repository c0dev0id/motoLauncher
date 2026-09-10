package de.codevoid.motolauncher.data

import android.content.Context

class SpeedStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_SPEED_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_SPEED_ENABLED, value).apply() }

    var isMetric: Boolean
        get() = prefs.getBoolean(KEY_SPEED_METRIC, true)
        set(value) { prefs.edit().putBoolean(KEY_SPEED_METRIC, value).apply() }

    companion object {
        const val PREFS = "settings"
        const val KEY_SPEED_ENABLED = "speed_enabled"
        const val KEY_SPEED_METRIC = "speed_metric"
    }
}
