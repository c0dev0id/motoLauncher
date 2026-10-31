package de.codevoid.motolauncher.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.update.HttpStatusException
import de.codevoid.motolauncher.update.ReleaseInfo
import de.codevoid.motolauncher.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What the update flow is doing; the update tile shows it. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState

    /** [percent] is null while the server has not sent a length. */
    data class Downloading(val percent: Int?, val bytesPerSecond: Long) : UpdateState
}

/** An outcome the screen shows once. */
sealed interface UpdateEvent {
    data object UpToDate : UpdateEvent
    data class Available(val release: ReleaseInfo) : UpdateEvent
    data class Install(val intent: Intent) : UpdateEvent
    data class Failed(val error: Exception) : UpdateEvent
}

/**
 * The user-triggered update check and download. It survives a recreate of the screen,
 * runs one step at a time, and delivers each outcome once to whichever activity collects
 * [events].
 */
class UpdateViewModel internal constructor(
    app: Application,
    private val findRelease: suspend () -> ReleaseInfo?,
    private val fetch: suspend (ReleaseInfo, onProgress: (written: Long, total: Long) -> Unit) -> Intent,
) : AndroidViewModel(app) {

    constructor(app: Application) : this(app, UpdateChecker(app))

    private constructor(app: Application, checker: UpdateChecker) : this(
        app,
        findRelease = checker::check,
        fetch = { release, onProgress -> checker.installIntent(checker.download(release, onProgress)) },
    )

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _events = Channel<UpdateEvent>(Channel.BUFFERED)
    val events: Flow<UpdateEvent> = _events.receiveAsFlow()

    /** Asks the release channel for a different build; ignored while a check or download runs. */
    fun check() = startIfIdle(UpdateState.Checking) {
        val release = findRelease()
        _events.send(if (release == null) UpdateEvent.UpToDate else UpdateEvent.Available(release))
    }

    /** Downloads [release] for the installer; ignored while a check or download runs. */
    fun download(release: ReleaseInfo) = startIfIdle(UpdateState.Downloading(null, 0)) {
        var lastBytes = 0L
        var lastTime = SystemClock.elapsedRealtime()
        val installer = fetch(release) { written, total ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastTime >= PROGRESS_INTERVAL_MS) {
                _state.value = UpdateState.Downloading(
                    percent = if (total > 0) (written * 100 / total).toInt() else null,
                    bytesPerSecond = (written - lastBytes) * 1000 / (now - lastTime),
                )
                lastBytes = written
                lastTime = now
            }
        }
        _events.send(UpdateEvent.Install(installer))
    }

    private fun startIfIdle(busy: UpdateState, work: suspend () -> Unit) {
        if (_state.value != UpdateState.Idle) return
        _state.value = busy
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UpdateEvent.Failed(e))
            } finally {
                _state.value = UpdateState.Idle
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}

/** The update tile's progress line, such as "45% · 2.2 MB/s". */
fun UpdateState.Downloading.progressText(): String =
    (percent?.let { "$it% · " } ?: "") + "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024))

/** Shows [event]: a message, the install prompt, the installer, or the failure. */
fun AppCompatActivity.showUpdateEvent(event: UpdateEvent) {
    when (event) {
        UpdateEvent.UpToDate -> MessageDialog.tell(supportFragmentManager, getString(R.string.update_none))
        is UpdateEvent.Available -> MessageDialog.ask(
            supportFragmentManager,
            DOWNLOAD_REQUEST,
            question = getString(R.string.update_available, event.release.versionName),
            positive = getString(R.string.update_download),
            extras = event.release.toBundle(),
        )
        is UpdateEvent.Install -> startActivity(event.intent)
        is UpdateEvent.Failed ->
            MessageDialog.tell(supportFragmentManager, getString(R.string.update_failed, updateErrorText(event.error)))
    }
}

/**
 * Starts the download once the install prompt is accepted. Call it from `onCreate`, so a
 * prompt that outlived a recreate still reaches the flow.
 */
fun AppCompatActivity.downloadWhenAccepted(updates: UpdateViewModel) {
    supportFragmentManager.setFragmentResultListener(DOWNLOAD_REQUEST, this) { _, result ->
        updates.download(result.toReleaseInfo())
    }
}

private const val DOWNLOAD_REQUEST = "update.download"

private fun ReleaseInfo.toBundle() = Bundle().apply {
    putString("versionName", versionName)
    putString("apkUrl", apkUrl)
    putString("apkName", apkName)
}

private fun Bundle.toReleaseInfo() = ReleaseInfo(
    versionName = requireNotNull(getString("versionName")),
    apkUrl = requireNotNull(getString("apkUrl")),
    apkName = requireNotNull(getString("apkName")),
)

/** [error] in words a rider can act on. */
fun Context.updateErrorText(error: Exception): String = when (error) {
    is HttpStatusException ->
        if (error.code == HttpURLConnection.HTTP_NOT_FOUND) getString(R.string.update_error_not_found)
        else getString(R.string.update_error_http, error.code)
    is UnknownHostException, is ConnectException -> getString(R.string.update_error_offline)
    is SocketTimeoutException -> getString(R.string.update_error_timeout)
    else -> error.message ?: error.javaClass.simpleName
}
