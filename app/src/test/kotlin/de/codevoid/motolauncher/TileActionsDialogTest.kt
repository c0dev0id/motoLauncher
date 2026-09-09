package de.codevoid.motolauncher

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.ui.TileActionsDialog
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
        var reassign = 0
        var appInfo = 0
    }

    private fun show(calls: Calls) = TileActionsDialog.create(
        context = ApplicationProvider.getApplicationContext(),
        entry = entry,
        onReassign = { calls.reassign++ },
        onAppInfo = { calls.appInfo++ },
    ).also { it.show() }

    @Test
    fun showsTheAppLabel() {
        val dialog = show(Calls())
        assertEquals("Example", dialog.findViewById<TextView>(R.id.appLabel).text.toString())
        assertTrue(dialog.isShowing)
    }

    @Test
    fun reassignInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionReassign).performClick()
        assertEquals(1, calls.reassign)
        assertEquals(0, calls.appInfo)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun appInfoInvokesOnlyItsCallbackAndDismisses() {
        val calls = Calls()
        val dialog = show(calls)
        dialog.findViewById<TextView>(R.id.actionAppInfo).performClick()
        assertEquals(0, calls.reassign)
        assertEquals(1, calls.appInfo)
        assertFalse(dialog.isShowing)
    }
}
