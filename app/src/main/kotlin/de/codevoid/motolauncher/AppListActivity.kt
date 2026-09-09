package de.codevoid.motolauncher

import android.Manifest
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.databinding.ActivityAppListBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.finishOnEscape
import de.codevoid.motolauncher.ui.showImmersive
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppListBinding
    private lateinit var repository: AppRepository
    private lateinit var themeStore: ThemeStore
    private lateinit var updateChecker: UpdateChecker
    private val adapter = AppTileAdapter(emptyList())
    private var allApps: List<AppEntry> = emptyList()

    // >= 0: pick mode — the chosen app is written to that favourite slot and the
    // activity finishes. NO_SLOT: browse mode — tap launches, long-press opens app info.
    private val pickSlot by lazy { intent.getIntExtra(EXTRA_PICK_SLOT, NO_SLOT) }
    private val pickMode get() = pickSlot != NO_SLOT

    private val cellularPermissionLauncher =
        registerForActivityResult(RequestPermission()) { updateCellularPermissionButton() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode()

        repository = AppRepository(this)
        themeStore = ThemeStore(this)
        updateChecker = UpdateChecker(this)

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
        binding.themeButton.setText(if (themeStore.isDark) R.string.theme_dark else R.string.theme_light)
        binding.themeButton.setOnClickListener { themeStore.isDark = !themeStore.isDark }
        binding.checkUpdateButton.setOnClickListener { checkForUpdates() }
        binding.enableCellularButton.setOnClickListener {
            cellularPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        // The picker is a single-purpose screen: no configuration controls while choosing.
        binding.headerConfig.visibility = if (pickMode) View.GONE else View.VISIBLE

        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { repository.loadApps() }
            render(allApps)
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

    // Progress shows on the button itself; outcomes are dialogs, so the header row
    // never needs a status line.
    private fun checkForUpdates() {
        setUpdateBusy(R.string.checking_updates)
        lifecycleScope.launch {
            try {
                val release = updateChecker.check()
                if (release == null) showMessage(getString(R.string.update_none)) else promptInstall(release)
            } catch (e: Exception) {
                showUpdateError(e)
            } finally {
                setUpdateIdle()
            }
        }
    }

    private fun promptInstall(release: ReleaseInfo) {
        AlertDialog.Builder(this, R.style.Theme_MotoLauncher_Dialog)
            .setTitle(getString(R.string.update_available, release.versionName))
            .setPositiveButton(R.string.update_download) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .showImmersive()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        setUpdateBusy(R.string.downloading)
        lifecycleScope.launch {
            try {
                val file = updateChecker.download(release)
                startActivity(updateChecker.installIntent(file))
            } catch (e: Exception) {
                showUpdateError(e)
            } finally {
                setUpdateIdle()
            }
        }
    }

    private fun setUpdateBusy(labelRes: Int) {
        binding.checkUpdateButton.isEnabled = false
        binding.checkUpdateButton.setText(labelRes)
    }

    private fun setUpdateIdle() {
        binding.checkUpdateButton.isEnabled = true
        binding.checkUpdateButton.setText(R.string.check_for_updates)
    }

    private fun showUpdateError(e: Exception) {
        showMessage(getString(R.string.update_failed, e.message ?: e.javaClass.simpleName))
    }

    private fun showMessage(text: String) {
        AlertDialog.Builder(this, R.style.Theme_MotoLauncher_Dialog)
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .create()
            .showImmersive()
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
