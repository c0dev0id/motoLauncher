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
    private val wifiMaxSignalLevel = wifiManager.maxSignalLevel

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

    // Which Wi-Fi networks are up, and therefore whether the icon belongs on screen at
    // all. An empty meter cannot say "no Wi-Fi" — it reads as "connected, no signal" —
    // so absence is shown by absence. ConnectivityManager serialises one callback's
    // methods onto a single thread, so plain set bookkeeping is enough here; more than
    // one Wi-Fi network at a time is unusual, but losing one of two must not hide an
    // indicator the other still earns.
    private val wifiNetworks = HashSet<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (wifiNetworks.add(network) && wifiNetworks.size == 1) post { showWifiIcon(true) }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            // Runs on a Binder thread. Classify cheaply here and only hop to the main
            // thread when the icon level actually changes.
            val rssi = (caps.transportInfo as? WifiInfo)?.rssi ?: return
            val level = scaleWifiLevel(rssi)
            if (level == lastWifiLevel) return
            post { applyWifiLevel(level) }
        }

        override fun onLost(network: Network) {
            if (!wifiNetworks.remove(network) || wifiNetworks.isNotEmpty()) return
            post {
                // Empty the meter while it is hidden, so reconnecting can't flash the old
                // strength in the gap before the first capabilities callback arrives.
                applyWifiLevel(0)
                showWifiIcon(false)
            }
        }
    }

    private var signalCallback: TelephonyCallback? = null

    // Same idea as the Wi-Fi set, for the mobile-data network. The meter is about data:
    // with mobile data switched off there is nothing for it to report, so it leaves the
    // bar rather than sitting at zero bars, which would read as "data on, no coverage".
    private val cellularNetworks = HashSet<Network>()
    private var cellularNetworkCallback: ConnectivityManager.NetworkCallback? = null

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

        // Re-attaching does not re-run the layout's starting visibility, and a network
        // that is already gone sends no onLost, so start hidden and let onAvailable show it.
        showWifiIcon(false)
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
        cellularNetworkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        cellularNetworkCallback = null
        // Re-registering on re-attach replays onAvailable for networks that are already
        // up; without this the sets would still hold them and the icons would stay hidden.
        wifiNetworks.clear()
        cellularNetworks.clear()
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

    private fun showWifiIcon(connected: Boolean) {
        binding.wifiIcon.visibility = if (connected) View.VISIBLE else View.GONE
    }

    private fun scaleWifiLevel(rssi: Int): Int =
        wifiIconLevel(wifiManager.calculateSignalLevel(rssi), wifiMaxSignalLevel)

    private fun applyCellularLevel(level: Int) {
        if (level == lastCellularLevel) return
        lastCellularLevel = level
        binding.cellularIcon.setImageLevel(level)
    }

    private fun showCellularIcon(connected: Boolean) {
        binding.cellularIcon.visibility = if (connected) View.VISIBLE else View.GONE
    }

    // Two independent facts decide the cellular meter, and both must hold: telephony has
    // to be able to report a signal level at all (API 31+ and READ_PHONE_STATE), and a
    // mobile-data network has to exist. The permission gate is checked once here; the
    // network gate is the callback below, which is only registered when the first gate
    // passes — so without the permission nothing is watched and the icon stays hidden.
    private fun registerCellular() {
        showCellularIcon(false)
        val tm = telephonyManager
        if (tm == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val cb = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                applyCellularLevel(signalStrength.level.coerceIn(0, 4))
            }
        }
        signalCallback = cb
        tm.registerTelephonyCallback(context.mainExecutor, cb)
        watchCellularNetwork()
    }

    private fun watchCellularNetwork() {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (cellularNetworks.add(network) && cellularNetworks.size == 1) {
                    post { showCellularIcon(true) }
                }
            }

            override fun onLost(network: Network) {
                if (!cellularNetworks.remove(network) || cellularNetworks.isNotEmpty()) return
                post {
                    // Empty the meter while it is hidden so switching data back on cannot
                    // flash the old strength before the first signal callback arrives.
                    applyCellularLevel(0)
                    showCellularIcon(false)
                }
            }
        }
        cellularNetworkCallback = callback
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                // NET_CAPABILITY_INTERNET is what makes this "mobile data" rather than
                // "the radio is on": many devices keep an IMS connection up for VoLTE with
                // data switched off, and that is a TRANSPORT_CELLULAR network too.
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )
    }

    companion object {
        // Our meters have five states, 0 (empty) to 4 (full).
        private const val ICON_LEVELS = 4

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
            return rawLevel.coerceIn(0, max) * ICON_LEVELS / max
        }
    }
}
