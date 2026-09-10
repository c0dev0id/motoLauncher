package de.codevoid.motolauncher.ui

import android.graphics.drawable.Drawable

data class TileItem(
    val label: String,
    val subtitle: String? = null,
    val icon: Drawable? = null,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Boolean)? = null,
)
