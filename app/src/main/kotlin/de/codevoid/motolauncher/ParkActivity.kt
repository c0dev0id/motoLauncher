package de.codevoid.motolauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import de.codevoid.motolauncher.data.OrientationStore
import de.codevoid.motolauncher.data.ParkStore
import de.codevoid.motolauncher.databinding.ActivityParkBinding
import de.codevoid.motolauncher.ui.enableImmersiveMode
import de.codevoid.motolauncher.ui.fitContentToSystemBars
import de.codevoid.motolauncher.ui.noTransition

/**
 * The park lock: a PIN keypad in its own task that pins the screen, and re-pins it after
 * the system unpin gesture, until the correct PIN is entered. Opened with [setPinIntent] it
 * is the enter-twice PIN setup instead, which never pins.
 */
class ParkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkBinding
    private val store by lazy { ParkStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }

    private val setMode get() = intent.getBooleanExtra(EXTRA_SET_PIN, false)

    private val activityManager by lazy { getSystemService(ACTIVITY_SERVICE) as ActivityManager }

    private var lastLockTaskRequestAt = 0L
    // Pin requests in a row that had not taken by their settle check.
    private var unansweredRequests = 0
    private val lockTaskUnavailable get() = unansweredRequests >= MAX_UNANSWERED_REQUESTS

    // Re-pins after the system unpin gesture while this screen is resumed and parked.
    private val lockTaskGuard = object : Runnable {
        override fun run() {
            if (setMode || isFinishing || !store.isParked || lockTaskUnavailable) return
            requestLockTask()
            binding.root.postDelayed(this, GUARD_INTERVAL_MS)
        }
    }

    // Judges the last pin request once the system has had time to apply it.
    private val settleCheck = Runnable {
        unansweredRequests = if (isLockTaskActive()) 0 else unansweredRequests + 1
    }

    private val entered = StringBuilder()
    // Set mode only: the first of the two entries, held until the repeat confirms it.
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
        if (!setMode) store.isParked = true
        requestLockTask()
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
        unansweredRequests = 0
        requestLockTask()
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
        if (setMode) submitSetPin(pin) else submitUnlock(pin)
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
            stopLockTask()
            finish()
            noTransition()
        } else {
            message = getString(R.string.park_wrong_pin)
            render()
        }
    }

    private fun render() {
        binding.parkPrompt.text = message ?: when {
            !setMode -> getString(R.string.park_enter_pin)
            firstEntry == null ->
                getString(if (store.hasPin) R.string.park_change_pin else R.string.park_new_pin)
            else -> getString(R.string.park_repeat_pin)
        }
        binding.parkDots.text = buildString {
            repeat(ParkStore.PIN_LENGTH) { append(if (it < entered.length) '●' else '○') }
        }
    }

    private fun isLockTaskActive(): Boolean =
        activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    private fun requestLockTask() {
        if (!ParkLockRules.shouldRequestLockTask(
                parked = store.isParked,
                setMode = setMode,
                finishing = isFinishing,
                lockTaskActive = isLockTaskActive(),
                sinceLastRequestMs = SystemClock.elapsedRealtime() - lastLockTaskRequestAt,
            )
        ) {
            return
        }
        lastLockTaskRequestAt = SystemClock.elapsedRealtime()
        runCatching { startLockTask() }
        binding.root.removeCallbacks(settleCheck)
        binding.root.postDelayed(settleCheck, ParkLockRules.LOCK_TASK_SETTLE_MS)
    }

    private fun armLockTaskGuard() {
        if (setMode || lockTaskUnavailable) return
        binding.root.removeCallbacks(lockTaskGuard)
        binding.root.postDelayed(lockTaskGuard, GUARD_INTERVAL_MS)
    }

    private fun disarmLockTaskGuard() {
        binding.root.removeCallbacks(lockTaskGuard)
        binding.root.removeCallbacks(settleCheck)
    }

    companion object {
        private const val EXTRA_SET_PIN = "set_pin"
        private const val GUARD_INTERVAL_MS = 300L
        private const val MAX_UNANSWERED_REQUESTS = 3

        /** Shows the keypad, marks the launcher parked and asks for pinning. */
        fun lockIntent(context: Context) = Intent(context, ParkActivity::class.java)

        /** Enter-twice PIN setup; never pins. */
        fun setPinIntent(context: Context) =
            Intent(context, ParkActivity::class.java).putExtra(EXTRA_SET_PIN, true)
    }
}
