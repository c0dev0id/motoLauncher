package de.codevoid.motolauncher

import android.app.Application
import de.codevoid.motolauncher.data.ThemeStore

class MotoLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-apply the saved theme before any activity is created so the choice survives restarts.
        ThemeStore(this).apply()
    }
}
