package de.codevoid.motolauncher

import de.codevoid.motolauncher.update.DownloadProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class DownloadProgressTest {

    private val mb = 1024L * 1024
    private var clock = 0L
    private val progress = DownloadProgress(intervalMs = 500, now = { clock })
    private lateinit var savedLocale: Locale

    @Before
    fun pinLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.ROOT)
    }

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    @Test
    fun reportsTheFirstChunkAtOnce() {
        clock = 250
        assertEquals("12% · 4.0 MB/s", progress.report(mb, 8 * mb))
    }

    @Test
    fun staysQuietWithinTheInterval() {
        progress.report(mb, 8 * mb)
        clock = 499
        assertNull(progress.report(2 * mb, 8 * mb))
    }

    @Test
    fun measuresTheRateSinceThePreviousReport() {
        clock = 500
        progress.report(mb, 8 * mb)
        clock = 1000
        assertEquals("25% · 2.0 MB/s", progress.report(2 * mb, 8 * mb))
    }

    @Test
    fun showsOnlyTheRateWhenTheSizeIsUnknown() {
        clock = 1000
        assertEquals("1.0 MB/s", progress.report(mb, -1))
    }
}
