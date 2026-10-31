package de.codevoid.motolauncher

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import de.codevoid.motolauncher.data.AppEntry
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
import de.codevoid.motolauncher.data.TileSize
import de.codevoid.motolauncher.data.TileSizeStore
import de.codevoid.motolauncher.ui.MenuDialog
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.UpdateState
import de.codevoid.motolauncher.ui.UpdateViewModel
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.progressText
import de.codevoid.motolauncher.ui.showImmersive

/**
 * The app list's settings mode: its tiles and what tapping them does. Every change of a
 * setting ends in one call to [onChanged], after which the caller shows [tiles] again; the
 * caller also shows them again when [updates] changes state. Construct it in `onCreate`:
 * it registers the location permission request.
 */
class SettingsTiles(
    private val activity: AppCompatActivity,
    private val updates: UpdateViewModel,
    private val onChanged: () -> Unit,
) {
    private val themeStore = ThemeStore(activity)
    private val navAppStore = NavAppStore(activity)
    private val parkStore = ParkStore(activity)
    private val cellularStore = CellularStore(activity)
    private val speedStore = SpeedStore(activity)
    private val batteryStore = BatteryStore(activity)
    private val hiddenAppsStore = HiddenAppsStore(activity)
    private val orientationStore = OrientationStore(activity)
    private val tileSizeStore = TileSizeStore(activity)
    private val navBarStore = NavBarStore(activity)
    private val hasGps = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    // Speed turns on only when precise location is granted.
    private val locationPermission =
        activity.registerForActivityResult(RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) speedStore.enabled = true
            onChanged()
        }

    init {
        val fragments = activity.supportFragmentManager
        fragments.setFragmentResultListener(ORIENTATION_PICKER, activity) { _, result ->
            val orientation = result.getInt(MenuDialog.ROW)
            orientationStore.orientation = orientation
            activity.requestedOrientation = orientation
            onChanged()
        }
        fragments.setFragmentResultListener(TILE_SIZE_PICKER, activity) { _, result ->
            tileSizeStore.tileSize = TileSize.entries[result.getInt(MenuDialog.ROW)]
            onChanged()
        }
    }

    /** The settings tiles, in display order; [apps] supplies the navigation app's label once loaded. */
    fun tiles(apps: List<AppEntry>?): List<TileItem> = buildList {
        add(settingTile(R.string.theme_label, if (themeStore.isDark) R.string.theme_dark else R.string.theme_light) {
            themeStore.isDark = !themeStore.isDark
        })
        add(navAppTile(apps))
        if (BuildConfig.SELF_UPDATE) add(updateTile())
        if (offerParkLock()) add(parkLockTile())
        add(settingTile(R.string.cellular_indicator, onOff(cellularStore.enabled)) {
            cellularStore.enabled = !cellularStore.enabled
        })
        if (hasGps) {
            add(gpsTile())
            add(settingTile(R.string.units, if (speedStore.isMetric) R.string.units_kmh else R.string.units_mph) {
                speedStore.isMetric = !speedStore.isMetric
            })
        }
        add(settingTile(R.string.battery_display, batteryDisplayName(batteryStore.display)) {
            val entries = BatteryDisplay.entries
            batteryStore.display = entries[(entries.indexOf(batteryStore.display) + 1) % entries.size]
        })
        add(settingTile(
            R.string.hidden_apps,
            if (hiddenAppsStore.showHidden) R.string.hidden_apps_showing else R.string.hidden_apps_hidden,
        ) {
            hiddenAppsStore.showHidden = !hiddenAppsStore.showHidden
        })
        add(orientationTile())
        add(tileSizeTile())
        add(settingTile(R.string.nav_bar, if (navBarStore.showNavBar) R.string.nav_bar_visible else R.string.nav_bar_hidden) {
            navBarStore.showNavBar = !navBarStore.showNavBar
            activity.window.enableImmersiveMode(navBarStore.showNavBar)
        })
        add(TileItem(label = activity.getString(R.string.privacy_policy), onClick = ::showPrivacyPolicy))
    }

    // A tile whose tap changes its setting in place.
    private fun settingTile(@StringRes label: Int, @StringRes value: Int, change: () -> Unit) = TileItem(
        label = activity.getString(label),
        subtitle = activity.getString(value),
        onClick = { change(); onChanged() },
    )

    private fun navAppTile(apps: List<AppEntry>?): TileItem {
        val navApp = navAppStore.navApp
        val name = if (navApp == null) activity.getString(R.string.pick_none)
        else apps?.firstOrNull { it.key == navApp }?.label ?: navApp.component.packageName
        return TileItem(
            label = activity.getString(R.string.nav_app),
            subtitle = name,
            onClick = { activity.startActivity(AppListActivity.navAppPickerIntent(activity)) },
        )
    }

    private fun updateTile() = TileItem(
        label = activity.getString(R.string.check_for_updates),
        subtitle = when (val state = updates.state.value) {
            UpdateState.Idle -> BuildConfig.VERSION_NAME
            UpdateState.Checking -> activity.getString(R.string.checking_updates)
            is UpdateState.Downloading -> state.progressText()
        },
        onClick = updates::check,
    )

    private fun offerParkLock() = ParkLockRules.shouldOfferParkLock(
        sdkInt = Build.VERSION.SDK_INT,
        deviceSecure = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true,
    )

    private fun parkLockTile(): TileItem {
        val hasPin = parkStore.hasPin
        val openSetPin = {
            activity.startActivity(ParkActivity.setPinIntent(activity))
            activity.noTransition()
        }
        return TileItem(
            label = activity.getString(R.string.park_lock),
            subtitle = activity.getString(if (hasPin) R.string.park_lock_now else R.string.park_set_pin),
            onClick = {
                if (hasPin) {
                    activity.startActivity(ParkActivity.lockIntent(activity))
                    activity.noTransition()
                    activity.finish()
                } else {
                    openSetPin()
                }
            },
            onLongClick = if (hasPin) ({ openSetPin(); true }) else null,
        )
    }

    private fun gpsTile(): TileItem {
        val isOn = speedStore.enabled && ContextCompat.checkSelfPermission(
            activity, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return TileItem(
            label = activity.getString(R.string.gps_speed),
            subtitle = activity.getString(onOff(isOn)),
            onClick = {
                if (isOn) {
                    speedStore.enabled = false
                    onChanged()
                } else {
                    locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ))
                }
            },
        )
    }

    // Each row's id is the orientation constant it applies.
    private fun orientationTile(): TileItem {
        val current = orientationStore.orientation
        return TileItem(
            label = activity.getString(R.string.orientation),
            subtitle = activity.getString(ORIENTATIONS.first { it.first == current }.second),
            onClick = {
                MenuDialog.show(
                    activity.supportFragmentManager,
                    ORIENTATION_PICKER,
                    title = activity.getString(R.string.orientation),
                    rows = ORIENTATIONS.map { it.first to activity.getString(it.second) },
                    selected = current,
                )
            },
        )
    }

    // Each row's id is the tile size's ordinal.
    private fun tileSizeTile(): TileItem = TileItem(
        label = activity.getString(R.string.tile_size),
        subtitle = activity.getString(tileSizeName(tileSizeStore.tileSize)),
        onClick = {
            MenuDialog.show(
                activity.supportFragmentManager,
                TILE_SIZE_PICKER,
                title = activity.getString(R.string.tile_size),
                rows = TileSize.entries.map {
                    it.ordinal to activity.resources.getQuantityString(
                        R.plurals.tile_size_option,
                        it.homeTileCount,
                        activity.getString(tileSizeName(it)),
                        it.homeTileCount,
                    )
                },
                selected = tileSizeStore.tileSize.ordinal,
            )
        },
    )

    private fun showPrivacyPolicy() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.privacy_policy)
            .setMessage(R.string.privacy_policy_text)
            .setPositiveButton(R.string.ok, null)
            .create()
            .showImmersive()
    }

    private companion object {
        const val ORIENTATION_PICKER = "settings.orientation"
        const val TILE_SIZE_PICKER = "settings.tileSize"

        val ORIENTATIONS = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to R.string.orientation_landscape,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to R.string.orientation_portrait,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to R.string.orientation_reverse_landscape,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to R.string.orientation_reverse_portrait,
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR to R.string.orientation_sensor,
        )

        @StringRes
        fun onOff(on: Boolean) = if (on) R.string.setting_on else R.string.setting_off

        @StringRes
        fun batteryDisplayName(display: BatteryDisplay) = when (display) {
            BatteryDisplay.BOTH -> R.string.battery_display_both
            BatteryDisplay.ICON -> R.string.battery_display_icon
            BatteryDisplay.TEXT -> R.string.battery_display_text
        }

        @StringRes
        fun tileSizeName(size: TileSize) = when (size) {
            TileSize.LARGE -> R.string.tile_size_large
            TileSize.MEDIUM -> R.string.tile_size_medium
            TileSize.SMALL -> R.string.tile_size_small
        }
    }
}
