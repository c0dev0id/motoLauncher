package de.codevoid.motolauncher.applist

import android.content.Context
import androidx.core.content.edit

class HiddenAppsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Returns a mutable copy — callers must not mutate the live SharedPreferences set.
    fun hiddenPackages(): Set<String> = prefs.getStringSet(KEY_HIDDEN, emptySet())!!.toHashSet()

    fun hide(packageName: String) {
        prefs.edit { putStringSet(KEY_HIDDEN, hiddenPackages() + packageName) }
    }

    fun unhide(packageName: String) {
        prefs.edit { putStringSet(KEY_HIDDEN, hiddenPackages() - packageName) }
    }

    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW, false)
        set(value) { prefs.edit { putBoolean(KEY_SHOW, value) } }

    companion object {
        private const val PREFS = "hidden_apps"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SHOW = "show_hidden"
    }
}
