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
        // A downloaded update has served its purpose once the launcher comes back up
        // after installing it. Process start, not activity resume, so an in-flight
        // download can never be deleted underneath the installer. Off the main thread
        // to keep it out of the cold-start path.
        thread(name = "clear-updates") { UpdateChecker(this).clearDownloads() }
    }
}
