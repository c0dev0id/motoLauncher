package de.codevoid.motolauncher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class AppEntry(
    val component: ComponentName,
    val label: String,
    val icon: Drawable,
)
