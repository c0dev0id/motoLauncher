package de.codevoid.motolauncher

import android.app.Activity
import android.content.ComponentName
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
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private val adapter = AppTileAdapter(emptyList())
    private var allApps: List<AppEntry> = emptyList()
    private val pickMode by lazy { intent.getBooleanExtra(EXTRA_PICK_MODE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository(this)

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
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(RESULT_COMPONENT, component.flattenToString()),
            )
            finish()
        } else {
            repository.launch(component)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_PICK_MODE = "pick_mode"
        const val RESULT_COMPONENT = "component"
        private const val COLUMNS = 5
    }
}
