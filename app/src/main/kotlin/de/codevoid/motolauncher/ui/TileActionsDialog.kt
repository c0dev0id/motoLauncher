package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.databinding.DialogTileActionsBinding

// Touch long-press menu for an assigned home tile. Built on a plain Dialog rather than
// AlertDialog so the surface uses the launcher's own palette and tile-style buttons
// instead of the Material3 dialog theme. Back / Escape dismiss it without choosing.
object TileActionsDialog {

    fun create(
        context: Context,
        entry: AppEntry,
        onReassign: () -> Unit,
        onAppInfo: () -> Unit,
    ): Dialog {
        val binding = DialogTileActionsBinding.inflate(LayoutInflater.from(context))
        binding.appIcon.setImageDrawable(entry.icon)
        binding.appLabel.text = entry.label

        val dialog = Dialog(context)
        dialog.setContentView(binding.root)
        dialog.window?.apply {
            setBackgroundDrawableResource(R.drawable.dialog_background)
            enableImmersiveMode()
        }

        binding.actionReassign.setOnClickListener {
            dialog.dismiss()
            onReassign()
        }
        binding.actionAppInfo.setOnClickListener {
            dialog.dismiss()
            onAppInfo()
        }
        return dialog
    }
}
