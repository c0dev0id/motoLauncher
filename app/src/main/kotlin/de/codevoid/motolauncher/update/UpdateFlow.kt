package de.codevoid.motolauncher.update

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.ui.showImmersive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Checks for an update and offers to download and install it, each outcome in a dialog.
 * [setClickable] is false while a step runs; [setSubtitle] receives download progress.
 */
fun AppCompatActivity.runUpdateFlow(
    setClickable: (Boolean) -> Unit = {},
    setSubtitle: (String) -> Unit = {},
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
        val progress = DownloadProgress()
        lifecycleScope.launch {
            try {
                val apk = checker.download(release) { written, total ->
                    progress.report(written, total)?.let { runOnUiThread { setSubtitle(it) } }
                }
                startActivity(checker.installIntent(apk))
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
