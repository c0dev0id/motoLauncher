package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager

class AppRepository(private val context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    fun loadApps(): List<AppEntry> {
        val self = context.packageName
        val entries = ArrayList<AppEntry>()
        for (user in userManager.userProfiles) {
            for (info in launcherApps.getActivityList(null, user)) {
                if (info.applicationInfo.packageName == self) continue
                entries.add(toEntry(info))
            }
        }
        return sortApps(entries)
    }

    // Resolves only the requested components (and decodes only their icons), so the home
    // grid doesn't enumerate and rasterize every installed app just to show its favorites.
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

    fun launch(component: ComponentName) {
        launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
    }

    fun openInfo(component: ComponentName) {
        launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
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
