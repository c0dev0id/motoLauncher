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
        assertEquals(1, calls.appInfo)
        assertEquals(0, calls.uninstall)
        assertEquals(0, calls.reassign)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun uninstallInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionUninstall).performClick()
        assertEquals(0, calls.appInfo)
        assertEquals(1, calls.uninstall)
        assertEquals(0, calls.reassign)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun reassignInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionReassign).performClick()
        assertEquals(0, calls.appInfo)
        assertEquals(0, calls.uninstall)
        assertEquals(1, calls.reassign)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun reassignRowIsHiddenWhenNoCallbackIsGiven() {
        val dialog = showTileActionsDialog(
            ApplicationProvider.getApplicationContext(),
            entry,
            onAppInfo = {},
            onUninstall = {},
        )
        assertEquals(android.view.View.GONE, dialog.findViewById<TextView>(R.id.actionReassign).visibility)
    }

    @Test
    fun hideRowIsHiddenWhenNoCallbackIsGiven() {
        val dialog = showTileActionsDialog(
            ApplicationProvider.getApplicationContext(),
            entry,
            onAppInfo = {},
            onUninstall = {},
        )
        assertEquals(android.view.View.GONE, dialog.findViewById<TextView>(R.id.actionHide).visibility)
    }

    @Test
    fun hideInvokesCallbackAndDismisses() {
        var hideCount = 0
        val dialog = showTileActionsDialog(
            ApplicationProvider.getApplicationContext(),
            entry,
            onAppInfo = {},
            onUninstall = {},
            onHide = { hideCount++ },
        )
        dialog.findViewById<TextView>(R.id.actionHide).performClick()
        assertEquals(1, hideCount)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun unhideInvokesCallbackAndDismisses() {
        var unhideCount = 0
        val dialog = showTileActionsDialog(
            ApplicationProvider.getApplicationContext(),
            entry,
            onAppInfo = {},
            onUninstall = {},
            onUnhide = { unhideCount++ },
        )
        dialog.findViewById<TextView>(R.id.actionHide).performClick()
        assertEquals(1, unhideCount)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun rowsAppearInDocumentedOrder() {
        val dialog = show()
        val parent = dialog.findViewById<TextView>(R.id.actionAppInfo).parent as android.view.ViewGroup
        val actionIds = setOf(R.id.actionAppInfo, R.id.actionUninstall, R.id.actionReassign)
        val order = (0 until parent.childCount).map { parent.getChildAt(it).id }.filter { it in actionIds }
        assertEquals(listOf(R.id.actionAppInfo, R.id.actionUninstall, R.id.actionReassign), order)
    }
}
