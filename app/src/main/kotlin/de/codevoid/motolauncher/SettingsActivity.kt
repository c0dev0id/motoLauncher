package de.codevoid.motolauncher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.data.ThemeStore
import de.codevoid.motolauncher.databinding.ActivitySettingsBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private lateinit var updateChecker: UpdateChecker
    private lateinit var themeStore: ThemeStore
    private val adapter = AppTileAdapter(emptyList())
    private var pickingSlot = -1

    private val cellularPermissionLauncher =
        registerForActivityResult(RequestPermission()) { updateCellularPermissionButton() }

    private val pickLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val flat = result.data?.getStringExtra(AppListActivity.RESULT_COMPONENT)
        val component = flat?.let { ComponentName.unflattenFromString(it) }
        if (result.resultCode == RESULT_OK && pickingSlot >= 0 && component != null) {
            favorites.setSlot(pickingSlot, component)
        }
        pickingSlot = -1
        renderSlots()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()

        binding.backButton.setOnClickListener { finish() }

        favorites = FavoritesStore(this)
        repository = AppRepository(this)
        updateChecker = UpdateChecker(this)
        themeStore = ThemeStore(this)

        binding.slotGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.slotGrid.adapter = adapter

        binding.checkUpdateButton.setOnClickListener { checkForUpdates() }

        // Label shows the active theme; tapping flips it, which recreates the activity so
        // the label refreshes on the way back in.
        binding.themeButton.setText(if (themeStore.isDark) R.string.theme_dark else R.string.theme_light)
        binding.themeButton.setOnClickListener { themeStore.isDark = !themeStore.isDark }

        binding.enableCellularButton.setOnClickListener {
            cellularPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }

        renderSlots()
    }

    override fun onResume() {
        super.onResume()
        updateCellularPermissionButton()
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
        pickingSlot = index
        pickLauncher.launch(
            Intent(this, AppListActivity::class.java)
                .putExtra(AppListActivity.EXTRA_PICK_MODE, true)
        )
    }

    private fun checkForUpdates() {
        binding.updateStatus.visibility = View.VISIBLE
        binding.updateStatus.text = getString(R.string.checking_updates)
        binding.checkUpdateButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val release = updateChecker.check()
                if (release == null) {
                    binding.updateStatus.text = getString(R.string.update_none)
                } else {
                    promptInstall(release)
                }
            } catch (e: Exception) {
                showUpdateError(e)
            } finally {
                binding.checkUpdateButton.isEnabled = true
            }
        }
    }

    private fun promptInstall(release: ReleaseInfo) {
        binding.updateStatus.text = getString(R.string.update_available, release.versionName)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, release.versionName))
            .setPositiveButton(R.string.update_download) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(release: ReleaseInfo) {
        binding.updateStatus.text = getString(R.string.downloading)
        lifecycleScope.launch {
            try {
                val file = updateChecker.download(release)
                startActivity(updateChecker.installIntent(file))
            } catch (e: Exception) {
                showUpdateError(e)
            }
        }
    }

    private fun showUpdateError(e: Exception) {
        binding.updateStatus.text =
            getString(R.string.update_failed, e.message ?: e.javaClass.simpleName)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val COLUMNS = 4
    }
}
