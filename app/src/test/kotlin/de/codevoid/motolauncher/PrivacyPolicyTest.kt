package de.codevoid.motolauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PrivacyPolicyTest {

    @Test
    fun inAppTextIsTheBodyOfPrivacyMd() {
        // Unit tests run from the module directory; the heading and the effective-date
        // paragraph are the file's own header.
        val body = File("../PRIVACY.md").readText().trim()
            .split(Regex("\n\\s*\n"))
            .drop(2)
            .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }
        val inApp = ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.privacy_policy_text)
        assertEquals(body, inApp)
    }
}
