package de.codevoid.motolauncher

import android.app.Application
import android.content.pm.LauncherApps
import android.os.UserHandle
import de.codevoid.motolauncher.apps.AppEntry
import de.codevoid.motolauncher.apps.AppRepository
import de.codevoid.motolauncher.home.FavoritesStore
import de.codevoid.motolauncher.settings.ThemeStore
import de.codevoid.motolauncher.update.UpdateChecker
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class MotoLauncherApp : Application() {

    /** Counts package changes; a screen holding app data from an older count reloads it. */
    var packageGeneration: Int = 0
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appsCache: Deferred<List<AppEntry>>? = null
    private var appsGeneration: Int = -1

    /** The installed apps, loaded once per package generation; a new instance means a new load. */
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
        ThemeStore(this).applyNightMode()
        if (BuildConfig.SELF_UPDATE) {
            thread(name = "delete-installed-update") { UpdateChecker(this).deleteInstalledUpdate() }
        }
        watchPackages()
    }

    private fun watchPackages() {
        AppRepository(this).registerPackageCallback(object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
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
