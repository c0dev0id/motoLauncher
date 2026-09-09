package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var launcherApps: LauncherApps
    private val adapter = AppTileAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favorites = FavoritesStore(this)
        repository = AppRepository(this)
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        binding.homeGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.homeGrid.adapter = adapter

        binding.homeGrid.post {
            val usable = binding.homeGrid.height -
                binding.homeGrid.paddingTop - binding.homeGrid.paddingBottom
            if (usable > 0) adapter.itemHeightPx = usable / ROWS
        }

        // Home is the root screen: Back/Escape must not exit to a blank screen.
        onBackPressedDispatcher.addCallback(this) { /* stay on home */ }
    }

    override fun onResume() {
        super.onResume()
        buildGrid()
    }

    private fun buildGrid() {
        val apps = repository.loadApps().associateBy { it.component }
        val tiles = ArrayList<TileItem>(COLUMNS * ROWS)

        favorites.allSlots().forEachIndexed { index, component ->
            val entry = component?.let { apps[it] }
            if (entry != null) {
                tiles.add(
                    TileItem(
                        label = entry.label,
                        icon = entry.icon,
                        onClick = { launchApp(entry.component) },
                        onLongClick = { openAppInfo(entry.component); true },
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

    private fun launchApp(component: ComponentName) {
        launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
    }

    private fun openAppInfo(component: ComponentName) {
        launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Escape on the home screen is a no-op (there is nowhere "back" to go).
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) return true
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val COLUMNS = 4
        private const val ROWS = 3
    }
}
