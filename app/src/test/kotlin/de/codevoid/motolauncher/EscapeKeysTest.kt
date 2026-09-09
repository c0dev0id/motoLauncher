package de.codevoid.motolauncher

import android.view.KeyEvent
import de.codevoid.motolauncher.ui.EscapeKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Events go through `KeyEvent.dispatch` with a real `KeyEvent.DispatcherState` — the same
 * path an activity's key handling takes — rather than through the three methods directly
 * with hand-set flags. That way the test exercises the framework's tracking machinery
 * instead of restating what it is assumed to do: the DOWN is only tracked if the handler
 * claims it, `onKeyLongPress` is only delivered while tracking, and the UP is only
 * canceled because the long press was reported.
 */
@RunWith(RobolectricTestRunner::class)
class EscapeKeysTest {

    private var shortPresses = 0
    private var longPresses = 0
    private val keys = EscapeKeys(
        onLongPress = { longPresses++ },
        onShortPress = { shortPresses++ },
    )

    private val state = KeyEvent.DispatcherState()

    // Wired exactly as HomeActivity and AppListActivity wire it.
    private val callback = object : KeyEvent.Callback {
        override fun onKeyDown(keyCode: Int, event: KeyEvent) = keys.onKeyDown(keyCode, event)
        override fun onKeyLongPress(keyCode: Int, event: KeyEvent) =
            keys.onKeyLongPress(keyCode, event)
        override fun onKeyUp(keyCode: Int, event: KeyEvent) = keys.onKeyUp(keyCode, event)
        override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent) = false
    }

    private fun dispatch(
        action: Int,
        keyCode: Int = KeyEvent.KEYCODE_ESCAPE,
        repeatCount: Int = 0,
        flags: Int = 0,
    ): Boolean {
        val event = KeyEvent(0L, 0L, action, keyCode, repeatCount, 0, 0, 0, flags)
        return event.dispatch(callback, state, this)
    }

    private fun down() = dispatch(KeyEvent.ACTION_DOWN)

    // The first key repeat carries FLAG_LONG_PRESS; that is what the framework turns into
    // an onKeyLongPress callback, and it arrives roughly a key-repeat delay after the DOWN.
    private fun hold() = dispatch(KeyEvent.ACTION_DOWN, repeatCount = 1, flags = KeyEvent.FLAG_LONG_PRESS)

    private fun up() = dispatch(KeyEvent.ACTION_UP)

    @Test
    fun tapRunsTheShortActionOnKeyUp() {
        assertTrue(down())
        assertEquals("nothing may happen before the key comes back up", 0, shortPresses)

        assertTrue(up())
        assertEquals(1, shortPresses)
        assertEquals(0, longPresses)
    }

    @Test
    fun holdRunsTheLongActionAndSuppressesTheShortOne() {
        down()
        hold()
        assertEquals(1, longPresses)

        up()
        assertEquals(0, shortPresses)
        assertEquals(1, longPresses)
    }

    @Test
    fun keyRepeatsBeyondTheFirstChangeNothing() {
        down()
        hold()
        dispatch(KeyEvent.ACTION_DOWN, repeatCount = 2)
        dispatch(KeyEvent.ACTION_DOWN, repeatCount = 3)
        assertEquals(1, longPresses)

        up()
        assertEquals(0, shortPresses)
    }

    @Test
    fun anUntrackedUpRunsNothing() {
        // No DOWN reached this handler — the press started before the window had focus.
        assertTrue(up())
        assertEquals(0, shortPresses)
        assertEquals(0, longPresses)
    }

    @Test
    fun aHoldReportedWithoutTrackingIsIgnored() {
        // Without the DOWN the framework never tracks the key, so the long press is never
        // delivered: wiring onKeyDown is not optional.
        hold()
        assertEquals(0, longPresses)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        val code = KeyEvent.KEYCODE_DPAD_CENTER
        assertFalse(dispatch(KeyEvent.ACTION_DOWN, keyCode = code))
        assertFalse(dispatch(KeyEvent.ACTION_DOWN, keyCode = code, repeatCount = 1, flags = KeyEvent.FLAG_LONG_PRESS))
        assertFalse(dispatch(KeyEvent.ACTION_UP, keyCode = code))
        assertEquals(0, shortPresses)
        assertEquals(0, longPresses)
    }
}
