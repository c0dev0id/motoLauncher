package de.codevoid.motolauncher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Suppress the animation between activities — this launcher owns the full screen and
// sliding or fading to another launcher activity looks wrong.
@Suppress("DEPRECATION")
fun Activity.noTransition() = overridePendingTransition(0, 0)

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
// system bars back for as long as it is up. Every dialog the app shows goes through
// here, which is also where "never show on a finishing or destroyed activity" lives:
// a dialog produced after an await (the update flow) can otherwise outlive its host
// and crash on show() with a dead window token.
fun Dialog.showImmersive() {
    val host = hostActivity()
    if (host != null && (host.isFinishing || host.isDestroyed)) return
    window?.enableImmersiveMode()
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
