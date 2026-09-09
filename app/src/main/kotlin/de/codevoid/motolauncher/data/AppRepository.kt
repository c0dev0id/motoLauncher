package de.codevoid.motolauncher.data

import android.content.Context
import android.content.pm.LauncherApps
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
                entries.add(
                    AppEntry(
                        component = info.componentName,
                        label = info.label?.toString().orEmpty(),
                        icon = info.getBadgedIcon(0),
                    )
                )
            }
        }
        return sortApps(entries)
    }

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
