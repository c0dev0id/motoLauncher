package de.codevoid.motolauncher.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
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
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import de.codevoid.motolauncher.R
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

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.ROOT)

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val telephonyManager =
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    // Cached once — maxSignalLevel is a fixed property that would otherwise cross the
    // Binder on every capability callback (which fires many times per second on a
    // fluctuating link).
    private val wifiSignalSteps = (wifiManager.maxSignalLevel - 1).coerceAtLeast(1)

    private var lastWifiLevel = -1
    private var lastBatteryPercent = -1
    private var lastBatteryIconLevel = -1
    private var lastCellularLevel = -1

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateTime()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBatteryIntent(intent)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            // Runs on a Binder thread. Classify cheaply here and only hop to the main
            // thread when the icon level actually changes.
            val rssi = (caps.transportInfo as? WifiInfo)?.rssi ?: return
            val level = scaleWifiLevel(rssi)
            if (level == lastWifiLevel) return
            post { applyWifiLevel(level) }
        }
        override fun onLost(network: Network) {
            if (lastWifiLevel == 0) return
            post { applyWifiLevel(0) }
        }
    }

    private var signalCallback: TelephonyCallback? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        updateTime()
        context.registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

        // Sticky broadcast: registerReceiver returns the current state synchronously so
        // the initial percentage is available without waiting for a change event.
        val initialBattery = context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        initialBattery?.let(::applyBatteryIntent)

        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(wifiRequest, networkCallback)

        registerCellular()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(timeReceiver)
        context.unregisterReceiver(batteryReceiver)
        connectivityManager.unregisterNetworkCallback(networkCallback)
        // signalCallback is only ever set when SDK >= S (see registerCellular), but lint
        // needs the explicit check because it doesn't cross-reference the two call sites.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            signalCallback?.let { telephonyManager?.unregisterTelephonyCallback(it) }
        }
        signalCallback = null
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
        lastWifiLevel = level
        binding.wifiIcon.setImageLevel(level)
    }

    // WifiManager.calculateSignalLevel(rssi) returns 0..maxSignalLevel-1 (typically 0..3
    // or 0..4). Rescale to our 5-step icon (0..4) so the "full bars" drawable is reachable
    // regardless of what the platform reports as its maximum.
    private fun scaleWifiLevel(rssi: Int): Int {
        val raw = wifiManager.calculateSignalLevel(rssi)
        return ((raw * 4) / wifiSignalSteps).coerceIn(0, 4)
    }

    private fun registerCellular() {
        val tm = telephonyManager
        if (tm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            binding.cellularIcon.visibility = View.GONE
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            binding.cellularIcon.visibility = View.GONE
            return
        }
        binding.cellularIcon.visibility = View.VISIBLE
        val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                val level = signalStrength.level.coerceIn(0, 4)
                if (level == lastCellularLevel) return
                lastCellularLevel = level
                binding.cellularIcon.setImageLevel(level)
            }
        }
        signalCallback = cb
        tm.registerTelephonyCallback(context.mainExecutor, cb)
    }
}
