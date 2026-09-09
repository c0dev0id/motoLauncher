package de.codevoid.motolauncher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private lateinit var launcherApps: LauncherApps
    private val adapter = AppTileAdapter(emptyList())
    private var allApps: List<AppEntry> = emptyList()
    private val pickMode by lazy { intent.getBooleanExtra(EXTRA_PICK_MODE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = AppRepository(this)
        launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        binding.appGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.appGrid.adapter = adapter

        allApps = repository.loadApps()
        render(allApps)

        binding.searchBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                render(AppRepository.filterApps(allApps, s?.toString().orEmpty()))
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.appGrid.post {
            binding.appGrid.layoutManager?.findViewByPosition(0)?.requestFocus()
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
                            openAppInfo(entry.component)
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
            launcherApps.startMainActivity(component, Process.myUserHandle(), null, null)
        }
    }

    private fun openAppInfo(component: ComponentName) {
        launcherApps.startAppDetailsActivity(component, Process.myUserHandle(), null, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Escape from the app list returns to the home screen.
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
