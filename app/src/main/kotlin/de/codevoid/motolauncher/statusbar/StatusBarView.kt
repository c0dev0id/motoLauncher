package de.codevoid.motolauncher.statusbar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.databinding.ViewStatusBarBinding
import de.codevoid.motolauncher.ui.isPortrait
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home's top bar: time, GPS speed, Wi-Fi, cellular and battery, subscribed while attached. */
class StatusBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewStatusBarBinding =
        ViewStatusBarBinding.inflate(LayoutInflater.from(context), this)

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ROOT)

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val speedStore = SpeedStore(context)
    private val cellularStore = CellularStore(context)
    private val batteryStore = BatteryStore(context)
    private val settingsPrefs =
        context.getSharedPreferences(SpeedStore.PREFS, Context.MODE_PRIVATE)

    private val wifiMaxSignalLevel = wifiManager.maxSignalLevel

    // Written on the main thread, read on a Binder thread in onCapabilitiesChanged.
    @Volatile private var lastWifiLevel = -1
    private var lastBatteryPercent = -1
    private var lastBatteryIconLevel = -1
    private var lastCellularLevel = -1

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateTime()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBatteryIntent(intent)
    }

    // The meter drops to zero only when the last network is lost. ConnectivityManager runs
    // one callback's methods on a single thread.
    private val wifiNetworks = HashSet<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetworks.add(network)
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val rssi = (caps.transportInfo as? WifiInfo)?.rssi ?: return
            val level = rssiToIconLevel(rssi)
            if (level == lastWifiLevel) return
            post { applyWifiLevel(level) }
        }

        override fun onLost(network: Network) {
            if (!wifiNetworks.remove(network) || wifiNetworks.isNotEmpty()) return
            post { applyWifiLevel(0) }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> unregisterGps()
                Intent.ACTION_SCREEN_ON  -> {
                    refreshOnResume()
                    // The navigation app may still be in front of Home.
                    if (isShown) registerGps()
                }
            }
        }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)) {
                WifiManager.WIFI_STATE_ENABLED -> binding.wifiIcon.isVisible = true
                WifiManager.WIFI_STATE_DISABLED -> {
                    wifiNetworks.clear()
                    applyWifiLevel(0)
                    binding.wifiIcon.isVisible = false
                }
            }
        }
    }

    private var signalCallback: TelephonyCallback? = null

    private val locationListener = LocationListener { location ->
        if (!location.hasSpeed()) return@LocationListener
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
                if (binding.speedText.isVisible) {
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        updateTime()
        context.registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        context.registerReceiver(screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF).also { it.addAction(Intent.ACTION_SCREEN_ON) })

        // The battery broadcast is sticky: registering returns the current state.
        val initialBattery = context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        initialBattery?.let(::applyBatteryIntent)

        binding.wifiIcon.isVisible = wifiManager.isWifiEnabled
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(wifiRequest, networkCallback)

        registerCellular()

        settingsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        registerGps()
        applyBatteryDisplay()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeReceiver)
        context.unregisterReceiver(screenReceiver)
        context.unregisterReceiver(batteryReceiver)
        context.unregisterReceiver(wifiStateReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        wifiNetworks.clear()
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

    /** Re-reads the clock and the battery, which can go stale while the screen is off. */
    private fun refreshOnResume() {
        updateTime()
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let(::applyBatteryIntent)
    }

    private fun updateTime() {
        binding.timeText.text = timeFormat.format(Date())
    }

    private fun applyBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        // Five fill levels, plus five for charging: the level-list in ic_battery.xml.
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

    private fun rssiToIconLevel(rssi: Int): Int =
        wifiIconLevel(wifiManager.calculateSignalLevel(rssi), wifiMaxSignalLevel)

    private fun applyCellularLevel(level: Int) {
        if (level == lastCellularLevel) return
        lastCellularLevel = level
        binding.cellularIcon.setImageLevel(level)
    }

    private fun registerCellular() {
        binding.cellularIcon.isVisible = false
        if (!cellularStore.enabled) return
        val tm = telephonyManager
        if (tm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) return

        val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                applyCellularLevel(signalStrength.level.coerceIn(0, 4))
            }
        }
        signalCallback = cb
        tm.registerTelephonyCallback(context.mainExecutor, cb)
        applyCellularLevel(0)
        binding.cellularIcon.isVisible = true
    }

    private fun unregisterCellular() {
        binding.cellularIcon.isVisible = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            signalCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
        signalCallback = null
    }

    private fun registerGps() {
        binding.speedText.isVisible = false
        if (!speedStore.enabled) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, context.mainLooper
        )
        binding.speedText.text = "-- ${speedUnitString(speedStore.isMetric)}"
        binding.speedText.isVisible = true
    }

    private fun unregisterGps() {
        locationManager.removeUpdates(locationListener)
        binding.speedText.isVisible = false
    }

    private fun speedUnitString(metric: Boolean): String =
        context.getString(if (metric) R.string.units_kmh else R.string.units_mph)

    private fun applyBatteryDisplay() {
        val display = batteryStore.display
        binding.batteryIcon.isVisible = display != BatteryDisplay.TEXT
        binding.batteryText.isVisible = display != BatteryDisplay.ICON
    }

    companion object {
        // Our meters have five states, 0 (empty) to 4 (full).
        private const val MAX_ICON_LEVEL = 4

        /** Maps a platform Wi-Fi rating, `0..maxSignalLevel` inclusive, onto the icon's five states. */
        fun wifiIconLevel(rawLevel: Int, maxSignalLevel: Int): Int {
            val max = maxSignalLevel.coerceAtLeast(1)
            return rawLevel.coerceIn(0, max) * MAX_ICON_LEVEL / max
        }
    }
}
