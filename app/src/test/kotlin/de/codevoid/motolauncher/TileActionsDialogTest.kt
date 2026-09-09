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

    private class Calls {
        var appInfo = 0
        var uninstall = 0
        var reassign = 0
    }

    private fun show(calls: Calls = Calls()) = showTileActionsDialog(
        ApplicationProvider.getApplicationContext(),
        entry,
        onAppInfo = { calls.appInfo++ },
        onUninstall = { calls.uninstall++ },
        onReassign = { calls.reassign++ },
    )

    @Test
    fun showsTheAppLabel() {
        val dialog = show()
        assertEquals("Example", dialog.findViewById<TextView>(R.id.appLabel).text.toString())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun appInfoInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionAppInfo).performClick()
        assertEquals(listOf(1, 0, 0), listOf(calls.appInfo, calls.uninstall, calls.reassign))
        assertFalse(dialog.isShowing)
    }

    @Test
    fun uninstallInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionUninstall).performClick()
        assertEquals(listOf(0, 1, 0), listOf(calls.appInfo, calls.uninstall, calls.reassign))
        assertFalse(dialog.isShowing)
    }

    @Test
    fun reassignInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionReassign).performClick()
        assertEquals(listOf(0, 0, 1), listOf(calls.appInfo, calls.uninstall, calls.reassign))
        assertFalse(dialog.isShowing)
    }

    @Test
    fun rowsAppearInDocumentedOrder() {
        val dialog = show()
        val parent = dialog.findViewById<TextView>(R.id.actionAppInfo).parent as android.view.ViewGroup
        val ids = (0 until parent.childCount).map { parent.getChildAt(it).id }
        val appInfo = ids.indexOf(R.id.actionAppInfo)
        val uninstall = ids.indexOf(R.id.actionUninstall)
        val reassign = ids.indexOf(R.id.actionReassign)
        assertTrue(appInfo < uninstall && uninstall < reassign)
    }
}
