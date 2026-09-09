package de.codevoid.motolauncher.ui

import android.graphics.drawable.Drawable

data class TileItem(
    val label: String,
    val icon: Drawable,
    val onClick: () -> Unit = {},
    val onLongClick: () -> Boolean = { false },
)
