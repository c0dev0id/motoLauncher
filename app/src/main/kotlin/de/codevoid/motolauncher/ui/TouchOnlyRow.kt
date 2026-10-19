package de.codevoid.motolauncher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/** A row whose children take touch focus but are invisible to dpad traversal. */
class TouchOnlyRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
    }
}
