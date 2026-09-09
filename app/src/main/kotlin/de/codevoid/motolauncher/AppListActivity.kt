package de.codevoid.motolauncher

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.EscapeKeys
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
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
    private var allApps: List<AppEntry> = emptyList()

    // >= 0: pick mode — the chosen app is written to that favourite slot and the
    // activity finishes. NO_SLOT: browse mode — tap launches, long-press opens app info.
    private val pickSlot by lazy { intent.getIntExtra(EXTRA_PICK_SLOT, NO_SLOT) }
    private val pickMode get() = pickSlot != NO_SLOT

    // ESC closes the screen; holding it launches the first favourite, the same quick
    // launch Home offers, so the gesture means one thing wherever the remote is.
    private val escapeKeys = EscapeKeys(
        onLongPress = { launchFirstFavorite() },
        onShortPress = { finish() },
    )

    private val cellularPermissionLauncher =
        registerForActivityResult(RequestPermission()) { updateCellularPermissionButton() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode()

        repository = AppRepository(this)

        binding.backButton.setOnClickListener { finish() }

        binding.appGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.appGrid.adapter = adapter

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render(AppRepository.filterApps(allApps, s?.toString().orEmpty()))
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // Label shows the active theme; tapping flips it, which recreates the activity so
        // the label refreshes on the way back in.
        val themeStore = ThemeStore(this)
        binding.themeButton.setText(if (themeStore.isDark) R.string.theme_dark else R.string.theme_light)
        binding.themeButton.setOnClickListener { themeStore.isDark = !themeStore.isDark }
        binding.checkUpdateButton.setOnClickListener { runUpdateFlow(binding.checkUpdateButton) }
        binding.enableCellularButton.setOnClickListener {
            cellularPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        // The picker is a single-purpose screen: no configuration controls while choosing.
        binding.headerConfig.visibility = if (pickMode) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            allApps = viewModel.apps.await()
            // After a recreate the restored search text is already in the box; honour it.
            render(AppRepository.filterApps(allApps, binding.searchBox.text.toString()))
            binding.appGrid.post {
                binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!pickMode) updateCellularPermissionButton()
    }

    // The cellular indicator is optional: expose the ask only when the platform can
    // deliver signal readings (API 31+) and the permission is still missing. Once
    // granted, the button silently disappears — no toast, no dialog.
    private fun updateCellularPermissionButton() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        binding.enableCellularButton.visibility = if (needsAsk) View.VISIBLE else View.GONE
    }

    private fun render(apps: List<AppEntry>) {
        val tiles = ArrayList<TileItem>(apps.size + 1)
        // In pick mode a "None" tile leads the grid, whatever the search says: choosing it
        // clears the slot. It is the only way to empty a favourite.
        if (pickMode) tiles.add(noneTile)
        apps.mapTo(tiles) { entry ->
            TileItem(
                label = entry.label,
                icon = entry.icon,
                onClick = { onAppSelected(entry.component) },
                onLongClick = {
                    if (pickMode) {
                        false
                    } else {
                        showTileActionsDialog(
                            context = this,
                            entry = entry,
                            onAppInfo = { repository.openInfo(entry.component) },
                            onUninstall = { repository.requestUninstall(entry.component) },
                        )
                        true
                    }
                },
            )
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
        private const val COLUMNS = 5

        // Callers rebuild their grid in onResume, so no result contract is needed.
        fun pickIntent(context: Context, slot: Int): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_SLOT, slot)
    }
}

// Enumerating and rasterising every installed app is the most expensive thing the app
// does. Keeping the result in a ViewModel means the theme toggle's recreate() reuses it
// instead of running it again.
class AppListViewModel(app: Application) : AndroidViewModel(app) {
    val apps: Deferred<List<AppEntry>> =
        viewModelScope.async(Dispatchers.IO) { AppRepository(app).loadApps() }
}
