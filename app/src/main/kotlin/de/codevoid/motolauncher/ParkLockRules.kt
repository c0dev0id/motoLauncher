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

    /** Whether the Park lock settings tile is shown at all. */
    fun shouldOfferParkLock(sdkInt: Int, deviceSecure: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !deviceSecure
}
