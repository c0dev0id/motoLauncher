package de.codevoid.motolauncher.ui

import android.view.KeyEvent
import android.view.View

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
