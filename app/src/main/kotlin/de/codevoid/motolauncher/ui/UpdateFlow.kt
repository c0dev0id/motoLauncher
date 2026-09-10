package de.codevoid.motolauncher.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// User-triggered update check: the caller is disabled while the flow is in flight
// (setClickable false → true) and each outcome — latest build, update available with an
// install prompt, failure — is a dialog. Off-bike, touch-only flow, so stock alert
// buttons are acceptable.
//
// CancellationException is an Exception, so the catches rethrow it: a job cancelled
// at ON_DESTROY must complete as cancelled, not as "handled". Dialogs reached after
// the activity is gone are dropped by showImmersive().
fun AppCompatActivity.runUpdateFlow(
    setClickable: (Boolean) -> Unit = {},
) {
    val checker = UpdateChecker(this)

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
        setClickable(false)
        lifecycleScope.launch {
            try {
                startActivity(checker.installIntent(checker.download(release)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            } finally {
                setClickable(true)
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

    setClickable(false)
    lifecycleScope.launch {
        try {
            val release = checker.check()
            if (release == null) showMessage(getString(R.string.update_none)) else promptInstall(release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showError(e)
        } finally {
            setClickable(true)
        }
    }
}
