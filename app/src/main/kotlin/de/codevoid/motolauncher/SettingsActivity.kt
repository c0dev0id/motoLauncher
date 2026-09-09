package de.codevoid.motolauncher

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore
import de.codevoid.motolauncher.databinding.ActivitySettingsBinding
import de.codevoid.motolauncher.ui.AppTileAdapter
import de.codevoid.motolauncher.ui.TileItem
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var favorites: FavoritesStore
    private lateinit var repository: AppRepository
    private lateinit var updateChecker: UpdateChecker
    private val adapter = AppTileAdapter(emptyList())
    private var pickingSlot = -1

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

        favorites = FavoritesStore(this)
        repository = AppRepository(this)
        updateChecker = UpdateChecker(this)

        binding.slotGrid.layoutManager = GridLayoutManager(this, COLUMNS)
        binding.slotGrid.adapter = adapter

        binding.checkUpdateButton.setOnClickListener { checkForUpdates() }

        renderSlots()
    }

    private fun renderSlots() {
        val apps = repository.loadApps().associateBy { it.component }
        val tiles = favorites.allSlots().mapIndexed { index, component ->
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
                binding.updateStatus.text =
                    getString(R.string.update_failed, e.message ?: e.javaClass.simpleName)
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
                binding.updateStatus.text =
                    getString(R.string.update_failed, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    companion object {
        private const val COLUMNS = 4
    }
}
