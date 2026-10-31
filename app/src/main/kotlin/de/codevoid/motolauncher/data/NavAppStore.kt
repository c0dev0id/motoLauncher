package de.codevoid.motolauncher.data

import android.content.Context
import android.os.UserManager

class NavAppStore(context: Context) {

    private val prefs = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val users = context.getSystemService(Context.USER_SERVICE) as UserManager

    var navApp: ComponentKey?
        get() = prefs.getComponentKey(KEY, users)
        set(value) {
            if (value != null) prefs.edit().putComponentKey(KEY, value, users).apply()
            else prefs.edit().removeComponentKey(KEY).apply()
        }

    companion object {
        private const val KEY = "nav_app"
    }
}
