package de.codevoid.motolauncher

import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.ui.MessageDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The privacy policy, the update notices and the install prompt, hosted by a real activity. */
@RunWith(RobolectricTestRunner::class)
class MessageDialogTest {

    private val host = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
    private val results = mutableListOf<Bundle>()

    private fun AppCompatActivity.listen() =
        supportFragmentManager.setFragmentResultListener(KEY, this) { _, result -> results += result }

    private fun AppCompatActivity.shown(tag: String): AlertDialog {
        supportFragmentManager.executePendingTransactions()
        return (supportFragmentManager.findFragmentByTag(tag) as MessageDialog).requireDialog() as AlertDialog
    }

    private fun ask(): AlertDialog {
        val activity = host.get()
        activity.listen()
        MessageDialog.ask(
            activity.supportFragmentManager,
            KEY,
            question = "Update available: dev-new",
            positive = "Download & install",
            extras = Bundle().apply { putString("version", "dev-new") },
        )
        return activity.shown(KEY)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun aMessageShowsItsTextWithAnUnfocusableOk() {
        MessageDialog.tell(host.get().supportFragmentManager, "You are on the latest build.")
        val dialog = host.get().shown("message")

        assertEquals("You are on the latest build.", dialog.findViewById<TextView>(android.R.id.message)?.text.toString())
        assertFalse(dialog.getButton(DialogInterface.BUTTON_POSITIVE).isFocusable)
    }

    @Test
    fun acceptingAQuestionReportsItsExtrasOnce() {
        ask().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(listOf("dev-new"), results.map { it.getString("version") })
    }

    @Test
    fun cancellingAQuestionReportsNothing() {
        val dialog = ask()
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()
        assertFalse(dialog.isShowing)
        assertTrue(results.isEmpty())
    }

    // The install prompt used to be lost when the theme switch or a rotation recreated the app list.
    @Test
    fun aQuestionSurvivesARecreateAndStillReports() {
        ask()
        host.recreate()
        val activity = host.get()
        activity.listen()
        val dialog = activity.shown(KEY)

        assertTrue(dialog.isShowing)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(listOf("dev-new"), results.map { it.getString("version") })
    }

    private companion object {
        const val KEY = "test.question"
    }
}
