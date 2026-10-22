package de.codevoid.motolauncher

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.motolauncher.data.CellularStore
import de.codevoid.motolauncher.data.SpeedStore
import de.codevoid.motolauncher.ui.StatusBarView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowWifiManager

/**
 * What a device would otherwise be the only way to check: how a platform signal rating maps
 * onto the five icon states, that the resume refresh re-reads the clock and the battery when
 * the bar comes back into view, and that the pre-Android-11 paths run on Android 9.
 *
 * On the first: `WifiManager.calculateSignalLevel` rates a signal in `[0, maxSignalLevel]`
 * inclusive, so a device reporting a maximum of 4 has five ratings, not four. Treating the
 * top rating as out of range is what made a 3-of-4 signal draw full bars.
 */
@RunWith(RobolectricTestRunner::class)
class StatusBarViewTest {

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    private fun statusBar() = StatusBarView(app)

    private fun attachedStatusBar(): StatusBarView = statusBar().also {
        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(it)
    }

    // ShadowWifiManager keeps the legacy rating in a static that it never resets.
    @After
    fun resetLegacyWifiRating() = ShadowWifiManager.setSignalLevelInPercent(1f)

    private fun StatusBarView.timeText() = findViewById<TextView>(R.id.timeText)

    private fun StatusBarView.batteryText() = findViewById<TextView>(R.id.batteryText)

    private fun StatusBarView.speedText() = findViewById<TextView>(R.id.speedText)

    private fun StatusBarView.wifiIcon() = findViewById<ImageView>(R.id.wifiIcon)

    private fun StatusBarView.cellularIcon() = findViewById<ImageView>(R.id.cellularIcon)

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

    @Test
    @Suppress("DEPRECATION") // sendStickyBroadcast is how a test plants one.
    fun readsTheBatteryWhenTheBarComesBackIntoView() {
        // What a change broadcast missed while the device slept would leave behind: the
        // level from before it went on charge.
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 42)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_PLUGGED, 0)
        )

        val bar = statusBar()
        bar.batteryText().text = STALE
        bar.dispatchWindowVisibilityChanged(View.VISIBLE)

        // Format is the caller's business; that the sticky read landed is this test's.
        assertTrue("left showing '${bar.batteryText().text}'", bar.batteryText().text.contains("42"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun gpsSpeedSurvivesTheProviderBeingSwitchedOffOnAndroid9() {
        SpeedStore(app).enabled = true
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val bar = attachedStatusBar()

        // On then off: each change calls a provider callback that LocationListener
        // leaves abstract on Android 9.
        val locationManager = shadowOf(app.getSystemService(LocationManager::class.java))
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, bar.speedText().visibility)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun wifiBarsUseTheFiveStepRatingOnAndroid9() {
        // The shadow's static rating ignores the RSSI and returns this share of the scale:
        // half of five steps is rating 2.
        ShadowWifiManager.setSignalLevelInPercent(0.5f)
        val bar = attachedStatusBar()

        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        shadowOf(connectivity).networkCallbacks.forEach {
            it.onCapabilitiesChanged(ShadowNetwork.newInstance(1), ShadowNetworkCapabilities.newInstance())
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(2, bar.wifiIcon().drawable.level)
    }

    @Test
    fun cellularBarsNeedNoPermission() {
        CellularStore(app).enabled = true
        val bar = attachedStatusBar()
        assertEquals(View.VISIBLE, bar.cellularIcon().visibility)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    @Suppress("DEPRECATION") // getEventFlags reports the PhoneStateListener subscription.
    fun cellularBarsSubscribeAndUnsubscribeOnAndroid9() {
        CellularStore(app).enabled = true
        val bar = attachedStatusBar()
        val telephony = shadowOf(app.getSystemService(TelephonyManager::class.java))
        assertEquals(View.VISIBLE, bar.cellularIcon().visibility)
        assertEquals(PhoneStateListener.LISTEN_SIGNAL_STRENGTHS, telephony.eventFlags)

        CellularStore(app).enabled = false
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, bar.cellularIcon().visibility)
        assertEquals(PhoneStateListener.LISTEN_NONE, telephony.eventFlags)
    }

    private companion object {
        /** Cannot match [HH_MM] or a percentage: the assertion has to prove a real read. */
        const val STALE = "--:--"
        val HH_MM = Regex("\\d{2}:\\d{2}")
    }
}
