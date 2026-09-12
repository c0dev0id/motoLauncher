package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.NavBarStore
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.databinding.ActivityHomeBinding
import de.codevoid.motolauncher.databinding.ItemAppTileBinding
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.blockKeyLongPress
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.noTransition
import de.codevoid.motolauncher.ui.isPortrait
import de.codevoid.motolauncher.ui.launchNavApp
import de.codevoid.motolauncher.ui.showTileActionsDialog

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val orientationStore by lazy { OrientationStore(this) }
    private val navBarStore by lazy { NavBarStore(this) }
    private val columns get() = if (isPortrait) 3 else 4
    private val rows get() = if (isPortrait) 4 else 3
    private val tiles = ArrayList<ItemAppTileBinding>(12)

    // Resolved on first resume and reused until either packageGeneration advances (app
    // installed/removed) or the slot list itself changes (reassign/clear). Avoids Binder
    // IPC and icon decoding on every return from the navigation app.
    private var cachedApps: Map<ComponentName, AppEntry> = emptyMap()
    private var cacheGeneration: Int = -1
    private var cacheSlots: List<ComponentName?> = emptyList()

    // A short ESC stays inert: Home is the launcher root, there is nowhere to go back to.
    // Holding it launches the first favourite — still only a launch, so the remote gains
    // no route into configuration.
    private val escapeKeys = EscapeKeys(onLongPress = { launchNavApp() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(navBarStore.showNavBar)

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        populateGrid()
        // Seed the remote's focus once. Tiles are reused across rebinds and Android keeps
        // the focused view across app switches, so nothing needs re-seeding on resume.
        tiles[0].root.post { tiles[0].root.requestFocus() }

        onBackPressedDispatcher.addCallback(this) { /* home is the root; swallow back */ }

        // Start loading the app list in the background so it is ready before the user taps
        // "All Apps". The result is cached at the Application level; this just warms it.
        (application as MotoLauncherApp).getApps()
    }

    // Weighted rows divide space during the layout pass, avoiding the RecyclerView
    // first-frame race that briefly showed the third row cut off on cold start.
    private fun populateGrid() {
        val inflater = LayoutInflater.from(this)
        repeat(rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
                )
            }
            repeat(columns) {
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
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        buildGrid()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode(navBarStore.showNavBar)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyLongPress(keyCode, event) || super.onKeyLongPress(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        escapeKeys.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event)

    private fun buildGrid() {
        val slots = favorites.allSlots()
        val generation = (application as MotoLauncherApp).packageGeneration
        if (generation != cacheGeneration || slots != cacheSlots) {
            cachedApps = repository.loadByComponents(slots.filterNotNull())
            cacheGeneration = generation
            cacheSlots = slots
        }
        val apps = cachedApps

        slots.forEachIndexed { index, component ->
            val entry = component?.let { apps[it] }
            if (entry != null) {
                bindTile(
                    tile = tiles[index],
                    label = entry.label,
                    icon = entry.icon,
                    onClick = { repository.launch(entry.component) },
                    onLongClick = {
                        showTileActionsDialog(
                            context = this,
                            entry = entry,
                            onAppInfo = { repository.openInfo(entry.component) },
                            onUninstall = { repository.requestUninstall(entry.component) },
                            onReassign = { pickForSlot(index) },
                        )
                        true
                    },
                )
            } else {
                bindTile(
                    tile = tiles[index],
                    label = getString(R.string.empty_slot),
                    iconRes = R.drawable.ic_add,
                    onClick = { pickForSlot(index) },
                    onLongClick = { pickForSlot(index); true },
                )
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

    // Tiles are reused across rebinds, so a tile without a long-press must clear the
    // previous listener and drop isLongClickable, or the framework keeps arming the
    // long-press timer for it.
    private fun bindTile(
        tile: ItemAppTileBinding,
        label: String,
        icon: Drawable? = null,
        iconRes: Int = 0,
        onClick: () -> Unit,
        onLongClick: (() -> Boolean)? = null,
    ) {
        tile.appLabel.text = label
        when {
            icon != null -> tile.appIcon.setImageDrawable(icon)
            iconRes != 0 -> tile.appIcon.setImageResource(iconRes)
            else -> tile.appIcon.setImageDrawable(null)
        }
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

}
