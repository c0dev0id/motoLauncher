package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.content.Context

class NavAppStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var navApp: ComponentName?
        get() = prefs.getString(KEY, null)?.let { ComponentName.unflattenFromString(it) }
        set(value) {
            if (value != null) prefs.edit().putString(KEY, value.flattenToString()).apply()
            else prefs.edit().remove(KEY).apply()
        }

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "nav_app"
    }
}
