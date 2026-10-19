package de.codevoid.motolauncher

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImmersiveTest {

    @Test
    fun contentIsPaddedByVisibleBarsAndCutout() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        activity.window.fitContentToSystemBars()
        val content = activity.findViewById<View>(android.R.id.content)

        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(32, 0, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, insets)

        assertEquals(listOf(32, 0, 0, 48), with(content) {
            listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
        })
    }

    @Test
    fun hidingTheNavBarDropsItsPadding() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        activity.window.fitContentToSystemBars()
        val content = activity.findViewById<View>(android.R.id.content)

        val navBarShown = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, navBarShown)
        ViewCompat.dispatchApplyWindowInsets(content, WindowInsetsCompat.Builder().build())

        assertEquals(0, content.paddingBottom)
    }
}
