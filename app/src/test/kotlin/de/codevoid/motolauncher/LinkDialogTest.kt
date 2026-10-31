package de.codevoid.motolauncher

import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.platform.app.InstrumentationRegistry
import de.codevoid.motolauncher.ui.LinkDialog
import de.codevoid.motolauncher.ui.normalizeLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The link dialog is opened by touch. Out of touch mode, as after the first remote key,
 * the remote must find nothing to press or type into; the keyboard's Next must still move
 * from the label to the URL. OK accepts only an address that can be opened.
 */
@RunWith(RobolectricTestRunner::class)
class LinkDialogTest {

    private val results = mutableListOf<Bundle>()
    private lateinit var activity: AppCompatActivity

    @Before
    fun startHost() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(AppCompatActivity::class.java).setup().get()
        activity.supportFragmentManager.setFragmentResultListener(KEY, activity) { _, result -> results += result }
    }

    private fun show(label: String = "", url: String = "", editing: Boolean = false): AlertDialog {
        LinkDialog.show(activity.supportFragmentManager, KEY, label, url, editing, Bundle().apply { putInt("slot", 4) })
        activity.supportFragmentManager.executePendingTransactions()
        return (activity.supportFragmentManager.findFragmentByTag(KEY) as LinkDialog).requireDialog() as AlertDialog
    }

    private val AlertDialog.label get() = findViewById<EditText>(R.id.linkLabel)!!
    private val AlertDialog.url get() = findViewById<EditText>(R.id.linkUrl)!!
    private val AlertDialog.ok: Button get() = getButton(DialogInterface.BUTTON_POSITIVE)

    @Test
    fun defaultFocusFindsNeitherField() {
        val dialog = show()
        dialog.window!!.decorView.restoreDefaultFocus()
        assertFalse(dialog.label.hasFocus())
        assertFalse(dialog.url.hasFocus())
    }

    // The style every AlertDialog button takes, so this covers the message dialogs too.
    @Test
    fun theButtonsCannotTakeFocus() {
        val dialog = show()
        assertFalse(dialog.ok.isFocusable)
        assertFalse(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isFocusable)
    }

    @Test
    fun nextFromTheLabelMovesToTheUrl() {
        val dialog = show()
        assertTrue(dialog.label.requestFocus())
        dialog.label.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertTrue(dialog.url.isFocused)
    }

    @Test
    fun okWaitsForANameAndAnAddressThatCanBeOpened() {
        val dialog = show()
        assertFalse(dialog.ok.isEnabled)
        dialog.label.setText("Weather")
        assertFalse(dialog.ok.isEnabled)
        dialog.url.setText("https://")
        assertFalse(dialog.ok.isEnabled)
        dialog.url.setText("example.com/weather")
        assertTrue(dialog.ok.isEnabled)
    }

    @Test
    fun okReportsTheLinkWithASchemeAndTheExtras() {
        val dialog = show(label = " Weather ", url = "example.com/weather")
        dialog.ok.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, results.size)
        assertEquals("Weather", results[0].getString(LinkDialog.LABEL))
        assertEquals("https://example.com/weather", results[0].getString(LinkDialog.URL))
        assertEquals(4, results[0].getInt("slot"))
    }

    @Test
    fun theTitleSaysWhetherALinkIsAddedOrEdited() {
        val adding = show()
        assertEquals(activity.getString(R.string.add_link), adding.title())
        (activity.supportFragmentManager.findFragmentByTag(KEY) as LinkDialog).dismissNow()

        val editing = show(label = "Weather", url = "https://example.com", editing = true)
        assertEquals(activity.getString(R.string.link_action_edit), editing.title())
    }

    private fun AlertDialog.title() = findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

    @Test
    fun anAddressWithoutASchemeBecomesHttps() {
        assertEquals("https://osm.org", normalizeLink("  osm.org "))
        assertEquals("https://example.com/a?b=1", normalizeLink("example.com/a?b=1"))
    }

    @Test
    fun anAddressWithASchemeIsKept() {
        assertEquals("HTTPS://osm.org", normalizeLink("HTTPS://osm.org"))
        assertEquals("geo:49.4,8.6", normalizeLink("geo:49.4,8.6"))
    }

    @Test
    fun nothingOrAWebAddressWithoutAHostIsRejected() {
        assertNull(normalizeLink("   "))
        assertNull(normalizeLink("https://"))
    }

    private companion object {
        const val KEY = "test.link"
    }
}
