package de.codevoid.motolauncher.settings

import android.content.Context
import androidx.core.content.edit

class NavBarStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var showNavBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NAV_BAR, false)
        set(value) { prefs.edit { putBoolean(KEY_SHOW_NAV_BAR, value) } }

    companion object {
        private const val PREFS = "settings"
        const val KEY_SHOW_NAV_BAR = "show_nav_bar"
    }
}
