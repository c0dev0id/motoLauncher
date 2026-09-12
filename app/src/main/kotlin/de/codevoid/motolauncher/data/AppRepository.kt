package de.codevoid.motolauncher.data

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

    // LauncherApps reports adds, removals and profile availability for exactly the apps
    // this class enumerates, so the change feed comes from the same place as the data.
    // Registered for the life of the process; nothing here unregisters.
    fun registerPackageCallback(callback: LauncherApps.Callback) {
        launcherApps.registerCallback(callback)
    }

    fun launch(component: ComponentName) {
        launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
    }

    // A favourite outlives its app: the component stored in SharedPreferences may point
    // at something since uninstalled, and startMainActivity throws on a component that no
    // longer resolves. Callers launching a stored component rather than a resolved
    // AppEntry go through here.
    fun launchIfInstalled(component: ComponentName) {
        if (launcherApps.isActivityEnabled(component, Process.myUserHandle())) launch(component)
    }

    fun openInfo(component: ComponentName) {
        launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
    }

    // Hands off to the system uninstaller, which asks for confirmation itself. Two
    // manifest entries make that hand-off work, and it fails silently without either:
    // REQUEST_DELETE_PACKAGES (mandatory since targetSdk 28 for requesting a package
    // delete) and a <queries> entry for ACTION_DELETE, because the uninstaller has no
    // launcher entry and package visibility filters intent resolution. The favourite tile
    // self-heals to "+" on the next resume because loadByComponents no longer resolves it.
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
