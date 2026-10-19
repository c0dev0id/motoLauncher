package de.codevoid.motolauncher.settings

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.core.content.edit

class OrientationStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var orientation: Int
        get() = prefs.getInt(KEY, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
        set(value) { prefs.edit { putInt(KEY, value) } }

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "orientation"
    }
}
