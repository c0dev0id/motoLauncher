package de.codevoid.motolauncher.settings

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.content.ContextCompat
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.databinding.DialogOptionsBinding
import de.codevoid.motolauncher.ui.showImmersive

/**
 * Shows [options] as tile-styled rows, [selectedIndex] highlighted, and reports the picked
 * index to [onPick]. Returns the shown dialog.
 */
fun showOptionsDialog(
    context: Context,
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onPick: (Int) -> Unit,
): Dialog {
    val inflater = LayoutInflater.from(context)
    val binding = DialogOptionsBinding.inflate(inflater)
    binding.optionsTitle.text = title

    val dialog = Dialog(context, R.style.Theme_MotoLauncher_Dialog)
    dialog.setContentView(binding.root)

    options.forEachIndexed { index, label ->
        val row = inflater.inflate(R.layout.item_dialog_option, binding.optionRows, false) as TextView
        row.text = label
        if (index == selectedIndex) {
            row.setTextColor(ContextCompat.getColor(context, R.color.tile_focused))
        }
        row.setOnClickListener {
            dialog.dismiss()
            onPick(index)
        }
        binding.optionRows.addView(row)
    }

    dialog.showImmersive()
    return dialog
}
