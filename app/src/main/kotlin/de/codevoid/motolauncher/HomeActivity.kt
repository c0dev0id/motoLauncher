package de.codevoid.motolauncher

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivityHomeBinding
import de.codevoid.motolauncher.databinding.ItemAppTileBinding
import de.codevoid.motolauncher.ui.blockKeyLongPress
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.showTileActionsDialog

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private val tiles = ArrayList<ItemAppTileBinding>(COLUMNS * ROWS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode()

        favorites = FavoritesStore(this)
        repository = AppRepository(this)

        populateGrid()

        onBackPressedDispatcher.addCallback(this) { /* home is the root; swallow back */ }
    }

    // Weighted rows divide space during the layout pass, avoiding the RecyclerView
    // first-frame race that briefly showed the third row cut off on cold start.
    private fun populateGrid() {
        val inflater = LayoutInflater.from(this)
        repeat(ROWS) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f,
                )
            }
            repeat(COLUMNS) {
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
        buildGrid()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode()
    }

    private fun buildGrid() {
        val slots = favorites.allSlots()
        val apps = repository.loadByComponents(slots.filterNotNull())

        slots.forEachIndexed { index, component ->
            val entry = component?.let { apps[it] }
            if (entry != null) {
                bindTile(
                    tile = tiles[index],
                    label = entry.label,
                    icon = entry.icon,
                    iconRes = 0,
                    onClick = { repository.launch(entry.component) },
                    onLongClick = {
                        showTileActionsDialog(
                            context = this,
                            entry = entry,
                            onReassign = { pickForSlot(index) },
                            onAppInfo = { repository.openInfo(entry.component) },
                        )
                        true
                    },
                )
            } else {
                bindTile(
                    tile = tiles[index],
                    label = getString(R.string.empty_slot),
                    icon = null,
                    iconRes = R.drawable.ic_add,
                    onClick = { pickForSlot(index) },
                    onLongClick = { pickForSlot(index); true },
                )
            }
        }

        bindTile(
            tile = tiles[COLUMNS * ROWS - 1],
            label = getString(R.string.all_apps),
            icon = null,
            iconRes = R.drawable.ic_all_apps,
            onClick = { startActivity(Intent(this, AppListActivity::class.java)) },
            onLongClick = { openSettings(); true },
        )

        tiles[0].root.post { tiles[0].root.requestFocus() }
    }

    private fun bindTile(
        tile: ItemAppTileBinding,
        label: String,
        icon: Drawable?,
        iconRes: Int,
        onClick: () -> Unit,
        onLongClick: () -> Boolean,
    ) {
        tile.appLabel.text = label
        when {
            icon != null -> tile.appIcon.setImageDrawable(icon)
            iconRes != 0 -> tile.appIcon.setImageResource(iconRes)
            else -> tile.appIcon.setImageDrawable(null)
        }
        tile.root.setOnClickListener { onClick() }
        tile.root.setOnLongClickListener { onLongClick() }
    }

    // The picker writes the slot itself; onResume rebuilds the grid on return.
    private fun pickForSlot(slot: Int) {
        startActivity(AppListActivity.pickIntent(this, slot))
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    companion object {
        private const val COLUMNS = 4
        private const val ROWS = 3
    }
}
