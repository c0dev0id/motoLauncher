package de.codevoid.motolauncher

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.update.UpdateChecker
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class MotoLauncherApp : Application() {

    // Bumped on the main thread by LauncherApps callbacks. Read on the main thread from
    // onResume to detect whether the cached app list is stale.
    var packageGeneration: Int = 0
        private set

    // Process-scoped app list cache. Outlives individual Activity instances so the
    // Home ↔ AppList bounce reuses the loaded list instead of re-decoding all icons.
    // All access is on the main thread; the actual load runs on the IO dispatcher.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appsCache: Deferred<List<AppEntry>>? = null
    private var appsGeneration: Int = -1

    // Returns the cached Deferred if packageGeneration hasn't moved, otherwise starts
    // a fresh load. Callers detect a cache miss by comparing the returned instance.
    fun getApps(): Deferred<List<AppEntry>> {
        val current = packageGeneration
        val cached = appsCache
        if (cached != null && appsGeneration == current) return cached
        appsGeneration = current
        return appScope.async { AppRepository(this@MotoLauncherApp).loadApps() }
            .also { appsCache = it }
    }

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
