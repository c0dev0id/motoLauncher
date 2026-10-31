package de.codevoid.motolauncher

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.ui.UpdateEvent
import de.codevoid.motolauncher.ui.UpdateState
import de.codevoid.motolauncher.ui.UpdateViewModel
import de.codevoid.motolauncher.ui.progressText
import de.codevoid.motolauncher.update.ReleaseInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.io.IOException
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class UpdateViewModelTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val release = ReleaseInfo("dev-new", "https://example.com/app.apk", "motoLauncher-dev-new.apk")
    private val installer = Intent("install")

    private fun updates(
        findRelease: suspend () -> ReleaseInfo? = { release },
        fetch: suspend (ReleaseInfo, (Long, Long) -> Unit) -> Intent = { _, _ -> installer },
    ) = UpdateViewModel(app, findRelease, fetch)

    private fun UpdateViewModel.nextEvent() = runBlocking { withTimeout(1_000) { events.first() } }

    @Test
    fun aCheckReportsTheReleaseOrThatTheBuildIsCurrent() {
        val available = updates()
        available.check()
        assertEquals(UpdateEvent.Available(release), available.nextEvent())

        val current = updates(findRelease = { null })
        current.check()
        assertEquals(UpdateEvent.UpToDate, current.nextEvent())
    }

    @Test
    fun aTapWhileACheckRunsStartsNothing() {
        val answer = CompletableDeferred<ReleaseInfo?>()
        var checks = 0
        val updates = updates(findRelease = { checks++; answer.await() })

        updates.check()
        updates.check()
        updates.download(release)
        assertEquals(1, checks)
        assertEquals(UpdateState.Checking, updates.state.value)

        answer.complete(null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateState.Idle, updates.state.value)
    }

    @Test
    fun aFailureIsReportedAndLeavesTheFlowIdle() {
        val updates = updates(findRelease = { throw IOException("disk full") })
        updates.check()
        assertEquals("disk full", (updates.nextEvent() as UpdateEvent.Failed).error.message)
        assertEquals(UpdateState.Idle, updates.state.value)
    }

    @Test
    fun aDownloadShowsProgressThenHandsOverToTheInstaller() {
        val finish = CompletableDeferred<Unit>()
        val updates = updates(fetch = { _, onProgress ->
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            onProgress(MB, 2 * MB)
            finish.await()
            installer
        })

        updates.download(release)
        assertEquals(UpdateState.Downloading(percent = 50, bytesPerSecond = MB), updates.state.value)

        finish.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(UpdateEvent.Install(installer), updates.nextEvent())
        assertEquals(UpdateState.Idle, updates.state.value)
    }

    @Test
    fun progressReadsAsPercentAndSpeed() {
        assertEquals("45% · 2.2 MB/s", UpdateState.Downloading(45, 2_306_867).progressText())
        assertEquals("0.0 MB/s", UpdateState.Downloading(null, 0).progressText())
    }

    private companion object {
        const val MB = 1024L * 1024
    }
}
