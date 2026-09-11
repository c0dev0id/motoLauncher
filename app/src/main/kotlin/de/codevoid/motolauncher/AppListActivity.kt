package de.codevoid.motolauncher

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.BuildConfig
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.BatteryStore
import de.codevoid.motolauncher.data.CellularStore
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.HiddenAppsStore
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.data.SpeedStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.isPortrait
import de.codevoid.motolauncher.ui.launchFirstFavorite
import de.codevoid.motolauncher.ui.runUpdateFlow
import de.codevoid.motolauncher.ui.showTileActionsDialog
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private val viewModel: AppListViewModel by viewModels()
    private val adapter = AppTileAdapter(emptyList())
    private val themeStore by lazy { ThemeStore(this) }
    private val speedStore by lazy { SpeedStore(this) }
    private val cellularStore by lazy { CellularStore(this) }
    private val batteryStore by lazy { BatteryStore(this) }
    private val hiddenAppsStore by lazy { HiddenAppsStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }
    private val columns get() = if (isPortrait) 4 else 5
    private var defaultSettingsTint: ColorStateList? = null
    private var activeSettingsTint: ColorStateList? = null
    // Invalidated when isCheckingUpdate, cellular permission, or GPS permission state changes.
    private var settingsTilesCache: List<TileItem>? = null
    private var allApps: List<AppEntry> = emptyList()

    // >= 0: pick mode — the chosen app is written to that favourite slot and the
    // activity finishes. NO_SLOT: browse mode — tap launches, long-press opens the
    // tile-actions menu.
    private val pickSlot by lazy { intent.getIntExtra(EXTRA_PICK_SLOT, NO_SLOT) }
    private val pickMode get() = pickSlot != NO_SLOT

    // In browse mode a short ESC exits settings mode (if active) before exiting the
    // screen, so Escape acts as a mode-level back. In pick mode there is no settings
    // mode, so a short ESC always finishes. Holding ESC quick-launches slot 0, same
    // as Home, so the gesture means one thing wherever the remote is.
    private val escapeKeys = EscapeKeys(
        onLongPress = { launchFirstFavorite() },
        onShortPress = {
            if (viewModel.isSettingsMode) {
                viewModel.isSettingsMode = false
                renderCurrentMode()
            } else {
                finish()
            }
        },
    )

    private val cellularPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) cellularStore.enabled = true
            settingsTilesCache = null
            renderCurrentMode()
        }

    private val gpsPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) speedStore.enabled = true
            settingsTilesCache = null
            renderCurrentMode()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode()

        defaultSettingsTint = binding.settingsButton.backgroundTintList
        activeSettingsTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tile_focused))

        repository = AppRepository(this)

        binding.backButton.setOnClickListener { finish() }

        binding.appGrid.layoutManager = GridLayoutManager(this, columns)
        binding.appGrid.adapter = adapter

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { renderCurrentMode() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // The picker is a single-purpose screen: hide the settings toggle so the user
        // can only pick an app or cancel.
        if (pickMode) {
            binding.settingsButton.visibility = View.GONE
        } else {
            binding.settingsButton.setOnClickListener {
                viewModel.isSettingsMode = !viewModel.isSettingsMode
                renderCurrentMode()
            }
        }

        loadAndRender(seedFocus = true)
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        // Enumerating apps is the most expensive thing this app does; reload only when the
        // installed-app set actually changed (uninstall, install) since the last load.
        if (viewModel.reloadIfStale()) loadAndRender(seedFocus = false)
        else renderCurrentMode()
    }

    private fun loadAndRender(seedFocus: Boolean) {
        lifecycleScope.launch {
            allApps = viewModel.apps.await()
            // After a recreate the restored search text is already in the box; honour it.
            renderCurrentMode()
            // Only on first load: re-seeding would drag the remote's focus back to tile 0
            // every time an app is installed or removed.
            if (seedFocus) {
                binding.appGrid.post {
                    binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
                }
            }
        }
    }

    // Single dispatch point for both modes: tints the toggle to reflect the active state,
    // swaps the search hint, and updates the grid with the current search text applied.
    private fun renderCurrentMode() {
        if (!pickMode) {
            binding.settingsButton.backgroundTintList =
                if (viewModel.isSettingsMode) activeSettingsTint else defaultSettingsTint
        }
        binding.searchBox.setHint(
            if (viewModel.isSettingsMode) R.string.search_settings_hint else R.string.search_hint
        )
        val query = binding.searchBox.text.toString().trim()
        if (viewModel.isSettingsMode) {
            val all = settingsTilesCache ?: buildSettingsTiles().also { settingsTilesCache = it }
            adapter.submit(if (query.isEmpty()) all else all.filter { it.label.contains(query, ignoreCase = true) })
        } else {
            render(AppRepository.filterApps(allApps, query))
        }
    }

    // Settings tiles replace the app grid in settings mode. Each tile is text-only (no
    // icon): the label is the action, and these tiles have enough vertical room to read.
    private fun buildSettingsTiles(): List<TileItem> {
        val tiles = mutableListOf<TileItem>()

        tiles.add(TileItem(
            label = getString(R.string.theme_label),
            subtitle = getString(if (themeStore.isDark) R.string.theme_dark else R.string.theme_light),
            onClick = { settingsTilesCache = null; themeStore.isDark = !themeStore.isDark },
        ))

        // While checking, the subtitle changes and the click is a no-op — guarding
        // against a double-tap while the network call is in flight.
        val isChecking = viewModel.isCheckingUpdate
        tiles.add(TileItem(
            label = getString(R.string.check_for_updates),
            subtitle = if (isChecking) getString(R.string.checking_updates) else BuildConfig.VERSION_NAME,
            onClick = if (isChecking) ({}) else ({
                viewModel.isCheckingUpdate = true
                settingsTilesCache = null
                renderCurrentMode()
                runUpdateFlow(
                    setClickable = { enabled ->
                        // setClickable(true) is the "done" signal from runUpdateFlow.
                        if (enabled) {
                            viewModel.isCheckingUpdate = false
                            settingsTilesCache = null
                            renderCurrentMode()
                        }
                    },
                )
            }),
        ))

        tiles.add(permissionToggleTile(
            label = getString(R.string.cellular_indicator),
            enabled = cellularStore.enabled,
            onDisable = { cellularStore.enabled = false },
            onEnable = { cellularPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE) },
        ))

        if (packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LOCATION_GPS)) {
            tiles.add(permissionToggleTile(
                label = getString(R.string.gps_speed),
                enabled = speedStore.enabled,
                onDisable = { speedStore.enabled = false },
                onEnable = { gpsPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            ))
            tiles.add(TileItem(
                label = getString(R.string.units),
                subtitle = getString(if (speedStore.isMetric) R.string.units_kmh else R.string.units_mph),
                onClick = { settingsTilesCache = null; speedStore.isMetric = !speedStore.isMetric; renderCurrentMode() },
            ))
        }

        tiles.add(batteryDisplayTile())

        tiles.add(TileItem(
            label = getString(R.string.hidden_apps),
            subtitle = getString(if (hiddenAppsStore.showHidden) R.string.hidden_apps_showing else R.string.hidden_apps_hidden),
            onClick = { settingsTilesCache = null; hiddenAppsStore.showHidden = !hiddenAppsStore.showHidden; renderCurrentMode() },
        ))

        tiles.add(orientationTile())

        return tiles
    }

    // Tapping "Off" launches the permission request; if granted the launcher callback sets
    // enabled=true and re-renders. If already granted the system skips the dialog.
    // Tapping "On" disables directly without a permission check.
    private fun permissionToggleTile(
        label: String,
        enabled: Boolean,
        onDisable: () -> Unit,
        onEnable: () -> Unit,
    ): TileItem = TileItem(
        label = label,
        subtitle = getString(if (enabled) R.string.setting_on else R.string.setting_off),
        onClick = {
            if (enabled) {
                onDisable()
                settingsTilesCache = null
                renderCurrentMode()
            } else {
                onEnable()
            }
        },
    )

    private fun batteryDisplayTile(): TileItem {
        val subtitleRes = when (batteryStore.display) {
            BatteryStore.DISPLAY_ICON -> R.string.battery_display_icon
            BatteryStore.DISPLAY_TEXT -> R.string.battery_display_text
            else -> R.string.battery_display_both
        }
        return TileItem(
            label = getString(R.string.battery_display),
            subtitle = getString(subtitleRes),
            onClick = {
                val opts = BatteryStore.OPTIONS
                batteryStore.display = opts[(opts.indexOf(batteryStore.display) + 1) % opts.size]
                settingsTilesCache = null
                renderCurrentMode()
            },
        )
    }

    private fun orientationTile(): TileItem {
        val current = orientationStore.orientation
        val idx = ORIENTATION_OPTIONS.indexOfFirst { it.first == current }.coerceAtLeast(0)
        return TileItem(
            label = getString(R.string.orientation),
            subtitle = getString(ORIENTATION_OPTIONS[idx].second),
            onClick = {
                val cur = orientationStore.orientation
                val next = ORIENTATION_OPTIONS[(ORIENTATION_OPTIONS.indexOfFirst { it.first == cur }.coerceAtLeast(0) + 1) % ORIENTATION_OPTIONS.size].first
                orientationStore.orientation = next
                requestedOrientation = next
                settingsTilesCache = null
                renderCurrentMode()
            },
        )
    }

    private fun render(apps: List<AppEntry>) {
        val tiles = ArrayList<TileItem>(apps.size + 1)
        // In pick mode a "None" tile leads the grid, whatever the search says: choosing it
        // clears the slot. It is the only way to empty a favourite.
        if (pickMode) tiles.add(noneTile)
        val hiddenSet = hiddenAppsStore.hiddenPackages()
        val showHidden = hiddenAppsStore.showHidden
        apps.forEach { entry ->
            val isHidden = entry.component.packageName in hiddenSet
            if (isHidden && !showHidden) return@forEach
            tiles.add(TileItem(
                label = entry.label,
                icon = entry.icon,
                dimmed = isHidden,
                onClick = { onAppSelected(entry.component) },
                onLongClick = if (pickMode) null else ({
                    showTileActionsDialog(
                        context = this,
                        entry = entry,
                        onAppInfo = { repository.openInfo(entry.component) },
                        onUninstall = { repository.requestUninstall(entry.component) },
                        hideAction = if (isHidden)
                            R.string.tile_action_unhide to { hiddenAppsStore.unhide(entry.component.packageName); renderCurrentMode() }
                        else
                            R.string.tile_action_hide to { hiddenAppsStore.hide(entry.component.packageName); renderCurrentMode() },
                    )
                    true
                }),
            ))
        }
        adapter.submit(tiles)
    }

    // Built once: render() runs on every keystroke and this tile never changes.
    private val noneTile by lazy {
        TileItem(
            label = getString(R.string.pick_none),
            icon = ContextCompat.getDrawable(this, R.drawable.ic_none)!!,
            onClick = {
                FavoritesStore(this).clearSlot(pickSlot)
                finish()
            },
        )
    }

    private fun onAppSelected(component: ComponentName) {
        if (pickMode) {
            FavoritesStore(this).setSlot(pickSlot, component)
            finish()
        } else {
            repository.launch(component)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyLongPress(keyCode, event) || super.onKeyLongPress(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    companion object {
        private const val EXTRA_PICK_SLOT = "pick_slot"
        private const val NO_SLOT = -1

        private val ORIENTATION_OPTIONS = listOf(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to R.string.orientation_landscape,
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to R.string.orientation_portrait,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE to R.string.orientation_reverse_landscape,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT to R.string.orientation_reverse_portrait,
        )

        // Callers rebuild their grid in onResume, so no result contract is needed.
        fun pickIntent(context: Context, slot: Int): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_SLOT, slot)
    }
}

// Enumerating and rasterising every installed app is the most expensive thing the app
// does. Keeping the result in a ViewModel means the theme toggle's recreate() reuses it
// instead of running it again. isSettingsMode survives recreate so the screen returns to
// settings mode after the theme change that caused the recreate.
class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private var loadedGeneration = packageGeneration()

    var apps: Deferred<List<AppEntry>> = load()
        private set

    var isSettingsMode: Boolean = false
    var isCheckingUpdate: Boolean = false

    /** Starts a fresh load if apps were installed or removed since the last one. */
    fun reloadIfStale(): Boolean {
        val current = packageGeneration()
        if (current == loadedGeneration) return false
        loadedGeneration = current
        apps = load()
        return true
    }

    private fun load(): Deferred<List<AppEntry>> = viewModelScope.async(Dispatchers.IO) {
        AppRepository(getApplication<MotoLauncherApp>()).loadApps()
    }

    private fun packageGeneration() = getApplication<MotoLauncherApp>().packageGeneration
}
