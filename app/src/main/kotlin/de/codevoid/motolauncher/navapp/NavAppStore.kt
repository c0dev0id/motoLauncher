package de.codevoid.motolauncher.navapp

import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit

class NavAppStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var navApp: ComponentName?
        get() = prefs.getString(KEY, null)?.let { ComponentName.unflattenFromString(it) }
        set(value) {
            prefs.edit { if (value != null) putString(KEY, value.flattenToString()) else remove(KEY) }
        }

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "nav_app"
    }
}
