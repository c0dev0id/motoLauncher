package de.codevoid.motolauncher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
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

    /** Resolves only [components], without enumerating every installed app. */
    fun loadByComponents(components: Collection<ComponentName>): Map<ComponentName, AppEntry> {
        if (components.isEmpty()) return emptyMap()
        val wanted = components.toHashSet()
        val packages = components.mapTo(HashSet()) { it.packageName }
        val result = HashMap<ComponentName, AppEntry>()
        for (user in userManager.userProfiles) {
            for (pkg in packages) {
                for (info in launcherApps.getActivityList(pkg, user)) {
                    if (info.componentName in wanted) result[info.componentName] = toEntry(info)
                }
            }
        }
        return result
    }

    fun registerPackageCallback(callback: LauncherApps.Callback) {
        launcherApps.registerCallback(callback)
    }

    fun launch(component: ComponentName) {
        launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
    }

    /** Launches a stored [component], or does nothing if it is no longer installed. */
    fun launchIfInstalled(component: ComponentName) {
        if (launcherApps.isActivityEnabled(component, Process.myUserHandle())) launch(component)
    }

    fun openInfo(component: ComponentName) {
        launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
    }

    /** Hands [component]'s package to the system uninstaller, which asks for confirmation. */
    fun requestUninstall(component: ComponentName) {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.fromParts("package", component.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun toEntry(info: LauncherActivityInfo) = AppEntry(
        component = info.componentName,
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
