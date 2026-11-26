package de.codevoid.motolauncher

import android.os.Build

/** The park lock's decisions, kept free of Android state so they are testable without a device. */
object ParkLockRules {

    /** How long `lockTaskModeState` may lag a successful pin request. */
    const val LOCK_TASK_SETTLE_MS = 400L

    /** Whether the park screen should ask for lock task mode now. */
    fun shouldRequestLockTask(
        parked: Boolean,
        setMode: Boolean,
        finishing: Boolean,
        lockTaskActive: Boolean,
        sinceLastRequestMs: Long,
    ): Boolean = parked && !setMode && !finishing && !lockTaskActive &&
        sinceLastRequestMs >= LOCK_TASK_SETTLE_MS

    /**
     * How long the keypad takes no PIN after [failedAttempts] wrong ones in a row. The
     * first [FREE_ATTEMPTS] cost nothing; the pause then doubles from 30 seconds up to
     * ten minutes and never becomes a lockout.
     */
    fun retryDelayMs(failedAttempts: Int): Long =
        if (failedAttempts <= FREE_ATTEMPTS) 0L
        else (FIRST_RETRY_DELAY_MS shl (failedAttempts - FREE_ATTEMPTS - 1).coerceAtMost(5))
            .coerceAtMost(MAX_RETRY_DELAY_MS)

    const val FREE_ATTEMPTS = 4
    private const val FIRST_RETRY_DELAY_MS = 30_000L
    private const val MAX_RETRY_DELAY_MS = 600_000L

    /** Whether the Park lock settings tile is shown at all. */
    fun shouldOfferParkLock(sdkInt: Int, deviceSecure: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !deviceSecure
}
