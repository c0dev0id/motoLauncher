package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.data.AppEntry
import de.codevoid.motolauncher.databinding.DialogTileActionsBinding

// Touch long-press menu for an assigned home tile. A plain Dialog on the app's own
// dialog theme rather than an AlertDialog, so the surface keeps the launcher palette and
// tile-style buttons instead of the Material3 dialog look. Back / Escape dismiss it
// without choosing. Returns the shown dialog (tests use the handle).
fun showTileActionsDialog(
    context: Context,
    entry: AppEntry,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onReassign: () -> Unit,
): Dialog {
    val binding = DialogTileActionsBinding.inflate(LayoutInflater.from(context))
    binding.appIcon.setImageDrawable(entry.icon)
    binding.appLabel.text = entry.label

    val dialog = Dialog(context, R.style.Theme_MotoLauncher_Dialog)
    dialog.setContentView(binding.root)
    binding.actionAppInfo.setOnClickListener {
        dialog.dismiss()
        onAppInfo()
    }
    binding.actionUninstall.setOnClickListener {
        dialog.dismiss()
        onUninstall()
    }
    binding.actionReassign.setOnClickListener {
        dialog.dismiss()
        onReassign()
    }
    dialog.showImmersive()
    return dialog
}
