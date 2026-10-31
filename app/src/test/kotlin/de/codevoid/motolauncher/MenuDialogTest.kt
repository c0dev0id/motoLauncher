package de.codevoid.motolauncher

import android.app.Dialog
import android.os.Bundle
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.ui.MenuDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The one row menu behind the tile menus and the settings pickers, hosted by a real activity. */
@RunWith(RobolectricTestRunner::class)
class MenuDialogTest {

    private val host = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
    private val results = mutableListOf<Bundle>()
    private val rows = listOf(10 to "Landscape", 20 to "Portrait", 30 to "Reverse landscape", 40 to "Sensor")

    private fun AppCompatActivity.listen() =
        supportFragmentManager.setFragmentResultListener(KEY, this) { _, result -> results += result }

    private fun show(selected: Int? = null, iconRes: Int = 0): Dialog {
        val activity = host.get()
        activity.listen()
        MenuDialog.show(
            activity.supportFragmentManager,
            KEY,
            title = "Orientation",
            rows = rows,
            selected = selected,
            iconRes = iconRes,
            extras = Bundle().apply { putInt("slot", 3) },
        )
        activity.supportFragmentManager.executePendingTransactions()
        return activity.menu()
    }

    private fun AppCompatActivity.menu(): Dialog =
        (supportFragmentManager.findFragmentByTag(KEY) as MenuDialog).requireDialog()

    private fun Dialog.rows(): List<TextView> {
        val container = findViewById<ViewGroup>(R.id.optionRows)
        return (0 until container.childCount).map { container.getChildAt(it) as TextView }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun showsTheTitleAndOneRowPerEntryInOrder() {
        val dialog = show()
        assertTrue(dialog.isShowing)
        assertEquals("Orientation", dialog.findViewById<TextView>(R.id.optionsTitle).text.toString())
        assertEquals(rows.map { it.second }, dialog.rows().map { it.text.toString() })
    }

    @Test
    fun tappingARowReportsItsIdWithTheExtrasOnceAndCloses() {
        val dialog = show()
        dialog.rows()[2].performClick()
        idle()

        assertEquals(1, results.size)
        assertEquals(30, results[0].getInt(MenuDialog.ROW))
        assertEquals(3, results[0].getInt("slot"))
        assertFalse(dialog.isShowing)
    }

    // With the tile hidden behind the dialog, this is the only sign of the current value.
    // The fill marks it: in the dark theme the text is white on both fills.
    @Test
    fun onlyTheSelectedRowIsMarkedAndDrawnDifferently() {
        val rows = show(selected = 40).rows()
        assertEquals(listOf(false, false, false, true), rows.map { it.isSelected })
        assertNotEquals(rows[0].background.current.constantState, rows[3].background.current.constantState)
    }

    @Test
    fun withoutASelectionNoRowIsMarked() {
        assertTrue(show().rows().none { it.isSelected })
    }

    // A touch opens the menu; the remote must not be able to press a row.
    @Test
    fun noRowCanTakeFocus() {
        assertTrue(show().rows().none { it.isFocusable })
    }

    @Test
    fun escapeClosesItWithoutAResult() {
        val dialog = show()
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE))
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE))
        idle()

        assertFalse(dialog.isShowing)
        assertTrue(results.isEmpty())
    }

    // A theme switch or a rotation recreates the activity while the menu is up.
    @Test
    fun theMenuSurvivesARecreateAndStillReports() {
        show(selected = 20)
        host.recreate()
        val activity = host.get()
        activity.listen()
        activity.supportFragmentManager.executePendingTransactions()
        val dialog = activity.menu()

        assertTrue(dialog.isShowing)
        assertEquals(listOf(false, true, false, false), dialog.rows().map { it.isSelected })
        dialog.rows()[0].performClick()
        idle()
        assertEquals(listOf(10), results.map { it.getInt(MenuDialog.ROW) })
    }

    @Test
    fun theHeaderShowsAnIconOnlyWhenGivenOne() {
        assertEquals(View.GONE, show().findViewById<ImageView>(R.id.optionsIcon).visibility)
        (host.get().supportFragmentManager.findFragmentByTag(KEY) as MenuDialog).dismissNow()

        assertEquals(View.VISIBLE, show(iconRes = R.drawable.ic_link).findViewById<ImageView>(R.id.optionsIcon).visibility)
    }

    // A double tap on a tile must not stack two menus.
    @Test
    fun aSecondShowWhileOneIsUpDoesNothing() {
        show()
        show()
        assertEquals(1, host.get().supportFragmentManager.fragments.count { it is MenuDialog })
    }

    private companion object {
        const val KEY = "test.menu"
    }
}
