package de.codevoid.motolauncher.statusbar

import android.content.Context
import androidx.core.content.edit

class SpeedStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_SPEED_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_SPEED_ENABLED, value) } }

    var isMetric: Boolean
        get() = prefs.getBoolean(KEY_SPEED_METRIC, true)
        set(value) { prefs.edit { putBoolean(KEY_SPEED_METRIC, value) } }

    companion object {
        const val PREFS = "settings"
        const val KEY_SPEED_ENABLED = "speed_enabled"
        const val KEY_SPEED_METRIC = "speed_metric"
    }
}
