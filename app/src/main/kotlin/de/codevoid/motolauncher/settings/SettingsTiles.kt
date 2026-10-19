package de.codevoid.motolauncher.settings

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import de.codevoid.motolauncher.AppListActivity
import de.codevoid.motolauncher.BuildConfig
import de.codevoid.motolauncher.ParkActivity
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.BatteryDisplay
import de.codevoid.motolauncher.data.BatteryStore
import de.codevoid.motolauncher.data.CellularStore
import de.codevoid.motolauncher.data.HiddenAppsStore
import de.codevoid.motolauncher.data.NavAppStore
import de.codevoid.motolauncher.data.NavBarStore
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.data.ParkStore
import de.codevoid.motolauncher.data.SpeedStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.runUpdateFlow
import de.codevoid.motolauncher.ui.showImmersive
import de.codevoid.motolauncher.ui.showOptionsDialog

/**
 * The tiles of the app list's settings mode. Each tile applies its own change and calls
 * [onChanged] when the list needs rendering again; [patchTile] updates one shown tile's
 * subtitle in place. Construct it in `onCreate`, as it registers permission requests.
 */
class SettingsTiles(
    private val activity: AppCompatActivity,
    private val onChanged: () -> Unit,
    private val patchTile: (label: String, subtitle: String) -> Unit,
) {
    private val updateState: UpdateState by activity.viewModels()
    private val themeStore = ThemeStore(activity)
    private val navAppStore = NavAppStore(activity)
    private val parkStore = ParkStore(activity)
    private val cellularStore = CellularStore(activity)
    private val speedStore = SpeedStore(activity)
    private val batteryStore = BatteryStore(activity)
    private val hiddenAppsStore = HiddenAppsStore(activity)
    private val orientationStore = OrientationStore(activity)
    private val navBarStore = NavBarStore(activity)

    private val cellularPermission =
        activity.registerForActivityResult(RequestPermission()) { granted ->
            if (granted) cellularStore.enabled = true
            onChanged()
        }

    private val gpsPermission =
        activity.registerForActivityResult(RequestPermission()) { granted ->
            if (granted) speedStore.enabled = true
            onChanged()
        }

    fun build(): List<TileItem> {
        val hasGps = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        return listOfNotNull(
            themeTile(),
            navAppTile(),
            if (BuildConfig.SELF_UPDATE) updateTile() else null,
            if (offersParkLock()) parkLockTile() else null,
            cellularTile(),
            if (hasGps) speedTile() else null,
            if (hasGps) unitsTile() else null,
            batteryTile(),
            hiddenAppsTile(),
            orientationTile(),
            navBarTile(),
            privacyPolicyTile(),
        )
    }

    private fun themeTile() = TileItem(
        label = activity.getString(R.string.theme_label),
        subtitle = activity.getString(
            if (themeStore.isDark) R.string.theme_dark else R.string.theme_light
        ),
        onClick = { themeStore.isDark = !themeStore.isDark },
    )

    private fun navAppTile(): TileItem {
        val navApp = navAppStore.navApp
        val subtitle = if (navApp == null) {
            activity.getString(R.string.pick_none)
        } else {
            AppRepository(activity).loadByComponents(listOf(navApp))[navApp]?.label
                ?: navApp.packageName
        }
        return TileItem(
            label = activity.getString(R.string.nav_app),
            subtitle = subtitle,
            onClick = { activity.startActivity(AppListActivity.navAppPickerIntent(activity)) },
        )
    }

    private fun updateTile(): TileItem {
        val label = activity.getString(R.string.check_for_updates)
        val isChecking = updateState.isChecking
        return TileItem(
            label = label,
            subtitle = updateState.progress ?: when {
                isChecking -> activity.getString(R.string.checking_updates)
                else -> BuildConfig.VERSION_NAME
            },
            onClick = if (isChecking) ({}) else ({ checkForUpdate(label) }),
        )
    }

    private fun checkForUpdate(label: String) {
        updateState.isChecking = true
        onChanged()
        activity.runUpdateFlow(
            setClickable = { enabled ->
                if (enabled) {
                    updateState.isChecking = false
                    updateState.progress = null
                    onChanged()
                }
            },
            setSubtitle = { text ->
                updateState.progress = text
                patchTile(label, text)
            },
        )
    }

    private fun offersParkLock() = ParkActivity.shouldOfferParkLock(
        sdkInt = Build.VERSION.SDK_INT,
        deviceSecure = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true,
    )

    private fun parkLockTile(): TileItem {
        val hasPin = parkStore.hasPin
        return TileItem(
            label = activity.getString(R.string.park_lock),
            subtitle = activity.getString(
                if (hasPin) R.string.park_lock_now else R.string.park_set_pin
            ),
            onClick = { if (hasPin) lockNow() else openSetPin() },
            onLongClick = if (hasPin) ({ openSetPin(); true }) else null,
        )
    }

    private fun lockNow() {
        activity.startActivity(ParkActivity.lockIntent(activity))
        activity.noTransition()
        activity.finish()
    }

    private fun openSetPin() {
        activity.startActivity(ParkActivity.setPinIntent(activity))
        activity.noTransition()
    }

    private fun cellularTile() = permissionToggleTile(
        label = activity.getString(R.string.cellular_indicator),
        enabled = cellularStore.enabled,
        onDisable = { cellularStore.enabled = false },
        onEnable = { cellularPermission.launch(Manifest.permission.READ_PHONE_STATE) },
    )

    private fun speedTile() = permissionToggleTile(
        label = activity.getString(R.string.gps_speed),
        enabled = speedStore.enabled,
        onDisable = { speedStore.enabled = false },
        onEnable = { gpsPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )

    /** Off asks for the permission and turns on only when it is granted; On turns off directly. */
    private fun permissionToggleTile(
        label: String,
        enabled: Boolean,
        onDisable: () -> Unit,
        onEnable: () -> Unit,
    ) = TileItem(
        label = label,
        subtitle = activity.getString(if (enabled) R.string.setting_on else R.string.setting_off),
        onClick = {
            if (enabled) {
                onDisable()
                onChanged()
            } else {
                onEnable()
            }
        },
    )

    private fun unitsTile() = TileItem(
        label = activity.getString(R.string.units),
        subtitle = activity.getString(
            if (speedStore.isMetric) R.string.units_kmh else R.string.units_mph
        ),
        onClick = { speedStore.isMetric = !speedStore.isMetric; onChanged() },
    )

    private fun batteryTile(): TileItem {
        val subtitle = when (batteryStore.display) {
            BatteryDisplay.BOTH -> R.string.battery_display_both
            BatteryDisplay.ICON -> R.string.battery_display_icon
            BatteryDisplay.TEXT -> R.string.battery_display_text
        }
        return TileItem(
            label = activity.getString(R.string.battery_display),
            subtitle = activity.getString(subtitle),
            onClick = {
                val entries = BatteryDisplay.entries
                batteryStore.display = entries[(entries.indexOf(batteryStore.display) + 1) % entries.size]
                onChanged()
            },
        )
    }

    private fun hiddenAppsTile() = TileItem(
        label = activity.getString(R.string.hidden_apps),
        subtitle = activity.getString(
            if (hiddenAppsStore.showHidden) R.string.hidden_apps_showing else R.string.hidden_apps_hidden
        ),
        onClick = { hiddenAppsStore.showHidden = !hiddenAppsStore.showHidden; onChanged() },
    )

    private fun orientationTile(): TileItem {
        val current = ORIENTATIONS.indexOfFirst { it.first == orientationStore.orientation }
        return TileItem(
            label = activity.getString(R.string.orientation),
            subtitle = activity.getString(ORIENTATIONS[current].second),
            onClick = {
                showOptionsDialog(
                    context = activity,
                    title = activity.getString(R.string.orientation),
                    options = ORIENTATIONS.map { activity.getString(it.second) },
                    selectedIndex = current,
                ) { index ->
                    val picked = ORIENTATIONS[index].first
                    orientationStore.orientation = picked
                    activity.requestedOrientation = picked
                    onChanged()
                }
            },
        )
    }

    private fun navBarTile() = TileItem(
        label = activity.getString(R.string.nav_bar),
        subtitle = activity.getString(
            if (navBarStore.showNavBar) R.string.nav_bar_visible else R.string.nav_bar_hidden
        ),
        onClick = {
            val show = !navBarStore.showNavBar
            navBarStore.showNavBar = show
            activity.window.enableImmersiveMode(show)
            onChanged()
        },
    )

    private fun privacyPolicyTile() = TileItem(
        label = activity.getString(R.string.privacy_policy),
        onClick = {
            AlertDialog.Builder(activity)
                .setTitle(R.string.privacy_policy)
                .setMessage(R.string.privacy_policy_text)
                .setPositiveButton(R.string.ok, null)
                .create()
                .showImmersive()
        },
    )

    private companion object {
        val ORIENTATIONS = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to R.string.orientation_landscape,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to R.string.orientation_portrait,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to R.string.orientation_reverse_landscape,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to R.string.orientation_reverse_portrait,
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to R.string.orientation_sensor,
        )
    }
}

/** The update check's progress, kept across the theme toggle's recreate. */
class UpdateState : ViewModel() {
    var isChecking = false
    var progress: String? = null
}
