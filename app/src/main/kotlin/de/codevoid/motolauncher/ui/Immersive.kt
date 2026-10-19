package de.codevoid.motolauncher.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.codevoid.motolauncher.settings.NavBarStore

@Suppress("DEPRECATION")
fun Activity.noTransition() = overridePendingTransition(0, 0)

/** Hides the status bar, and the navigation bar unless [showNavBar]. */
fun Window.enableImmersiveMode(showNavBar: Boolean = false) {
    WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
        if (showNavBar) show(WindowInsetsCompat.Type.navigationBars())
        else hide(WindowInsetsCompat.Type.navigationBars())
    }
}

/** Lays the window out edge to edge and pads its content by the visible bars and cutout. */
fun Window.fitContentToSystemBars() {
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val content = decorView.findViewById<View>(android.R.id.content)
    ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}

/** Shows the dialog with the system bars hidden, unless its activity is finishing or gone. */
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
