package de.codevoid.motolauncher.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration

/** Makes Enter click on release and never long-press; a touch long press still works. */
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
 * Escape from the remote or a keyboard: a press shorter than [longPressTimeoutMs] runs
 * [onShortPress], a longer one [onLongPress]. Wire both [onKeyDown] and [onKeyUp].
 */
class EscapeKeys(
    private val onLongPress: () -> Unit,
    private val onShortPress: () -> Unit = {},
    private val longPressTimeoutMs: Long = ViewConfiguration.getLongPressTimeout().toLong(),
) {
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.repeatCount == 0) event.startTracking()
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        // An untracked UP ends a press that began in another window.
        if (!event.isTracking || event.isCanceled) return true
        if (event.eventTime - event.downTime >= longPressTimeoutMs) onLongPress() else onShortPress()
        return true
    }
}
