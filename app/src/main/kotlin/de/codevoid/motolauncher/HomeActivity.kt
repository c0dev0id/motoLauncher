package de.codevoid.motolauncher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivityHomeBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val adapter = AppTileAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        binding.homeGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.homeGrid.adapter = adapter

        // The launcher window sits behind the status/navigation bars; inset the grid so its
        // three rows are measured against the visible area instead of scrolling under them.
        val basePad = binding.homeGrid.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeGrid) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = basePad + bars.top, bottom = basePad + bars.bottom)
            v.post {
                val usable = v.height - v.paddingTop - v.paddingBottom
                if (usable > 0) adapter.itemHeightPx = usable / ROWS
            }
            insets
        }

        onBackPressedDispatcher.addCallback(this) { /* home is the root; swallow back */ }
    }

    override fun onResume() {
        super.onResume()
        buildGrid()
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
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val COLUMNS = 4
        private const val ROWS = 3
    }
}
