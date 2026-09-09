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
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import de.codevoid.motolauncher.databinding.ViewStatusBarBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Self-managed status bar: registers its own system broadcasts and telephony callbacks in
// onAttachedToWindow, releases them in onDetachedFromWindow. HomeActivity owns no
// lifecycle wiring for this — the view manages its own data streams.
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

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateTime()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = applyBatteryIntent(intent)
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val info = caps.transportInfo as? WifiInfo
            post { updateWifi(info?.rssi) }
        }
        override fun onLost(network: Network) {
            post { updateWifi(null) }
        }
    }

    private var signalCallback: TelephonyCallback? = null

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        updateTime()
        context.registerReceiver(timeReceiver, IntentFilter(Intent.ACTION_TIME_TICK))

        // Sticky broadcast: registerReceiver returns the current state synchronously,
        // so the initial percentage is available immediately without waiting for a change.
        val initialBattery = context.registerReceiver(
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        initialBattery?.let(::applyBatteryIntent)

        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(wifiRequest, networkCallback)
        updateWifi(null)

        registerCellular()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching { context.unregisterReceiver(timeReceiver) }
        runCatching { context.unregisterReceiver(batteryReceiver) }
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
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
        binding.batteryText.text = context.getString(
            de.codevoid.motolauncher.R.string.battery_percent,
            level * 100 / scale,
        )
    }

    private fun updateWifi(rssi: Int?) {
        val level = if (rssi == null) 0 else scaleWifiLevel(rssi)
        binding.wifiIcon.setImageLevel(level)
    }

    // WifiManager.calculateSignalLevel(rssi) returns 0..maxSignalLevel-1 (typically 0..3
    // or 0..4). Rescale to our 5-step icon (0..4) so the "full bars" drawable is reachable
    // regardless of what the platform reports as its maximum.
    private fun scaleWifiLevel(rssi: Int): Int {
        val raw = wifiManager.calculateSignalLevel(rssi)
        val steps = (wifiManager.maxSignalLevel - 1).coerceAtLeast(1)
        return ((raw * 4) / steps).coerceIn(0, 4)
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
                binding.cellularIcon.setImageLevel(signalStrength.level.coerceIn(0, 4))
            }
        }
        signalCallback = cb
        tm.registerTelephonyCallback(context.mainExecutor, cb)
    }
}
