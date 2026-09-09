package de.codevoid.motolauncher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivityHomeBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val adapter = AppTileAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        // The home grid is a fixed layout that must stay put; disabling scrolling stops the
        // overscroll stretch on swipes. Dpad focus traversal is unaffected.
        binding.homeGrid.layoutManager = object : GridLayoutManager(this, COLUMNS) {
            override fun canScrollVertically() = false
            override fun canScrollHorizontally() = false
        }
        binding.homeGrid.adapter = adapter

        // Size each row to the grid's measured height so all three rows pack in without
        // scrolling; the tiles' own margins are subtracted by the adapter. The listener
        // runs on every layout pass because the first pass happens with the adapter empty
        // (data arrives in onResume) — a one-shot callback would set the height too late,
        // after tiles have already been laid out with their default XML height. The
        // adapter's setter no-ops on an unchanged value, so recomputing every pass is cheap.
        binding.homeGrid.addOnLayoutChangeListener { grid, _, _, _, _, _, _, _, _ ->
            val usable = grid.height - grid.paddingTop - grid.paddingBottom
            if (usable > 0) adapter.itemHeightPx = usable / ROWS
        }

        onBackPressedDispatcher.addCallback(this) { /* home is the root; swallow back */ }
    }

    override fun onResume() {
        super.onResume()
        buildGrid()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun buildGrid() {
        val slots = favorites.allSlots()
        val apps = repository.loadByComponents(slots.filterNotNull())
        val tiles = ArrayList<TileItem>(COLUMNS * ROWS)

        slots.forEach { component ->
            val entry = component?.let { apps[it] }
            if (entry != null) {
                tiles.add(
                    TileItem(
                        label = entry.label,
                        icon = entry.icon,
                        onClick = { repository.launch(entry.component) },
                        onLongClick = { repository.openInfo(entry.component); true },
                    )
                )
            } else {
                tiles.add(
                    TileItem(
                        label = getString(R.string.empty_slot),
                        iconRes = R.drawable.ic_add,
                        onClick = { openSettings() },
                        onLongClick = { openSettings(); true },
                    )
                )
            }
        }

        tiles.add(
            TileItem(
                label = getString(R.string.all_apps),
                iconRes = R.drawable.ic_all_apps,
                onClick = { startActivity(Intent(this, AppListActivity::class.java)) },
                onLongClick = { openSettings(); true },
            )
        )

        adapter.submit(tiles)
        binding.homeGrid.post {
            binding.homeGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Home is the root, so Escape has nothing to go "back" to; use it as the remote's
        // route into configuration (there is no spare tile once every slot is filled).
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            openSettings()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val COLUMNS = 4
        private const val ROWS = 3
    }
}
