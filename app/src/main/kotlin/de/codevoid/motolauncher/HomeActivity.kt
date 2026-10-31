package de.codevoid.motolauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.getComponentKey
import de.codevoid.motolauncher.data.putComponentKey
import de.codevoid.motolauncher.data.NavBarStore
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.data.ParkStore
import de.codevoid.motolauncher.data.SlotEntry
import de.codevoid.motolauncher.data.TileSize
import de.codevoid.motolauncher.data.TileSizeStore
import de.codevoid.motolauncher.databinding.ActivityHomeBinding
import de.codevoid.motolauncher.databinding.ItemAppTileBinding
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.blockKeyLongPress
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.isPortrait
import de.codevoid.motolauncher.ui.launchNavApp
import de.codevoid.motolauncher.ui.LinkDialog
import de.codevoid.motolauncher.ui.MenuDialog
import de.codevoid.motolauncher.ui.menuRows
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val orientationStore by lazy { OrientationStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val parkStore by lazy { ParkStore(this) }
    private val tileSizeStore by lazy { TileSizeStore(this) }
    private val tiles = ArrayList<ItemAppTileBinding>()
    // The size the tiles were inflated for; null until the first resume inflates them.
    private var gridTileSize: TileSize? = null

    private val favoritesCache: FavoritesCache by viewModels()

    // A short ESC stays inert: Home is the launcher root, there is nowhere to go back to.
    // Holding it launches the configured navigation app — still only a launch, so the
    // remote gains no route into configuration.
    private val escapeKeys = EscapeKeys(onLongPress = { launchNavApp() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)
        window.fitContentToSystemBars()

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        onBackPressedDispatcher.addCallback(this) { /* home is the root; swallow back */ }
        supportFragmentManager.setFragmentResultListener(APP_MENU, this) { _, result -> onAppMenu(result) }
        supportFragmentManager.setFragmentResultListener(LINK_MENU, this) { _, result -> onLinkMenu(result) }
        supportFragmentManager.setFragmentResultListener(LINK_EDIT, this) { _, result ->
            favorites.setLink(
                result.getInt(EXTRA_SLOT),
                requireNotNull(result.getString(LinkDialog.LABEL)),
                requireNotNull(result.getString(LinkDialog.URL)),
            )
            showGrid()
        }

        val app = application as MotoLauncherApp
        // Warms the app-list cache, so All Apps opens with the list decoded.
        app.getApps()
        // onResume covers changes made while Home was hidden; this covers the ones after.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.packageGeneration.drop(1).collect { showGrid() }
            }
        }
    }

    // Weighted rows divide space during the layout pass, avoiding the RecyclerView
    // first-frame race that briefly showed the third row cut off on cold start.
    private fun populateGrid(tileSize: TileSize) {
        binding.homeGrid.removeAllViews()
        tiles.clear()
        val inflater = LayoutInflater.from(this)
        repeat(tileSize.homeRows(isPortrait)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
                )
            }
            repeat(tileSize.homeColumns(isPortrait)) {
                val tile = ItemAppTileBinding.inflate(inflater, row, false)
                val existing = tile.root.layoutParams as ViewGroup.MarginLayoutParams
                tile.root.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f,
                ).apply {
                    setMargins(
                        existing.leftMargin,
                        existing.topMargin,
                        existing.rightMargin,
                        existing.bottomMargin,
                    )
                }
                tile.root.blockKeyLongPress()
                row.addView(tile.root)
                tiles.add(tile)
            }
            binding.homeGrid.addView(row)
        }
        gridTileSize = tileSize
        // Seeded only on new tiles. Rebinding keeps the views, and Android keeps the
        // focused view across app switches, so a resume needs no re-seeding.
        tiles[0].root.post { tiles[0].root.requestFocus() }
    }

    override fun onResume() {
        super.onResume()
        // The park screen is restored from here, which covers both cases lock task mode
        // cannot: a reboot (pinning does not survive one) and a Home press on a device
        // where pinning was refused, since Home starts the launcher and lands here.
        if (parkStore.isParked) {
            startActivity(ParkActivity.lockIntent(this))
            noTransition()
            return
        }
        setRequestedOrientation(orientationStore.orientation)
        showGrid()
    }

    // Inflates the tiles when the tile size has changed since they were built, then binds them.
    private fun showGrid() {
        val tileSize = tileSizeStore.tileSize
        if (tileSize != gridTileSize) populateGrid(tileSize)
        buildGrid()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode(navBarStore.showNavBar)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    private fun buildGrid() {
        val slots = favorites.allSlots().take(tiles.size - 1)
        val apps = favoritesCache.resolve(
            keys = slots.filterIsInstance<SlotEntry.App>().map { it.key },
            generation = (application as MotoLauncherApp).packageGeneration.value,
            load = repository::loadByKeys,
        )

        slots.forEachIndexed { index, slotEntry ->
            when (slotEntry) {
                is SlotEntry.App -> {
                    val entry = apps[slotEntry.key]
                    if (entry != null) {
                        bindTile(
                            tile = tiles[index],
                            label = entry.label,
                            icon = entry.icon,
                            onClick = { repository.launch(entry.key) },
                            onLongClick = {
                                MenuDialog.show(
                                    supportFragmentManager,
                                    APP_MENU,
                                    title = entry.label,
                                    rows = menuRows(
                                        R.string.tile_action_app_info,
                                        R.string.tile_action_uninstall,
                                        R.string.tile_action_remove,
                                    ),
                                    app = entry.key,
                                    extras = Bundle().apply {
                                        putInt(EXTRA_SLOT, index)
                                        putComponentKey(EXTRA_APP, entry.key)
                                    },
                                )
                                true
                            },
                        )
                    } else {
                        bindEmptySlot(index)
                    }
                }
                is SlotEntry.Link -> {
                    bindTile(
                        tile = tiles[index],
                        label = slotEntry.label,
                        iconRes = R.drawable.ic_link,
                        onClick = {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(slotEntry.url)))
                            }
                        },
                        onLongClick = {
                            MenuDialog.show(
                                supportFragmentManager,
                                LINK_MENU,
                                title = slotEntry.label,
                                rows = menuRows(R.string.link_action_edit, R.string.link_action_remove),
                                iconRes = R.drawable.ic_link,
                                extras = Bundle().apply { putInt(EXTRA_SLOT, index) },
                            )
                            true
                        },
                    )
                }
                null -> bindEmptySlot(index)
            }
        }

        bindTile(
            tile = tiles.last(),
            label = getString(R.string.all_apps),
            iconRes = R.drawable.ic_all_apps,
            onClick = {
                startActivity(Intent(this, AppListActivity::class.java))
                noTransition()
            },
        )
    }

    private fun bindEmptySlot(index: Int) = bindTile(
        tile = tiles[index],
        label = getString(R.string.empty_slot),
        iconRes = R.drawable.ic_add,
        onClick = { pickForSlot(index) },
        onLongClick = { pickForSlot(index); true },
        touchOnly = true,
    )

    // Tiles are reused across rebinds, so a tile without a long-press must clear the
    // previous listener and drop isLongClickable, or the framework keeps arming the
    // long-press timer for it. A touchOnly tile cannot take focus, so the remote skips it.
    private fun bindTile(
        tile: ItemAppTileBinding,
        label: String,
        icon: Drawable? = null,
        iconRes: Int = 0,
        onClick: () -> Unit,
        onLongClick: (() -> Boolean)? = null,
        touchOnly: Boolean = false,
    ) {
        tile.appLabel.text = label
        when {
            icon != null -> tile.appIcon.setImageDrawable(icon)
            iconRes != 0 -> tile.appIcon.setImageResource(iconRes)
            else -> tile.appIcon.setImageDrawable(null)
        }
        tile.root.isFocusable = !touchOnly
        tile.root.setOnClickListener { onClick() }
        if (onLongClick != null) {
            tile.root.setOnLongClickListener { onLongClick() }
        } else {
            tile.root.setOnLongClickListener(null)
            tile.root.isLongClickable = false
        }
    }

    // The picker writes the slot itself; onResume rebuilds the grid on return.
    private fun pickForSlot(slot: Int) {
        startActivity(AppListActivity.pickIntent(this, slot))
        noTransition()
    }

    private fun onAppMenu(result: Bundle) {
        val app = requireNotNull(result.getComponentKey(EXTRA_APP))
        when (result.getInt(MenuDialog.ROW)) {
            R.string.tile_action_app_info -> repository.openInfo(app)
            R.string.tile_action_uninstall -> repository.requestUninstall(app)
            R.string.tile_action_remove -> {
                favorites.clearSlot(result.getInt(EXTRA_SLOT))
                showGrid()
            }
        }
    }

    private fun onLinkMenu(result: Bundle) {
        val slot = result.getInt(EXTRA_SLOT)
        when (result.getInt(MenuDialog.ROW)) {
            R.string.link_action_edit -> {
                val link = favorites.getSlotEntry(slot) as? SlotEntry.Link ?: return
                LinkDialog.show(
                    supportFragmentManager,
                    LINK_EDIT,
                    link.label,
                    link.url,
                    editing = true,
                    extras = Bundle().apply { putInt(EXTRA_SLOT, slot) },
                )
            }
            R.string.link_action_remove -> {
                favorites.clearSlot(slot)
                showGrid()
            }
        }
    }

    private companion object {
        const val APP_MENU = "home.appMenu"
        const val LINK_MENU = "home.linkMenu"
        const val LINK_EDIT = "home.linkEdit"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_APP = "app"
    }
}

/** Home's resolved favourites, kept across recreates and emptied when the package generation moves. */
class FavoritesCache : ViewModel() {
    private var generation = -1
    // Null for a key that did not resolve, so it is not asked again until the next generation.
    private var apps: Map<ComponentKey, AppEntry?> = emptyMap()

    /** Every key of [keys] mapped to its app, or to null; only keys not seen in [generation] go to [load]. */
    fun resolve(
        keys: List<ComponentKey>,
        generation: Int,
        load: (List<ComponentKey>) -> Map<ComponentKey, AppEntry>,
    ): Map<ComponentKey, AppEntry?> {
        if (generation != this.generation) {
            apps = emptyMap()
            this.generation = generation
        }
        val unseen = keys.filterNot { it in apps }
        if (unseen.isNotEmpty()) {
            val loaded = load(unseen)
            apps = apps + unseen.associateWith { loaded[it] }
        }
        return apps
    }
}
