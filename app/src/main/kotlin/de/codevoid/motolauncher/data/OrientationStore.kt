package de.codevoid.motolauncher.data

import android.content.Context
import android.content.pm.ActivityInfo

class OrientationStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var orientation: Int
        get() = prefs.getInt(KEY, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        set(value) { prefs.edit().putInt(KEY, value).apply() }

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "orientation"
    }
}
