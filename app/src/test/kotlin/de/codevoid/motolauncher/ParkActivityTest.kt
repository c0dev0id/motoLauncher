package de.codevoid.motolauncher

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.ParkStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

/**
 * The park screen's flows, with Robolectric's lock task shadow standing in for screen
 * pinning: startLockTask and stopLockTask set the state that lockTaskModeState reports.
 */
@RunWith(RobolectricTestRunner::class)
class ParkActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ParkStore(context)

    private val keys = mapOf(
        '0' to R.id.key0, '1' to R.id.key1, '2' to R.id.key2, '3' to R.id.key3, '4' to R.id.key4,
        '5' to R.id.key5, '6' to R.id.key6, '7' to R.id.key7, '8' to R.id.key8, '9' to R.id.key9,
    )

    // Long enough for the guard and the settle check to run.
    private fun settle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

    private fun park(): ParkActivity =
        Robolectric.buildActivity(ParkActivity::class.java, ParkActivity.lockIntent(context))
            .setup().get().also { settle() }

    private fun setPinScreen() =
        Robolectric.buildActivity(ParkActivity::class.java, ParkActivity.setPinIntent(context))
            .setup().also { settle() }

    private fun ParkActivity.enter(pin: String) =
        pin.forEach { findViewById<TextView>(keys.getValue(it)).performClick() }

    private fun ParkActivity.prompt() = findViewById<TextView>(R.id.parkPrompt).text.toString()

    private fun ParkActivity.dots() = findViewById<TextView>(R.id.parkDots).text.toString()

    private fun ParkActivity.activityManager() =
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private fun ParkActivity.pinned() =
        activityManager().lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    @Test
    fun parkingMarksTheLauncherParkedAndPins() {
        store.setPin("1234")
        val park = park()
        assertTrue(store.isParked)
        assertTrue(park.pinned())
        assertEquals(park.getString(R.string.park_enter_pin), park.prompt())
    }

    @Test
    fun theCorrectPinUnparksUnpinsAndCloses() {
        store.setPin("1234")
        val park = park()
        park.enter("1234")
        assertFalse(store.isParked)
        assertFalse(park.pinned())
        assertTrue(park.isFinishing)
    }

    @Test
    fun aWrongPinKeepsTheLock() {
        store.setPin("1234")
        val park = park()
        park.enter("9999")
        assertTrue(store.isParked)
        assertTrue(park.pinned())
        assertFalse(park.isFinishing)
        assertEquals(park.getString(R.string.park_wrong_pin), park.prompt())
    }

    // The system unpin gesture cannot be blocked; the guard undoes it.
    @Test
    fun theGuardRepinsAfterAnUnpin() {
        store.setPin("1234")
        val park = park()
        shadowOf(park.activityManager()).setLockTaskModeState(ActivityManager.LOCK_TASK_MODE_NONE)
        settle()
        assertTrue(park.pinned())
    }

    @Test
    fun aNewPinIsEnteredTwiceAndNeverPins() {
        val screen = setPinScreen().get()
        screen.enter("1234")
        assertEquals(screen.getString(R.string.park_repeat_pin), screen.prompt())
        screen.enter("1234")
        assertTrue(store.verify("1234"))
        assertTrue(screen.isFinishing)
        assertFalse(store.isParked)
        assertFalse(screen.pinned())
    }

    @Test
    fun aMismatchStartsTheSetupOver() {
        val screen = setPinScreen().get()
        screen.enter("1234")
        screen.enter("5678")
        assertFalse(store.hasPin)
        assertEquals(screen.getString(R.string.park_pin_mismatch), screen.prompt())

        screen.enter("4321")
        screen.enter("4321")
        assertTrue(store.verify("4321"))
    }

    // The screen is singleInstance: parking after an abandoned setup reuses it, and the
    // system pauses and resumes it around onNewIntent.
    @Test
    fun parkingAfterAnAbandonedSetupShowsTheLock() {
        store.setPin("1234")
        val controller = setPinScreen()
        controller.get().enter("12")

        controller.pause().newIntent(ParkActivity.lockIntent(context)).resume()
        settle()

        val park = controller.get()
        assertEquals(park.getString(R.string.park_enter_pin), park.prompt())
        assertEquals("○".repeat(ParkStore.PIN_LENGTH), park.dots())
        assertTrue(store.isParked)
        assertTrue(park.pinned())
    }

    // Pinning does not survive a reboot; Home brings the lock back while parked.
    @Test
    fun homeRestoresTheLockWhileParked() {
        store.isParked = true
        val home = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        assertEquals(
            ComponentName(context, ParkActivity::class.java),
            shadowOf(home).nextStartedActivity.component,
        )
    }

    @Test
    fun homeStartsNothingWhileNotParked() {
        val home = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        assertNull(shadowOf(home).nextStartedActivity)
    }
}
