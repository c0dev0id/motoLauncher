package de.codevoid.motolauncher.applist

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.MotoLauncherApp
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.apps.AppEntry
import de.codevoid.motolauncher.apps.AppRepository
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.home.FavoritesStore
import de.codevoid.motolauncher.home.SlotEntry
import de.codevoid.motolauncher.home.showLinkDialog
import de.codevoid.motolauncher.navapp.NavAppStore
import de.codevoid.motolauncher.navapp.launchNavApp
import de.codevoid.motolauncher.settings.NavBarStore
import de.codevoid.motolauncher.settings.OrientationStore
import de.codevoid.motolauncher.settings.SettingsTiles
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import de.codevoid.motolauncher.ui.isPortrait
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.showTileActionsDialog
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private lateinit var settings: SettingsTiles
    private val viewModel: AppListViewModel by viewModels()
    private val adapter = AppTileAdapter(emptyList())
    private val favoritesStore by lazy { FavoritesStore(this) }
    private val navAppStore by lazy { NavAppStore(this) }
    private val hiddenAppsStore by lazy { HiddenAppsStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }
    private val columns get() = if (isPortrait) 4 else 5
    private val defaultSettingsTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tile_default)) }
    private val activeSettingsTint by lazy { ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tile_focused)) }
    private var allApps: List<AppEntry> = emptyList()

    // A slot pick writes the chosen app to that favourite slot, a nav-app pick stores it as
    // the navigation app; both finish on selection. Neither means browse mode.
    private val pickSlot by lazy { intent.getIntExtra(EXTRA_PICK_SLOT, NO_SLOT) }
    private val navAppPickMode by lazy { intent.getBooleanExtra(EXTRA_PICK_NAV_APP, false) }
    private val pickMode get() = pickSlot != NO_SLOT || navAppPickMode

    private val escapeKeys = EscapeKeys(
        onLongPress = { launchNavApp() },
        onShortPress = { if (viewModel.isSettingsMode) toggleSettingsMode() else finish() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        repository = AppRepository(this)
        settings = SettingsTiles(this, onChanged = ::render, patchTile = ::patchSettingsTile)

        binding.backButton.setOnClickListener { finish() }
        binding.appGrid.layoutManager = GridLayoutManager(this, columns)
        binding.appGrid.adapter = adapter
        binding.searchBox.doAfterTextChanged { render() }

        if (pickMode) {
            binding.settingsButton.isVisible = false
        } else {
            binding.settingsButton.setOnClickListener { toggleSettingsMode() }
        }

        loadAndRender(seedFocus = true)
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        if (viewModel.reloadIfStale()) loadAndRender(seedFocus = false) else render()
    }

    private fun loadAndRender(seedFocus: Boolean) {
        lifecycleScope.launch {
            allApps = viewModel.apps.await()
            render()
            if (seedFocus) {
                binding.appGrid.post {
                    binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
                }
            }
        }
    }

    private fun toggleSettingsMode() {
        viewModel.isSettingsMode = !viewModel.isSettingsMode
        render()
    }

    private fun render() {
        val settingsMode = viewModel.isSettingsMode
        if (!pickMode) {
            binding.settingsButton.backgroundTintList =
                if (settingsMode) activeSettingsTint else defaultSettingsTint
        }
        binding.searchBox.setHint(if (settingsMode) R.string.search_settings_hint else R.string.search_hint)
        val query = binding.searchBox.text.toString().trim()
        adapter.submit(
            if (settingsMode) settings.build().filter { it.label.contains(query, ignoreCase = true) }
            else appTiles(AppRepository.filterApps(allApps, query))
        )
    }

    // The adapter may hold a search-filtered list or the app grid, so the tile is found by
    // its label, and only while settings mode shows.
    private fun patchSettingsTile(label: String, subtitle: String) {
        if (viewModel.isSettingsMode) {
            adapter.updateItem({ it.label == label }) { it.copy(subtitle = subtitle) }
        }
    }

    private fun appTiles(apps: List<AppEntry>): List<TileItem> {
        val hidden = hiddenAppsStore.hiddenPackages()
        val showHidden = hiddenAppsStore.showHidden
        val pickTiles = when {
            pickSlot != NO_SLOT -> listOf(noneTile, addLinkTile)
            navAppPickMode -> listOf(noneTile)
            else -> emptyList()
        }
        return pickTiles + apps
            .filter { showHidden || it.component.packageName !in hidden }
            .map { appTile(it, isHidden = it.component.packageName in hidden) }
    }

    private fun appTile(entry: AppEntry, isHidden: Boolean) = TileItem(
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
                hideAction = hideAction(entry.component.packageName, isHidden),
            )
            true
        }),
    )

    private fun hideAction(packageName: String, isHidden: Boolean): Pair<Int, () -> Unit> =
        if (isHidden) {
            R.string.tile_action_unhide to { hiddenAppsStore.unhide(packageName); render() }
        } else {
            R.string.tile_action_hide to { hiddenAppsStore.hide(packageName); render() }
        }

    private val noneTile by lazy {
        TileItem(
            label = getString(R.string.pick_none),
            icon = ContextCompat.getDrawable(this, R.drawable.ic_none)!!,
            onClick = {
                if (navAppPickMode) navAppStore.navApp = null
                else favoritesStore.clearSlot(pickSlot)
                finish()
            },
        )
    }

    private val addLinkTile by lazy {
        TileItem(
            label = getString(R.string.add_link),
            icon = ContextCompat.getDrawable(this, R.drawable.ic_link)!!,
            onClick = {
                val existing = favoritesStore.getSlotEntry(pickSlot) as? SlotEntry.Link
                showLinkDialog(existing?.label ?: "", existing?.url ?: "") { label, url ->
                    favoritesStore.setLink(pickSlot, label, url)
                    finish()
                }
            },
        )
    }

    private fun onAppSelected(component: ComponentName) {
        when {
            navAppPickMode -> { navAppStore.navApp = component; finish() }
            pickSlot != NO_SLOT -> { favoritesStore.setSlot(pickSlot, component); finish() }
            else -> repository.launch(component)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode(navBarStore.showNavBar)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    override fun finish() {
        super.finish()
        noTransition()
    }

    companion object {
        private const val EXTRA_PICK_SLOT = "pick_slot"
        private const val EXTRA_PICK_NAV_APP = "pick_nav_app"
        private const val NO_SLOT = -1

        fun pickIntent(context: Context, slot: Int): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_SLOT, slot)

        fun navAppPickerIntent(context: Context): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_NAV_APP, true)
    }
}

/** Holds the screen's mode and the process-wide app list load across recreation. */
class AppListViewModel(app: Application) : AndroidViewModel(app) {

    var apps: Deferred<List<AppEntry>> = getApplication<MotoLauncherApp>().getApps()
        private set

    var isSettingsMode: Boolean = false

    /** Swaps in the application's current load; true when it differs from the held one. */
    fun reloadIfStale(): Boolean {
        val fresh = getApplication<MotoLauncherApp>().getApps()
        if (fresh === apps) return false
        apps = fresh
        return true
    }
}
