package de.codevoid.motolauncher

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.update.UpdateChecker
import kotlin.concurrent.thread

class MotoLauncherApp : Application() {

    /**
     * Bumped whenever the installed-app set changes. The app list caches an enumeration
     * that is the most expensive thing this app does, so it compares this on resume to
     * learn whether the cache is stale instead of re-running the enumeration to find out.
     *
     * Read and written on the main thread only — `LauncherApps` delivers its callbacks
     * there, and the app list reads it from `onResume`.
     */
    var packageGeneration: Int = 0
        private set

    override fun onCreate() {
        super.onCreate()
        // Re-apply the saved theme before any activity is created so the choice survives restarts.
        ThemeStore(this).applyNightMode()
        // The update that was just installed has served its purpose once the launcher
        // comes back up. A plain thread rather than a coroutine: Home never loads
        // kotlinx.coroutines, and this keeps a possibly cold cache-dir read off the
        // first frame.
        thread(name = "delete-installed-update") { UpdateChecker(this).deleteInstalledUpdate() }
        watchPackages()
    }

    // One registration for the life of the process, rather than one per activity: the
    // favourites it repairs are process-wide state, and an activity that is paused during
    // an uninstall would miss the event that concerns it most.
    private fun watchPackages() {
        AppRepository(this).registerPackageCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                // Only an actual removal clears a favourite. "Failed to resolve" must not:
                // an app on unmounted external storage is unavailable, not uninstalled,
                // and dropping the slot then would lose the configuration for good.
                FavoritesStore(this@MotoLauncherApp).clearSlotsForPackage(packageName)
                packageGeneration++
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                packageGeneration++
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                packageGeneration++
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageGeneration++
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                packageGeneration++
            }
        })
    }
}
