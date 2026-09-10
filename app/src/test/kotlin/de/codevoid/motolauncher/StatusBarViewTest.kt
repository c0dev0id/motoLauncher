package de.codevoid.motolauncher

import de.codevoid.motolauncher.ui.StatusBarView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `WifiManager.calculateSignalLevel` rates a signal in `[0, maxSignalLevel]` inclusive, so
 * a device reporting a maximum of 4 has five ratings, not four. Treating the top rating as
 * out of range is what made a 3-of-4 signal draw full bars.
 */
@RunWith(RobolectricTestRunner::class)
class StatusBarViewTest {

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
}
