package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.data.AppRepository
import de.codevoid.motolauncher.data.ComponentKey
import de.codevoid.motolauncher.data.getComponentKey
import de.codevoid.motolauncher.data.putComponentKey
import de.codevoid.motolauncher.databinding.DialogOptionsBinding

/**
 * A titled list of tile-styled rows on the launcher's dialog theme: the tile menus and the
 * pickers for multi-valued settings. A tap closes it and reports the row's id, together
 * with the caller's extras, as a fragment result under the request key; Back or Escape
 * closes it without one. Everything it shows comes from its arguments, so it survives a
 * recreate of the activity.
 */
class MenuDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val context = requireContext()
        val inflater = LayoutInflater.from(context)
        val binding = DialogOptionsBinding.inflate(inflater)
        val dialog = Dialog(context, R.style.Theme_MotoLauncher_Dialog)

        binding.optionsTitle.text = args.getString(ARG_TITLE)
        val iconRes = args.getInt(ARG_ICON)
        val app = args.getComponentKey(ARG_APP)
        val icon = when {
            iconRes != 0 -> ContextCompat.getDrawable(context, iconRes)
            app != null -> AppRepository(context).loadByKeys(listOf(app))[app]?.icon
            else -> null
        }
        binding.optionsIcon.setImageDrawable(icon)
        binding.optionsIcon.isVisible = icon != null

        val ids = requireNotNull(args.getIntArray(ARG_IDS))
        val labels = requireNotNull(args.getStringArrayList(ARG_LABELS))
        val selected = args.getInt(ARG_SELECTED, NONE)
        labels.forEachIndexed { index, label ->
            val row = inflater.inflate(R.layout.item_dialog_option, binding.optionRows, false) as TextView
            row.text = label
            row.isSelected = ids[index] == selected
            row.setOnClickListener {
                val result = Bundle(args.getBundle(ARG_EXTRAS)).apply { putInt(ROW, ids[index]) }
                parentFragmentManager.setFragmentResult(requireNotNull(args.getString(ARG_REQUEST)), result)
                dismiss()
            }
            binding.optionRows.addView(row)
        }

        dialog.setContentView(binding.root)
        dialog.hideSystemBars()
        return dialog
    }

    companion object {
        /** The result key holding the id of the row that was tapped. */
        const val ROW = "row"

        private const val ARG_REQUEST = "request"
        private const val ARG_TITLE = "title"
        private const val ARG_ICON = "icon"
        private const val ARG_APP = "app"
        private const val ARG_IDS = "ids"
        private const val ARG_LABELS = "labels"
        private const val ARG_SELECTED = "selected"
        private const val ARG_EXTRAS = "extras"
        private const val NONE = Int.MIN_VALUE

        /**
         * Shows [rows] (id to label) under [title], marking the row whose id is [selected].
         * The header shows [iconRes], or else [app]'s icon. Does nothing while a menu for
         * [requestKey] is already up.
         */
        fun show(
            fragmentManager: FragmentManager,
            requestKey: String,
            title: String,
            rows: List<Pair<Int, String>>,
            selected: Int? = null,
            @DrawableRes iconRes: Int = 0,
            app: ComponentKey? = null,
            extras: Bundle = Bundle(),
        ) {
            if (fragmentManager.findFragmentByTag(requestKey) != null) return
            MenuDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST, requestKey)
                    putString(ARG_TITLE, title)
                    putInt(ARG_ICON, iconRes)
                    app?.let { putComponentKey(ARG_APP, it) }
                    putIntArray(ARG_IDS, rows.map { it.first }.toIntArray())
                    putStringArrayList(ARG_LABELS, ArrayList(rows.map { it.second }))
                    putInt(ARG_SELECTED, selected ?: NONE)
                    putBundle(ARG_EXTRAS, extras)
                }
            }.show(fragmentManager, requestKey)
        }
    }
}

/** Menu rows labelled and identified by string resources, so a result names its row by resource. */
fun Context.menuRows(@StringRes vararg labels: Int): List<Pair<Int, String>> = labels.map { it to getString(it) }
