package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Bars stay hidden until an explicit swipe from the edge, which never happens on the
// remote and is impractical with gloves. Activities re-apply on onWindowFocusChanged(true)
// because permission dialogs and the system installer can transiently restore them.
fun Window.enableImmersiveMode() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

// A dialog is its own window; shown plainly over an immersive activity it brings the
// system bars back for as long as it is up. Every dialog the app shows goes through here.
fun Dialog.showImmersive() {
    window?.enableImmersiveMode()
    show()
}
