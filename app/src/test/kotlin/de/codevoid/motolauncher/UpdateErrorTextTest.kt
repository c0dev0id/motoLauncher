package de.codevoid.motolauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.ui.updateErrorText
import de.codevoid.motolauncher.update.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class UpdateErrorTextTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // CI deletes and recreates the dev pre-release, so a 404 is a matter of minutes.
    @Test
    fun notFoundSaysToTryAgainShortly() {
        assertEquals(context.getString(R.string.update_error_not_found), context.updateErrorText(HttpStatusException(404)))
    }

    @Test
    fun anotherStatusNamesItsCode() {
        assertEquals("the server answered with HTTP 503.", context.updateErrorText(HttpStatusException(503)))
    }

    @Test
    fun noNetworkAndNoAnswerAreNamed() {
        assertEquals(context.getString(R.string.update_error_offline), context.updateErrorText(UnknownHostException("api.github.com")))
        assertEquals(context.getString(R.string.update_error_timeout), context.updateErrorText(SocketTimeoutException()))
    }

    @Test
    fun anythingElseKeepsItsMessage() {
        assertEquals("disk full", context.updateErrorText(IOException("disk full")))
    }
}
