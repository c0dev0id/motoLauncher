package de.codevoid.motolauncher.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Bars stay hidden until an explicit swipe from the edge, which never happens on the
// remote and is impractical with gloves. Re-apply on onWindowFocusChanged(true) because
// permission dialogs and the system installer can transiently restore them.
fun AppCompatActivity.enableImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
