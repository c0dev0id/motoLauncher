package de.codevoid.motolauncher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.HiddenAppsStore
import de.codevoid.motolauncher.data.NavAppStore
import de.codevoid.motolauncher.data.NavBarStore
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.data.SlotEntry
import de.codevoid.motolauncher.data.TileSizeStore
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import de.codevoid.motolauncher.ui.isPortrait
import de.codevoid.motolauncher.ui.launchNavApp
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.showLinkDialog
import de.codevoid.motolauncher.ui.showTileActionsDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppListActivity : AppCompatActivity() {

    /** What the screen is for, read once from the intent. */
    sealed interface Mode {
        /** Tap launches, long press opens the tile menu, and settings mode is available. */
        data object Browse : Mode

        /** The chosen app or link goes into favourite [slot]. */
        data class PickSlot(val slot: Int) : Mode

        /** The chosen app becomes the navigation app. */
        data object PickNavApp : Mode
    }

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
    private val tileSizeStore by lazy { TileSizeStore(this) }
    private val mode by lazy { modeOf(intent) }
    private val columns get() = tileSizeStore.tileSize.listColumns(isPortrait)
    // Null until the first list arrives.
    private var allApps: List<AppEntry>? = null

    private val escapeKeys = EscapeKeys(
        onLongPress = { launchNavApp() },
        onShortPress = {
            if (viewModel.isSettingsMode) {
                viewModel.isSettingsMode = false
                renderCurrentMode()
            } else {
                finish()
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        repository = AppRepository(this)
        settings = SettingsTiles(this, viewModel, onChanged = ::renderCurrentMode)

        binding.backButton.setOnClickListener { finish() }
        binding.appGrid.layoutManager = GridLayoutManager(this, columns)
        binding.appGrid.adapter = adapter
        binding.searchBox.doAfterTextChanged { renderCurrentMode() }

        // A picker only picks: there is no way into settings from it.
        if (mode == Mode.Browse) {
            binding.settingsButton.setOnClickListener {
                viewModel.isSettingsMode = !viewModel.isSettingsMode
                renderCurrentMode()
            }
        } else {
            binding.settingsButton.visibility = View.GONE
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.apps.filterNotNull().collect(::showApps)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        renderCurrentMode()
    }

    // Focus is seeded on the first list this activity shows, never on a later one, so an
    // install or uninstall does not drag the remote back to the first tile.
    private fun showApps(apps: List<AppEntry>) {
        if (apps === allApps) return
        val first = allApps == null
        allApps = apps
        renderCurrentMode()
        if (first) {
            binding.appGrid.post {
                binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
            }
        }
    }

    // Shows the current mode's tiles, filtered by the search text.
    private fun renderCurrentMode() {
        val settingsMode = viewModel.isSettingsMode
        binding.settingsButton.isActivated = settingsMode
        binding.searchBox.setHint(if (settingsMode) R.string.search_settings_hint else R.string.search_hint)
        (binding.appGrid.layoutManager as GridLayoutManager).spanCount = columns
        val query = binding.searchBox.text.toString().trim()
        if (settingsMode) {
            adapter.submit(
                settings.tiles(allApps).filter { it.label.contains(query, ignoreCase = true) },
                touchOnly = true,
            )
        } else {
            val tiles = appTiles(
                apps = AppRepository.filterApps(allApps.orEmpty(), query),
                hidden = hiddenAppsStore.hiddenPackages(),
                showHidden = hiddenAppsStore.showHidden,
                onClick = ::onAppSelected,
                onLongClick = if (mode == Mode.Browse) ::showAppMenu else null,
            )
            adapter.submit(pickTiles + tiles, touchOnly = mode != Mode.Browse)
        }
    }

    // They lead the grid whatever the search says: None is the only way to empty a slot.
    private val pickTiles: List<TileItem> by lazy {
        when (val current = mode) {
            Mode.Browse -> emptyList()
            Mode.PickNavApp -> listOf(noneTile { navAppStore.navApp = null })
            is Mode.PickSlot -> listOf(noneTile { favoritesStore.clearSlot(current.slot) }, addLinkTile(current.slot))
        }
    }

    private fun noneTile(clear: () -> Unit) = TileItem(
        label = getString(R.string.pick_none),
        icon = ContextCompat.getDrawable(this, R.drawable.ic_none),
        onClick = { clear(); finish() },
    )

    private fun addLinkTile(slot: Int) = TileItem(
        label = getString(R.string.add_link),
        icon = ContextCompat.getDrawable(this, R.drawable.ic_link),
        onClick = {
            val existing = favoritesStore.getSlotEntry(slot) as? SlotEntry.Link
            showLinkDialog(existing?.label ?: "", existing?.url ?: "") { label, url ->
                favoritesStore.setLink(slot, label, url)
                finish()
            }
        },
    )

    private fun onAppSelected(app: AppEntry) {
        when (val current = mode) {
            Mode.Browse -> repository.launch(app.key)
            Mode.PickNavApp -> { navAppStore.navApp = app.key; finish() }
            is Mode.PickSlot -> { favoritesStore.setSlot(current.slot, app.key); finish() }
        }
    }

    private fun showAppMenu(app: AppEntry, isHidden: Boolean) {
        val packageName = app.key.component.packageName
        showTileActionsDialog(
            context = this,
            entry = app,
            onAppInfo = { repository.openInfo(app.key) },
            onUninstall = { repository.requestUninstall(app.key) },
            hideAction = if (isHidden) {
                R.string.tile_action_unhide to { hiddenAppsStore.unhide(packageName); renderCurrentMode() }
            } else {
                R.string.tile_action_hide to { hiddenAppsStore.hide(packageName); renderCurrentMode() }
            },
        )
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

        // Pickers write the choice themselves and callers rebuild in onResume, so there is
        // no result contract.
        fun pickIntent(context: Context, slot: Int): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_SLOT, slot)

        fun navAppPickerIntent(context: Context): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_NAV_APP, true)

        private fun modeOf(intent: Intent): Mode = when {
            intent.hasExtra(EXTRA_PICK_SLOT) -> Mode.PickSlot(intent.getIntExtra(EXTRA_PICK_SLOT, 0))
            intent.getBooleanExtra(EXTRA_PICK_NAV_APP, false) -> Mode.PickNavApp
            else -> Mode.Browse
        }
    }
}

/**
 * The app grid's tiles for [apps]: hidden packages are left out, or dimmed when
 * [showHidden]. [onLongClick], when given, gets the app and whether it is hidden.
 */
fun appTiles(
    apps: List<AppEntry>,
    hidden: Set<String>,
    showHidden: Boolean,
    onClick: (AppEntry) -> Unit,
    onLongClick: ((AppEntry, Boolean) -> Unit)?,
): List<TileItem> = apps.mapNotNull { app ->
    val isHidden = app.key.component.packageName in hidden
    if (isHidden && !showHidden) return@mapNotNull null
    TileItem(
        label = app.label,
        icon = app.icon,
        dimmed = isHidden,
        key = app.key,
        onClick = { onClick(app) },
        onLongClick = onLongClick?.let { longClick -> { longClick(app, isHidden); true } },
    )
}

// State that survives recreate() (theme toggle, rotation).
class AppListViewModel(app: Application) : AndroidViewModel(app) {

    /** The app list, reloaded on every package change while the screen is started; null until the first load. */
    val apps: StateFlow<List<AppEntry>?> = getApplication<MotoLauncherApp>().let { launcher ->
        launcher.packageGeneration
            .map { launcher.getApps().await() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    }

    var isSettingsMode: Boolean = false
    var isCheckingUpdate: Boolean = false
    var downloadProgressSubtitle: String? = null
}
