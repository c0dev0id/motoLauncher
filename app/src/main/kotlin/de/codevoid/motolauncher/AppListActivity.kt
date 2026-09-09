package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.finishOnEscape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private val adapter = AppTileAdapter(emptyList())
    private var allApps: List<AppEntry> = emptyList()
    // >= 0: pick mode — the chosen app is written to that favourite slot and the
    // activity finishes. NO_SLOT: browse mode — tap launches, long-press opens app info.
    private val pickSlot by lazy { intent.getIntExtra(EXTRA_PICK_SLOT, NO_SLOT) }
    private val pickMode get() = pickSlot != NO_SLOT

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

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { repository.loadApps() }
            render(allApps)
            binding.appGrid.post {
                binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
            }
        }
    }

    private fun render(apps: List<AppEntry>) {
        adapter.submit(
            apps.map { entry ->
                TileItem(
                    label = entry.label,
                    icon = entry.icon,
                    onClick = { onAppSelected(entry.component) },
                    onLongClick = {
                        if (!pickMode) {
                            repository.openInfo(entry.component)
                            true
                        } else {
                            false
                        }
                    },
                )
            }
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
        finishOnEscape(keyCode) || super.onKeyDown(keyCode, event)

    companion object {
        private const val EXTRA_PICK_SLOT = "pick_slot"
        private const val NO_SLOT = -1
        private const val COLUMNS = 5

        // Callers rebuild their grid in onResume, so no result contract is needed.
        fun pickIntent(context: Context, slot: Int): Intent =
            Intent(context, AppListActivity::class.java).putExtra(EXTRA_PICK_SLOT, slot)
    }
}
