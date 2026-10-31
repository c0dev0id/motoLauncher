package de.codevoid.motolauncher

import android.content.pm.LauncherApps
import android.os.UserHandle
import de.codevoid.motolauncher.data.FavoritesStore
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Advances [generation] on every package event and clears the favourites of an
 * uninstalled app. LauncherApps calls it on the main thread.
 */
class PackageWatcher(
    private val favorites: FavoritesStore,
    private val generation: MutableStateFlow<Int>,
) : LauncherApps.Callback() {

    override fun onPackageRemoved(packageName: String, user: UserHandle) {
        favorites.clearSlotsForPackage(packageName, user)
        generation.value++
    }

    override fun onPackageAdded(packageName: String, user: UserHandle) {
        generation.value++
    }

    override fun onPackageChanged(packageName: String, user: UserHandle) {
        generation.value++
    }

    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        generation.value++
    }

    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
        generation.value++
    }
}
