package de.codevoid.motolauncher

import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivitySettingsBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.finishOnEscape

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val adapter = AppTileAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode()

        binding.backButton.setOnClickListener { finish() }

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        binding.slotGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.slotGrid.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        renderSlots()
    }

    private fun renderSlots() {
        val slots = favorites.allSlots()
        val apps = repository.loadByComponents(slots.filterNotNull())
        val tiles = slots.mapIndexed { index, component ->
            val entry = component?.let { apps[it] }
            if (entry != null) {
                TileItem(
                    label = entry.label,
                    icon = entry.icon,
                    onClick = { pickForSlot(index) },
                    onLongClick = { favorites.clearSlot(index); renderSlots(); true },
                )
            } else {
                TileItem(
                    label = getString(R.string.add_favorite),
                    iconRes = R.drawable.ic_add,
                    onClick = { pickForSlot(index) },
                )
            }
        }
        adapter.submit(tiles)
    }

    private fun pickForSlot(index: Int) {
        startActivity(AppListActivity.pickIntent(this, index))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        finishOnEscape(keyCode) || super.onKeyDown(keyCode, event)

    companion object {
        private const val COLUMNS = 4
    }
}
