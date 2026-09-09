package de.codevoid.motolauncher.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.FavoritesStore

// Route DPAD_CENTER / ENTER through performClick() on ACTION_UP without arming the
// framework's long-press timer. Touch long-press (setOnLongClickListener) still works;
// keyboard/remote long-press does not, which stops a held remote button from reaching
// touch-only destinations (app info, configuration) that a rider can't navigate back
// out of without touching the screen.
fun View.blockKeyLongPress() {
    setOnKeyListener { view, keyCode, event ->
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) view.isPressed = true
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        view.isPressed = false
                        view.performClick()
                        true
                    }
                    else -> true
                }
            }
            else -> false
        }
    }
}

/**
 * ESC from the handlebar remote (and any USB/BT keyboard): a short press runs
 * [onShortPress] — the screen's own "back", since Android doesn't route ESC to the back
 * dispatcher — while holding the key past the framework's key-repeat delay (~500 ms)
 * runs [onLongPress] instead.
 *
 * The short action can only run on key-up: at ACTION_DOWN it isn't known yet whether the
 * press will become a long one. [onKeyDown] claims the DOWN and calls `startTracking()`,
 * which is what makes the framework deliver [onKeyLongPress] on the first repeat;
 * returning true from there marks the press consumed, so the following UP arrives
 * canceled and the short action skips itself. All three must be wired from the activity —
 * with [onKeyDown] missing, the framework never tracks the key and the long press is
 * never reported.
 *
 * Whether a long press is reachable at all is a property of the remote: a button that
 * emits an instantaneous down/up pair instead of holding the key can't produce one. The
 * short press works either way.
 */
class EscapeKeys(
    private val onLongPress: () -> Unit,
    private val onShortPress: () -> Unit = {},
) {
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.repeatCount == 0) event.startTracking()
        return true
    }

    fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        onLongPress()
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        // Not tracking: the DOWN went to someone else (the press started before this
        // window had focus). Canceled: the long press already fired for this press.
        if (event.isTracking && !event.isCanceled) onShortPress()
        return true
    }
}

// The remote's one action beyond launching a focused tile, and still only a launch:
// holding ESC starts the app in the first favourite slot — the top-left home tile.
// An empty slot, or one whose app has been uninstalled, is a silent no-op: there is
// nothing worth showing someone riding with gloves on.
fun Context.launchFirstFavorite() {
    val component = FavoritesStore(this).getSlot(FavoritesStore.QUICK_LAUNCH_SLOT) ?: return
    AppRepository(this).launchIfInstalled(component)
}
