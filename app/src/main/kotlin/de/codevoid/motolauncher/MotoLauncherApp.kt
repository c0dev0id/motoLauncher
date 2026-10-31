package de.codevoid.motolauncher

import android.app.Application
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MotoLauncherApp : Application() {

    private val _packageGeneration = MutableStateFlow(0)

    /** Advances on the main thread whenever LauncherApps reports a package change. */
    val packageGeneration: StateFlow<Int> = _packageGeneration.asStateFlow()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appsCache: Deferred<List<AppEntry>>? = null
    private var appsGeneration: Int = -1

    /** The app list of the current package generation, loaded once per generation. Main thread only. */
    fun getApps(): Deferred<List<AppEntry>> {
        val current = packageGeneration.value
        val cached = appsCache
        if (cached != null && appsGeneration == current) return cached
        appsGeneration = current
        return appScope.async { AppRepository(this@MotoLauncherApp).loadApps() }
            .also { appsCache = it }
    }

    override fun onCreate() {
        super.onCreate()
        // Before any activity is created, so the saved theme survives restarts.
        ThemeStore(this).applyNightMode()
        if (BuildConfig.SELF_UPDATE) {
            appScope.launch { UpdateChecker(this@MotoLauncherApp).deleteInstalledUpdate() }
        }
        AppRepository(this).registerPackageCallback(
            PackageWatcher(FavoritesStore(this), _packageGeneration),
        )
    }
}
