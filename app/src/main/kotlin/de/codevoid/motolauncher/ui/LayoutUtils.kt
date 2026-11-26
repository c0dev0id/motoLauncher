package de.codevoid.motolauncher.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration

val Context.isPortrait: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

/**
 * Ask for [orientation] only when it is not what this activity already requested.
 *
 * The setter is a Binder call that makes the system re-evaluate the activity's
 * orientation, and under Sensor that means consulting the accelerometer again. Re-asserting
 * the value the activity already has buys nothing, and every activity here asserts it in
 * both `onCreate` and `onResume`.
 *
 * The manifest declares `fullSensor` on all three activities, so for the default setting
 * the value is already right at window creation and this skips entirely. It is still the
 * call that applies an explicitly chosen fixed orientation.
 */
fun Activity.applyOrientation(orientation: Int) {
    if (requestedOrientation != orientation) requestedOrientation = orientation
}
