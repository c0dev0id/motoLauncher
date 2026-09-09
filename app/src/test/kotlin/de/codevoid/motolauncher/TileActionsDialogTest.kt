package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.ui.showTileActionsDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TileActionsDialogTest {

    private val entry = AppEntry(ComponentName("com.example", "com.example.Main"), "Example", ColorDrawable())

    private fun show(onReassign: () -> Unit = {}, onAppInfo: () -> Unit = {}) =
        showTileActionsDialog(ApplicationProvider.getApplicationContext(), entry, onReassign, onAppInfo)

    @Test
    fun showsTheAppLabel() {
        val dialog = show()
        assertEquals("Example", dialog.findViewById<TextView>(R.id.appLabel).text.toString())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun reassignInvokesOnlyItsCallbackAndDismisses() {
        var reassign = 0
        var appInfo = 0
        val dialog = show(onReassign = { reassign++ }, onAppInfo = { appInfo++ })
        dialog.findViewById<TextView>(R.id.actionReassign).performClick()
        assertEquals(1, reassign)
        assertEquals(0, appInfo)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun appInfoInvokesOnlyItsCallbackAndDismisses() {
        var reassign = 0
        var appInfo = 0
        val dialog = show(onReassign = { reassign++ }, onAppInfo = { appInfo++ })
        dialog.findViewById<TextView>(R.id.actionAppInfo).performClick()
        assertEquals(0, reassign)
        assertEquals(1, appInfo)
        assertFalse(dialog.isShowing)
    }
}
