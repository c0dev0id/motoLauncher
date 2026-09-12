package de.codevoid.motolauncher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.codevoid.motolauncher.data.NavBarStore

@Suppress("DEPRECATION")
fun Activity.noTransition() = overridePendingTransition(0, 0)

// Status bar always hidden; nav bar follows the NavBarStore setting.
// setDecorFitsSystemWindows mirrors showNavBar so the grid automatically makes room
// for a visible nav bar without any manual inset handling.
// Activities re-apply on onWindowFocusChanged(true) because permission dialogs and
// the system installer can transiently restore bars.
fun Window.enableImmersiveMode(showNavBar: Boolean = false) {
    WindowCompat.setDecorFitsSystemWindows(this, showNavBar)
    WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
        if (showNavBar) show(WindowInsetsCompat.Type.navigationBars())
        else hide(WindowInsetsCompat.Type.navigationBars())
    }
}

// A dialog is its own window; shown plainly over an immersive activity it brings the
// system bars back for as long as it is up. Every dialog the app shows goes through
// here, which is also where "never show on a finishing or destroyed activity" lives:
// a dialog produced after an await (the update flow) can otherwise outlive its host
// and crash on show() with a dead window token.
fun Dialog.showImmersive() {
    val host = hostActivity()
    if (host != null && (host.isFinishing || host.isDestroyed)) return
    window?.enableImmersiveMode(host?.let { NavBarStore(it).showNavBar } ?: false)
    show()
}

private fun Dialog.hostActivity(): Activity? {
    var c: Context = context
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
