package de.codevoid.motolauncher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.data.BatteryDisplay
import de.codevoid.motolauncher.data.BatteryStore
import de.codevoid.motolauncher.data.CellularStore
import de.codevoid.motolauncher.data.SpeedStore
import de.codevoid.motolauncher.databinding.ViewStatusBarBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Self-contained: HomeActivity does not wire this view's lifecycle. Broadcast and
// callback registrations live in onAttachedToWindow / onDetachedFromWindow.
class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewStatusBarBinding =
        ViewStatusBarBinding.inflate(LayoutInflater.from(context), this)

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val speedStore = SpeedStore(context)
    private val cellularStore = CellularStore(context)
    private val batteryStore = BatteryStore(context)
    private val settingsPrefs =
        context.getSharedPreferences(SpeedStore.PREFS, Context.MODE_PRIVATE)

    // Cached once — maxSignalLevel is a fixed property that would otherwise cross the
    // Binder on every capability callback (which fires many times per second on a
    // fluctuating link). Before API 30 the rating is on the icon's own five steps.
    private val wifiMaxSignalLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wifiManager.maxSignalLevel
        else MAX_ICON_LEVEL

    private var lastWifiLevel = -1
    private var lastBatteryPercent = -1
    private var lastBatteryIconLevel = -1
    private var lastCellularLevel = -1

    // Carries the clock while the screen is on. The gap where it does not arrive is
    // covered by refreshOnResume, not here.
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateTime()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBatteryIntent(intent)
    }

    // Wi-Fi bars from RSSI, and zero bars once the last Wi-Fi network is lost; the icon
    // itself follows the radio (wifiStateReceiver). Its methods run on the connectivity
    // thread, so the network set is never touched elsewhere and every level is posted.
    // One instance per registration, so the set starts empty each time.
    private inner class WifiCallback : ConnectivityManager.NetworkCallback() {
        private val networks = HashSet<Network>()

        override fun onAvailable(network: Network) {
            networks.add(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (caps.transportInfo as? WifiInfo)?.rssi ?: return
            } else {
                @Suppress("DEPRECATION")
                wifiManager.connectionInfo.rssi
            }
            val level = rssiToIconLevel(rssi)
            post { applyWifiLevel(level) }
        }

        override fun onLost(network: Network) {
            if (networks.remove(network) && networks.isEmpty()) post { applyWifiLevel(0) }
        }
    }

    private var wifiCallback: WifiCallback? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> unregisterGps()
                Intent.ACTION_SCREEN_ON -> {
                    refreshOnResume()
                    registerGps()
                }
            }
        }
    }

    // WiFi radio on/off → show/hide the icon. Initial state is set from wifiManager.isWifiEnabled
    // in onAttachedToWindow; this receiver handles subsequent changes.
    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
                WifiManager.WIFI_STATE_ENABLED -> showWifiIcon(true)
                WifiManager.WIFI_STATE_DISABLED -> {
                    applyWifiLevel(0)
                    showWifiIcon(false)
                }
            }
        }
    }

    private var stopSignalUpdates: (() -> Unit)? = null

    // Compat: LocationListener's provider and status callbacks are abstract before API 30.
    private val locationListener = LocationListenerCompat { location ->
        if (!location.hasSpeed()) return@LocationListenerCompat
        val metric = speedStore.isMetric
        val displaySpeed = if (metric) (location.speed * 3.6).toInt() else (location.speed * 2.237).toInt()
        binding.speedText.text = "$displaySpeed ${speedUnitString(metric)}"
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            CellularStore.KEY_CELLULAR_ENABLED ->
                if (cellularStore.enabled) registerCellular() else unregisterCellular()
            SpeedStore.KEY_SPEED_ENABLED -> if (speedStore.enabled) registerGps() else unregisterGps()
            SpeedStore.KEY_SPEED_METRIC -> {
                // Next GPS fix (~1 s) will overwrite this; just update the unit in the placeholder.
                if (binding.speedText.visibility == View.VISIBLE) {
                    binding.speedText.text = "-- ${speedUnitString(speedStore.isMetric)}"
                }
            }
            BatteryStore.KEY_BATTERY_DISPLAY -> applyBatteryDisplay()
        }
    }

    init {
        orientation = if (context.isPortrait) VERTICAL else HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    // The clock, the battery and GPS start from onWindowVisibilityChanged, which attaching
    // sends right after this unless the window is gone.
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        context.registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        context.registerReceiver(screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).also { it.addAction(Intent.ACTION_SCREEN_ON) })
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // Seeded from the current radio state; wifiStateReceiver handles later changes.
        showWifiIcon(wifiManager.isWifiEnabled)
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        wifiCallback = WifiCallback().also { connectivityManager.registerNetworkCallback(wifiRequest, it) }

        registerCellular()

        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        applyBatteryDisplay()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeReceiver)
        context.unregisterReceiver(screenReceiver)
        context.unregisterReceiver(batteryReceiver)
        context.unregisterReceiver(wifiStateReceiver)
        wifiCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        wifiCallback = null
        unregisterCellular()

        settingsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        locationManager.removeUpdates(locationListener)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            refreshOnResume()
            registerGps()
        } else {
            unregisterGps()
        }
    }

    /** Re-reads what may have gone stale while nothing was delivered: the clock and the battery. */
    private fun refreshOnResume() {
        updateTime()
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let(::applyBatteryIntent)
    }

    private fun updateTime() {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        binding.timeText.text = SimpleDateFormat(pattern, Locale.ROOT).format(Date())
    }

    private fun applyBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        // 5 fill buckets [0..4] plus a +5 offset for the charging variants, matching the
        // 10-item level-list in ic_battery.xml.
        val bucket = (percent / 20).coerceIn(0, 4)
        val iconLevel = bucket + if (plugged) 5 else 0

        if (percent != lastBatteryPercent) {
            lastBatteryPercent = percent
            binding.batteryText.text = context.getString(R.string.battery_percent, percent)
        }
        if (iconLevel != lastBatteryIconLevel) {
            lastBatteryIconLevel = iconLevel
            binding.batteryIcon.setImageLevel(iconLevel)
        }
    }

    private fun applyWifiLevel(level: Int) {
        if (level == lastWifiLevel) return
        lastWifiLevel = level
        binding.wifiIcon.setImageLevel(level)
    }

    private fun showWifiIcon(connected: Boolean) {
        binding.wifiIcon.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun rssiToIconLevel(rssi: Int): Int {
        val rating = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.calculateSignalLevel(rssi)
        } else {
            @Suppress("DEPRECATION")
            WifiManager.calculateSignalLevel(rssi, MAX_ICON_LEVEL + 1)
        }
        return wifiIconLevel(rating, wifiMaxSignalLevel)
    }

    private fun applyCellularLevel(signalLevel: Int) {
        val level = signalLevel.coerceIn(0, MAX_ICON_LEVEL)
        if (level == lastCellularLevel) return
        lastCellularLevel = level
        binding.cellularIcon.setImageLevel(level)
    }

    private fun showCellularIcon(connected: Boolean) {
        binding.cellularIcon.visibility = if (connected) View.VISIBLE else View.GONE
    }

    // Reading the signal strength needs no permission.
    private fun registerCellular() {
        showCellularIcon(false)
        if (!cellularStore.enabled) return
        val tm = telephonyManager ?: return
        stopSignalUpdates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listenForSignal(tm)
        } else {
            listenForSignalBeforeS(tm)
        }
        applyCellularLevel(0)
        showCellularIcon(true)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listenForSignal(tm: TelephonyManager): () -> Unit {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) =
                applyCellularLevel(signalStrength.level)
        }
        tm.registerTelephonyCallback(context.mainExecutor, callback)
        return { tm.unregisterTelephonyCallback(callback) }
    }

    @Suppress("DEPRECATION")
    private fun listenForSignalBeforeS(tm: TelephonyManager): () -> Unit {
        val listener = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) =
                applyCellularLevel(signalStrength.level)
        }
        tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        return { tm.listen(listener, PhoneStateListener.LISTEN_NONE) }
    }

    private fun unregisterCellular() {
        showCellularIcon(false)
        stopSignalUpdates?.invoke()
        stopSignalUpdates = null
    }

    // Every path that starts GPS comes through here, so the one condition holds for all of
    // them: the bar can be seen and the screen is on.
    private fun registerGps() {
        binding.speedText.visibility = View.GONE
        if (!speedStore.enabled || windowVisibility != VISIBLE || !powerManager.isInteractive) return
        if (!context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LOCATION_GPS)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, context.mainLooper
        )
        binding.speedText.text = "-- ${speedUnitString(speedStore.isMetric)}"
        binding.speedText.visibility = View.VISIBLE
    }

    private fun unregisterGps() {
        locationManager.removeUpdates(locationListener)
        binding.speedText.visibility = View.GONE
    }

    private fun speedUnitString(metric: Boolean): String =
        context.getString(if (metric) R.string.units_kmh else R.string.units_mph)

    private fun applyBatteryDisplay() {
        when (batteryStore.display) {
            BatteryDisplay.BOTH -> { binding.batteryIcon.visibility = View.VISIBLE; binding.batteryText.visibility = View.VISIBLE }
            BatteryDisplay.ICON -> { binding.batteryIcon.visibility = View.VISIBLE; binding.batteryText.visibility = View.GONE }
            BatteryDisplay.TEXT -> { binding.batteryIcon.visibility = View.GONE;    binding.batteryText.visibility = View.VISIBLE }
        }
    }

    companion object {
        // Our meters have five states, 0 (empty) to 4 (full).
        private const val MAX_ICON_LEVEL = 4

        /**
         * Maps a platform Wi-Fi rating onto the icon's five states.
         *
         * `WifiManager.calculateSignalLevel` returns a rating in `[0, maxSignalLevel]`
         * *inclusive* — maxSignalLevel + 1 distinct values, five on a typical device.
         * Dividing by `maxSignalLevel` (not by one less, which treats the top rating as
         * out of range) keeps the mapping honest at both ends: only the platform's own
         * maximum draws full bars, and only a 0 rating draws none.
         *
         * A platform reporting fewer steps than the icon has simply skips icon states;
         * that is lost resolution, not a misreading.
         */
        fun wifiIconLevel(rawLevel: Int, maxSignalLevel: Int): Int {
            val max = maxSignalLevel.coerceAtLeast(1)
            return rawLevel.coerceIn(0, max) * MAX_ICON_LEVEL / max
        }
    }
}
