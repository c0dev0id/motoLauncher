package de.codevoid.motolauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Lock task mode (screen pinning, the user-confirmed variant — no device owner) is what
 * blocks Recents and the notification shade. It is best effort: if the device has screen
 * pinning disabled, startLockTask does nothing and the park screen degrades to a
 * deterrent against a stray tap. The status line says which of the two you have, because
 * the difference matters and is otherwise invisible.
 *
 * Even unpinned, the Home gesture is covered: it starts the home app, and HomeActivity
 * re-launches this screen while ParkStore.isParked is set.
 */
class ParkActivity : AppCompatActivity() {

    private lateinit var binding: ActivityParkBinding
    private val store by lazy { ParkStore(this) }
    private val orientationStore by lazy { OrientationStore(this) }

    private val setMode by lazy { intent.getBooleanExtra(EXTRA_SET_PIN, false) }

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

        digitKeys().forEach { (view, digit) -> view.setOnClickListener { onDigit(digit) } }
        binding.keyClear.setOnClickListener { entered.clear(); render() }
        binding.keyDelete.setOnClickListener {
            if (entered.isNotEmpty()) entered.setLength(entered.length - 1)
            render()
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        setRequestedOrientation(orientationStore.orientation)
        // Pinning is requested here, not in onCreate: startLockTask needs a resumed
        // activity, which also covers the restore-after-reboot path.
        if (!setMode) enterLockTask()
        render()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.enableImmersiveMode(showNavBar = false)
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
            firstEntry == null -> getString(R.string.park_new_pin)
            else -> getString(R.string.park_repeat_pin)
        }
        binding.parkDots.text = buildString {
            repeat(ParkStore.PIN_LENGTH) { append(if (it < entered.length) '●' else '○') }
        }
        binding.parkStatus.text = when {
            setMode -> ""
            isLockTaskActive() -> getString(R.string.park_pinned)
            else -> getString(R.string.park_not_pinned)
        }
    }

    private fun isLockTaskActive(): Boolean =
        getSystemService(ActivityManager::class.java)?.lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE

    // Both calls are best effort: the system refuses lock task mode outright on a device
    // with screen pinning switched off, and stopLockTask throws if it was never entered.
    private fun enterLockTask() {
        if (isLockTaskActive()) return
        runCatching { startLockTask() }
    }

    private fun exitLockTask() {
        if (!isLockTaskActive()) return
        runCatching { stopLockTask() }
    }

    companion object {
        private const val EXTRA_SET_PIN = "set_pin"

        /** Shows the keypad and asks for pinning. The caller sets ParkStore.isParked. */
        fun lockIntent(context: Context) = Intent(context, ParkActivity::class.java)

        /** Enter-twice PIN setup. Never pins: setup happens at a desk, not at a kerb. */
        fun setPinIntent(context: Context) =
            Intent(context, ParkActivity::class.java).putExtra(EXTRA_SET_PIN, true)
    }
}
