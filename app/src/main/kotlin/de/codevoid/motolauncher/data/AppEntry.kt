package de.codevoid.motolauncher.data

import android.graphics.drawable.Drawable

data class AppEntry(
    val key: ComponentKey,
    val label: String,
    val icon: Drawable,
)
