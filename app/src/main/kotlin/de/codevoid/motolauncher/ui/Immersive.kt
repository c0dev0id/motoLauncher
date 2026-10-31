package de.codevoid.motolauncher.ui

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import de.codevoid.motolauncher.data.NavBarStore

@Suppress("DEPRECATION")
fun Activity.noTransition() = overridePendingTransition(0, 0)

// Status bar always hidden; nav bar follows the NavBarStore setting.
// Activities re-apply on onWindowFocusChanged(true) because permission dialogs and
// the system installer can transiently restore bars.
fun Window.enableImmersiveMode(showNavBar: Boolean = false) {
    WindowInsetsControllerCompat(this, decorView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.statusBars())
        if (showNavBar) show(WindowInsetsCompat.Type.navigationBars())
        else hide(WindowInsetsCompat.Type.navigationBars())
    }
}

// Lays out an activity window edge to edge and pads its content by the visible system
// bars and display cutout.
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

/** Keeps the system bars hidden while this dialog is up; call it before the dialog is shown. */
fun Dialog.hideSystemBars() {
    window?.enableImmersiveMode(NavBarStore(context).showNavBar)
}
