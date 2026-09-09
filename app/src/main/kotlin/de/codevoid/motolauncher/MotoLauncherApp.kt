package de.codevoid.motolauncher

import android.app.Application
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.update.UpdateChecker
import kotlin.concurrent.thread

class MotoLauncherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Re-apply the saved theme before any activity is created so the choice survives restarts.
        ThemeStore(this).applyNightMode()
        // The update that was just installed has served its purpose once the launcher
        // comes back up. A plain thread rather than a coroutine: Home never loads
        // kotlinx.coroutines, and this keeps a possibly cold cache-dir read off the
        // first frame.
        thread(name = "delete-installed-update") { UpdateChecker(this).deleteInstalledUpdate() }
    }
}
