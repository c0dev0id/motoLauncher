package de.codevoid.motolauncher.ui

import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Bars stay hidden until an explicit swipe from the edge, which never happens on the
// remote and is impractical with gloves. Re-apply on onWindowFocusChanged(true) because
// permission dialogs and the system installer can transiently restore them.
fun AppCompatActivity.enableImmersiveMode() = window.enableImmersiveMode()

// Dialogs are separate windows; without this a dialog opening over an immersive
// activity brings the system bars back for as long as it is showing.
fun Window.enableImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}
