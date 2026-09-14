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
import de.codevoid.motolauncher.ui.noTransition

/**
 * The park lock: a manual, PIN-guarded screen for short unattended stops.
 *
 * It lives in its own task (singleInstance + taskAffinity) rather than being a mode of
 * HomeActivity, because HomeActivity is singleTask and the root of the home task — a Home
 * press routes through onNewIntent and clears everything above the root, which would take
 * a park screen stacked there with it. Its own task is also what makes lock task mode
 * applicable at all.
 *
 * Lock task mode (screen pinning, without device owner) is what blocks Recents and the
 * notification shade. An app pinning *itself* is not asked to confirm — the confirmation
 * dialog belongs to pinning started from Recents — so parking is a single tap. It is
 * still best effort: a device with screen pinning switched off refuses it and the park
 * screen degrades to a deterrent against a stray tap. That difference is deliberately not
 * shown — it is a technical detail, and the screen is the same lock either way.
 *
 * The system's own unpin gesture (hold Back + Recents) cannot be blocked, so it is
 * *undone* instead: while parked, a guard polls `lockTaskModeState` and re-pins within a
 * few hundred milliseconds. Only the correct PIN disarms it. The consequence is worth
 * being clear about — with the guard, the PIN is the only way out short of adb or
 * reinstalling, so a forgotten PIN strands the device.
 *
 * Even unpinned, the Home gesture is covered: it starts the home app, and HomeActivity
 * re-launches this screen while ParkStore.isParked is set.
 */
class ParkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkBinding
    private val store by lazy { ParkStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }

    // Read from the current intent, not cached: this activity is singleInstance, so a
    // later lockIntent arrives at the same instance through onNewIntent. An abandoned
    // set-PIN screen would otherwise come back as set-PIN when the user parks.
    private val setMode get() = intent.getBooleanExtra(EXTRA_SET_PIN, false)

    // Cached: looked up several times a second by the guard, and fixed for the life of
    // the activity.
    private val activityManager by lazy { getSystemService(ActivityManager::class.java) }

    private var lastLockTaskRequestAt = 0L
    // Set when a request demonstrably did not take *and* pinning has never worked on this
    // screen. Screen pinning being switched off is a device setting, so retrying it
    // several times a second buys nothing and costs a failed Binder call plus a caught
    // exception each time. Gated on everPinned so that a re-pin which merely takes longer
    // than the settle window cannot disarm the guard on a device where pinning does work —
    // that would reopen the unpin hatch silently. Cleared on focus gain, which is how
    // switching the setting on and coming back is picked up.
    private var lockTaskUnavailable = false
    private var everPinned = false

    // Polling is the only way to notice an unpin: there is no callback for it outside a
    // device-owner DeviceAdminReceiver. Armed only while this screen is resumed and
    // parked, so a parked device with the screen off polls nothing.
    private val lockTaskGuard = object : Runnable {
        override fun run() {
            // Disarmed: stop rearming rather than keep polling a screen on its way out.
            if (setMode || isFinishing || !store.isParked || lockTaskUnavailable) return
            // One lockTaskModeState read per pass: it is a Binder round trip.
            requestLockTask(readLockTaskState())
            binding.root.postDelayed(this, GUARD_INTERVAL_MS)
        }
    }

    // Runs once the system server has settled after a request, so it sees the real state
    // rather than the stale NONE a read straight after the request returns. A field rather
    // than a fresh lambda per request: this way disarming cancels it too, instead of
    // leaving callbacks that outlive the screen they were posted from.
    private val settleCheck = Runnable {
        if (isFinishing || setMode) return@Runnable
        if (!readLockTaskState() && !everPinned) {
            lockTaskUnavailable = true
            disarmLockTaskGuard()
        }
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
        // Both bars stay hidden here regardless of the NavBarStore setting: a visible
        // navigation bar next to a lock screen is an invitation.
        window.enableImmersiveMode(showNavBar = false)

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
        // The parked flag is this screen's own invariant — "only this may be in front" —
        // so it is set here rather than by whoever launched us. No entry point has to
        // remember a protocol, and HomeActivity's restore path is simply re-entering here.
        if (!setMode) store.isParked = true
        // Pinning is requested here, not in onCreate: startLockTask needs a resumed
        // activity, which also covers the restore-after-reboot path.
        requestLockTask(readLockTaskState())
        render()
        armLockTaskGuard()
    }

    override fun onPause() {
        disarmLockTaskGuard()
        super.onPause()
    }

    // A second re-assert point alongside the guard, for the case where focus returns
    // before the next poll is due — and the one event-driven retry after polling gave up,
    // which is what picks up screen pinning being switched on and the user coming back.
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
            // Order matters: the guard stops and the parked flag clears before the pin is
            // released, so no in-flight guard run can re-pin what the PIN just opened.
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
            !setMode -> getString(R.string.park_enter_pin)
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

    /**
     * The single place that asks for lock task mode — onResume, focus gain and the guard
     * all come through here, so they cannot drift apart. Takes the current state rather
     * than reading it, so one Binder round trip serves the whole pass.
     */
    private fun requestLockTask(lockTaskActive: Boolean) {
        if (!shouldRequestLockTask(
                parked = store.isParked,
                setMode = setMode,
                finishing = isFinishing,
                lockTaskActive = lockTaskActive,
                sinceLastRequestMs = SystemClock.elapsedRealtime() - lastLockTaskRequestAt,
            )
        ) {
            return
        }
        lastLockTaskRequestAt = SystemClock.elapsedRealtime()
        runCatching { startLockTask() }
        // The system server updates lockTaskModeState asynchronously, so reading it
        // straight after a request still reports NONE, so the guard would fire a second
        // request, and a second system toast, into the same window. Hence both the settle
        // check and the sinceLastRequestMs term in shouldRequestLockTask.
        binding.root.removeCallbacks(settleCheck)
        binding.root.postDelayed(settleCheck, LOCK_TASK_SETTLE_MS)
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

    // Best effort like the request: stopLockTask throws if it was never entered, which is
    // the normal case on a device with screen pinning switched off.
    private fun exitLockTask() {
        if (!isLockTaskActive()) return
        runCatching { stopLockTask() }
    }

    companion object {
        private const val EXTRA_SET_PIN = "set_pin"
        private const val LOCK_TASK_SETTLE_MS = 400L
        private const val GUARD_INTERVAL_MS = 300L

        /**
         * Whether the guard should ask for lock task mode again. Pure, so the single place
         * that decides to re-pin is unit-testable without a device.
         *
         * `sinceLastRequestMs` keeps the guard off its own toes: lockTaskModeState lags a
         * successful request, so without it the first poll after parking would fire a
         * redundant second request.
         */
        fun shouldRequestLockTask(
            parked: Boolean,
            setMode: Boolean,
            finishing: Boolean,
            lockTaskActive: Boolean,
            sinceLastRequestMs: Long,
        ): Boolean = parked && !setMode && !finishing && !lockTaskActive &&
            sinceLastRequestMs >= LOCK_TASK_SETTLE_MS

        /** Shows the keypad, marks the launcher parked and asks for pinning. */
        fun lockIntent(context: Context) = Intent(context, ParkActivity::class.java)

        /** Enter-twice PIN setup. Never pins: setup happens at a desk, not at a kerb. */
        fun setPinIntent(context: Context) =
            Intent(context, ParkActivity::class.java).putExtra(EXTRA_SET_PIN, true)
    }
}
