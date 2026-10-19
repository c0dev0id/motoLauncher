package de.codevoid.motolauncher.applist

import android.graphics.drawable.Drawable

data class TileItem(
    val label: String,
    val subtitle: String? = null,
    val icon: Drawable? = null,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Boolean)? = null,
    val dimmed: Boolean = false,
)
