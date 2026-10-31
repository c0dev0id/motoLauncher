package de.codevoid.motolauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    suspend fun loadApps(): List<AppEntry> = coroutineScope {
        val self = context.packageName
        val infos = userManager.userProfiles.flatMap { user ->
            launcherApps.getActivityList(null, user)
                .filter { it.applicationInfo.packageName != self }
        }
        sortApps(infos.map { async { toEntry(it) } }.awaitAll())
    }

    /** Resolves only [keys], decoding only their icons; keys that no longer resolve are left out. */
    fun loadByKeys(keys: Collection<ComponentKey>): Map<ComponentKey, AppEntry> {
        val wanted = keys.toHashSet()
        val result = HashMap<ComponentKey, AppEntry>()
        for ((user, packages) in keys.groupBy({ it.user }, { it.component.packageName })) {
            for (pkg in packages.toSet()) {
                for (info in launcherApps.getActivityList(pkg, user)) {
                    val key = ComponentKey(info.componentName, info.user)
                    if (key in wanted) result[key] = toEntry(info)
                }
            }
        }
        return result
    }

    // Registered for the life of the process; nothing here unregisters.
    fun registerPackageCallback(callback: LauncherApps.Callback) {
        launcherApps.registerCallback(callback)
    }

    /** Starts [app] if it still resolves, and does nothing if it does not. */
    fun launch(app: ComponentKey) {
        if (launcherApps.isActivityEnabled(app.component, app.user)) {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        }
    }

    fun openInfo(app: ComponentKey) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    /** Hands [app] to the system uninstaller, which asks for confirmation itself. */
    fun requestUninstall(app: ComponentKey) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.component.packageName, null))
                .putExtra(Intent.EXTRA_USER, app.user)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun toEntry(info: LauncherActivityInfo) = AppEntry(
        key = ComponentKey(info.componentName, info.user),
        label = info.label?.toString().orEmpty(),
        icon = info.getBadgedIcon(0),
    )

    companion object {
        fun sortApps(apps: List<AppEntry>): List<AppEntry> =
            apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        fun filterApps(apps: List<AppEntry>, query: String): List<AppEntry> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return apps
            return apps.filter { it.label.contains(trimmed, ignoreCase = true) }
        }
    }
}
