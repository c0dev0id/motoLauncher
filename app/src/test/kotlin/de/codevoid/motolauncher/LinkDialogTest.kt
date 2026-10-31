package de.codevoid.motolauncher

import android.content.Context
import android.content.DialogInterface
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import de.codevoid.motolauncher.ui.showLinkDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The link dialog is opened by touch. Out of touch mode, as after the first remote key,
 * the remote must find nothing to press or type into; the keyboard's Next must still move
 * from the label to the URL.
 */
@RunWith(RobolectricTestRunner::class)
class LinkDialogTest {

    private lateinit var dialog: AlertDialog
    private lateinit var label: EditText
    private lateinit var url: EditText

    // The app theme, where an activity would take it from the manifest; AlertDialog needs
    // its alertDialogTheme.
    @Before
    fun show() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_MotoLauncher,
        )
        dialog = context.showLinkDialog { _, _ -> }
        label = dialog.findViewById<EditText>(R.id.linkLabel)!!
        url = dialog.findViewById<EditText>(R.id.linkUrl)!!
    }

    @Test
    fun defaultFocusFindsNeitherField() {
        dialog.window!!.decorView.restoreDefaultFocus()
        assertFalse(label.hasFocus())
        assertFalse(url.hasFocus())
    }

    // The style every AlertDialog button takes, so this covers the update and privacy
    // dialogs too.
    @Test
    fun theButtonsCannotTakeFocus() {
        assertFalse(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isFocusable)
        assertFalse(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isFocusable)
    }

    @Test
    fun nextFromTheLabelMovesToTheUrl() {
        assertTrue(label.requestFocus())
        label.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertTrue(url.isFocused)
    }
}
