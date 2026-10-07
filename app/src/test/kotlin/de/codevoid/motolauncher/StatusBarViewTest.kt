package de.codevoid.motolauncher

import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.ui.StatusBarView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two things about the status bar that a device would otherwise be the only way to check:
 * how a platform signal rating maps onto the five icon states, and that the clock is
 * re-read when the bar comes back into view.
 *
 * On the first: `WifiManager.calculateSignalLevel` rates a signal in `[0, maxSignalLevel]`
 * inclusive, so a device reporting a maximum of 4 has five ratings, not four. Treating the
 * top rating as out of range is what made a 3-of-4 signal draw full bars.
 */
@RunWith(RobolectricTestRunner::class)
class StatusBarViewTest {

    private fun statusBar() = StatusBarView(ApplicationProvider.getApplicationContext())

    private fun StatusBarView.timeText() = findViewById<TextView>(R.id.timeText)

    @Test
    fun mapsOneToOneOnAFiveRatingPlatform() {
        // The usual case: maxSignalLevel 4, ratings 0..4, icon states 0..4.
        for (rating in 0..4) {
            assertEquals(rating, StatusBarView.wifiIconLevel(rating, 4))
        }
    }

    @Test
    fun onlyThePlatformMaximumDrawsFullBars() {
        assertEquals(3, StatusBarView.wifiIconLevel(3, 4))
        assertEquals(4, StatusBarView.wifiIconLevel(4, 4))
    }

    @Test
    fun emptyOnlyForTheLowestRating() {
        assertEquals(0, StatusBarView.wifiIconLevel(0, 4))
        assertEquals(1, StatusBarView.wifiIconLevel(1, 4))
    }

    @Test
    fun aFinerPlatformScaleStillReachesBothEnds() {
        // Six ratings squeezed into five icon states: monotonic, full only at the top.
        assertEquals(0, StatusBarView.wifiIconLevel(0, 5))
        assertEquals(3, StatusBarView.wifiIconLevel(4, 5))
        assertEquals(4, StatusBarView.wifiIconLevel(5, 5))
    }

    @Test
    fun aCoarserPlatformScaleSkipsIconStatesRatherThanMisreporting() {
        // Four ratings can't fill five states; the gap is lost resolution, and full bars
        // still mean the platform's own maximum.
        assertEquals(0, StatusBarView.wifiIconLevel(0, 3))
        assertEquals(1, StatusBarView.wifiIconLevel(1, 3))
        assertEquals(2, StatusBarView.wifiIconLevel(2, 3))
        assertEquals(4, StatusBarView.wifiIconLevel(3, 3))
    }

    @Test
    fun ratingsOutsideTheReportedRangeAreClamped() {
        assertEquals(0, StatusBarView.wifiIconLevel(-1, 4))
        assertEquals(4, StatusBarView.wifiIconLevel(9, 4))
        // A platform claiming no range at all must not divide by zero.
        assertEquals(4, StatusBarView.wifiIconLevel(1, 0))
        assertEquals(0, StatusBarView.wifiIconLevel(0, 0))
    }

    @Test
    fun readsTheClockWhenTheBarComesBackIntoView() {
        val bar = statusBar()
        // The minute left on screen when the bar went away. Deliberately unlike a clock,
        // so a test that never refreshed cannot pass by looking plausible.
        bar.timeText().text = STALE
        bar.dispatchWindowVisibilityChanged(View.VISIBLE)
        assertTrue("left showing '${bar.timeText().text}'", HH_MM.matches(bar.timeText().text))
    }

    @Test
    fun leavingViewDoesNotTouchTheClock() {
        // The refresh belongs on the way in. On the way out there is nobody to read it,
        // and the GPS listener is the only thing that should be reacting.
        val bar = statusBar()
        bar.timeText().text = STALE
        bar.dispatchWindowVisibilityChanged(View.INVISIBLE)
        assertEquals(STALE, bar.timeText().text.toString())
    }

    private companion object {
        /** Cannot match [HH_MM]: the assertion has to prove a real clock was written. */
        const val STALE = "--:--"
        val HH_MM = Regex("\\d{2}:\\d{2}")
    }
}
