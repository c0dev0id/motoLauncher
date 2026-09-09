package de.codevoid.motolauncher.ui

import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.launch

// User-triggered update check driven from one button: the button label carries
// progress ("Checking…", "Downloading…", disabled meanwhile) and each outcome — latest
// build, update available with an install prompt, failure — is a dialog. This is an
// off-bike, touch-only flow, so the stock alert buttons are acceptable here.
fun AppCompatActivity.runUpdateFlow(button: Button) {
    val checker = UpdateChecker(this)

    fun setButton(labelRes: Int, enabled: Boolean) {
        button.isEnabled = enabled
        button.setText(labelRes)
    }

    fun showMessage(text: String) {
        AlertDialog.Builder(this)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .create()
            .showImmersive()
    }

    fun showError(e: Exception) =
        showMessage(getString(R.string.update_failed, e.message ?: e.javaClass.simpleName))

    fun downloadAndInstall(release: ReleaseInfo) {
        setButton(R.string.downloading, enabled = false)
        lifecycleScope.launch {
            try {
                startActivity(checker.installIntent(checker.download(release)))
            } catch (e: Exception) {
                showError(e)
            } finally {
                setButton(R.string.check_for_updates, enabled = true)
            }
        }
    }

    fun promptInstall(release: ReleaseInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, release.versionName))
            .setPositiveButton(R.string.update_download) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .showImmersive()
    }

    setButton(R.string.checking_updates, enabled = false)
    lifecycleScope.launch {
        try {
            val release = checker.check()
            if (release == null) showMessage(getString(R.string.update_none)) else promptInstall(release)
        } catch (e: Exception) {
            showError(e)
        } finally {
            setButton(R.string.check_for_updates, enabled = true)
        }
    }
}
