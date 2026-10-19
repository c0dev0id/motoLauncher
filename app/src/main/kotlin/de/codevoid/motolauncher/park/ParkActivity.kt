package de.codevoid.motolauncher.park

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.databinding.ActivityParkBinding
import de.codevoid.motolauncher.settings.OrientationStore
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import de.codevoid.motolauncher.ui.noTransition

/** The park lock: a PIN keypad that pins the screen for short unattended stops. */
class ParkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkBinding
    private val store by lazy { ParkStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }

    private val isSettingPin get() = intent.getBooleanExtra(EXTRA_SET_PIN, false)

    private val activityManager by lazy { getSystemService(ActivityManager::class.java) }

    private var lastLockTaskRequestAt = 0L
    // Set when a request failed and pinning has never worked here; cleared on focus gain.
    private var lockTaskUnavailable = false
    private var everPinned = false

    // Re-pins the screen after the system's unpin gesture.
    private val lockTaskGuard = object : Runnable {
        override fun run() {
            if (isSettingPin || isFinishing || !store.isParked || lockTaskUnavailable) return
            requestLockTask(readLockTaskState())
            binding.root.postDelayed(this, GUARD_INTERVAL_MS)
        }
    }

    // Checks, once lockTaskModeState has caught up with a request, whether it took.
    private val settleCheck = Runnable {
        if (isFinishing || isSettingPin) return@Runnable
        if (!readLockTaskState() && !everPinned) {
            lockTaskUnavailable = true
            disarmLockTaskGuard()
        }
    }

    private val entered = StringBuilder()
    // While setting a PIN: the first entry, until the repeat confirms it.
    private var firstEntry: String? = null
    private var message: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRequestedOrientation(orientationStore.orientation)
        binding = ActivityParkBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.enableImmersiveMode(showNavBar = false)
        window.fitContentToSystemBars()

        onBackPressedDispatcher.addCallback(this) { /* the whole point is not leaving */ }

        digitKeys().forEach { (view, digit) ->
            view.text = digit.toString()
            view.setOnClickListener { onDigit(digit) }
        }
        binding.keyClear.setOnClickListener { entered.clear(); render() }
        binding.keyDelete.setOnClickListener {
            if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
            render()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entered.clear()
        firstEntry = null
        message = null
        render()
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        if (!isSettingPin) store.isParked = true
        requestLockTask(readLockTaskState())
        render()
        armLockTaskGuard()
    }

    override fun onPause() {
        disarmLockTaskGuard()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        window.enableImmersiveMode(showNavBar = false)
        lockTaskUnavailable = false
        requestLockTask(readLockTaskState())
        armLockTaskGuard()
    }

    private fun digitKeys(): List<Pair<TextView, Char>> = listOf(
        binding.key0 to '0', binding.key1 to '1', binding.key2 to '2', binding.key3 to '3',
        binding.key4 to '4', binding.key5 to '5', binding.key6 to '6', binding.key7 to '7',
        binding.key8 to '8', binding.key9 to '9',
    )

    private fun onDigit(digit: Char) {
        if (entered.length >= ParkStore.PIN_LENGTH) return
        entered.append(digit)
        message = null
        if (entered.length == ParkStore.PIN_LENGTH) submit() else render()
    }

    private fun submit() {
        val pin = entered.toString()
        entered.clear()
        if (isSettingPin) submitSetPin(pin) else submitUnlock(pin)
    }

    private fun submitSetPin(pin: String) {
        val first = firstEntry
        when {
            first == null -> firstEntry = pin
            first == pin -> {
                store.setPin(pin)
                finish()
                noTransition()
                return
            }
            else -> {
                firstEntry = null
                message = getString(R.string.park_pin_mismatch)
            }
        }
        render()
    }

    private fun submitUnlock(pin: String) {
        if (store.verify(pin)) {
            disarmLockTaskGuard()
            store.isParked = false
            exitLockTask()
            finish()
            noTransition()
        } else {
            message = getString(R.string.park_wrong_pin)
            render()
        }
    }

    private fun render() {
        binding.parkPrompt.text = message ?: when {
            !isSettingPin -> getString(R.string.park_enter_pin)
            firstEntry == null ->
                getString(if (store.hasPin) R.string.park_change_pin else R.string.park_new_pin)
            else -> getString(R.string.park_repeat_pin)
        }
        binding.parkDots.text = buildString {
            repeat(ParkStore.PIN_LENGTH) { append(if (it < entered.length) '●' else '○') }
        }
    }

    private fun isLockTaskActive(): Boolean {
        val state = activityManager?.lockTaskModeState ?: ActivityManager.LOCK_TASK_MODE_NONE
        return state != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /** One Binder read, remembering that pinning has worked here at least once. */
    private fun readLockTaskState(): Boolean =
        isLockTaskActive().also { if (it) everPinned = true }

    /** The one place that asks for lock task mode, given the state just read. */
    private fun requestLockTask(lockTaskActive: Boolean) {
        if (!shouldRequestLockTask(
                parked = store.isParked,
                isSettingPin = isSettingPin,
                finishing = isFinishing,
                lockTaskActive = lockTaskActive,
                sinceLastRequestMs = SystemClock.elapsedRealtime() - lastLockTaskRequestAt,
            )
        ) {
            return
        }
        lastLockTaskRequestAt = SystemClock.elapsedRealtime()
        runCatching { startLockTask() }
        binding.root.removeCallbacks(settleCheck)
        binding.root.postDelayed(settleCheck, LOCK_TASK_SETTLE_MS)
    }

    private fun armLockTaskGuard() {
        if (isSettingPin || lockTaskUnavailable) return
        binding.root.removeCallbacks(lockTaskGuard)
        binding.root.postDelayed(lockTaskGuard, GUARD_INTERVAL_MS)
    }

    private fun disarmLockTaskGuard() {
        binding.root.removeCallbacks(lockTaskGuard)
        binding.root.removeCallbacks(settleCheck)
    }

    private fun exitLockTask() {
        if (!isLockTaskActive()) return
        runCatching { stopLockTask() }
    }

    companion object {
        private const val EXTRA_SET_PIN = "set_pin"
        private const val LOCK_TASK_SETTLE_MS = 400L
        private const val GUARD_INTERVAL_MS = 300L

        /** Whether to ask for lock task mode now, outside the settle window of the last request. */
        fun shouldRequestLockTask(
            parked: Boolean,
            isSettingPin: Boolean,
            finishing: Boolean,
            lockTaskActive: Boolean,
            sinceLastRequestMs: Long,
        ): Boolean = parked && !isSettingPin && !finishing && !lockTaskActive &&
            sinceLastRequestMs >= LOCK_TASK_SETTLE_MS

        /** Whether to offer the park lock: Android 15 or newer, and no secure lock screen. */
        fun shouldOfferParkLock(sdkInt: Int, deviceSecure: Boolean): Boolean =
            sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !deviceSecure

        /** Shows the keypad, marks the launcher parked and asks for pinning. */
        fun lockIntent(context: Context) = Intent(context, ParkActivity::class.java)

        /** Enter-twice PIN setup; never pins the screen. */
        fun setPinIntent(context: Context) =
            Intent(context, ParkActivity::class.java).putExtra(EXTRA_SET_PIN, true)
    }
}
