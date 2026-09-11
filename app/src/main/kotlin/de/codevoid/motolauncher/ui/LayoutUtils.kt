package de.codevoid.motolauncher.ui

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity

val AppCompatActivity.isPortrait: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
