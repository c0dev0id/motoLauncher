package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import de.codevoid.motolauncher.R
import de.codevoid.motolauncher.apps.AppEntry
import de.codevoid.motolauncher.databinding.DialogTileActionsBinding

/** The touch long-press menu of an app tile. Returns the shown dialog. */
fun showTileActionsDialog(
    context: Context,
    entry: AppEntry,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemove: (() -> Unit)? = null,
    hideAction: Pair<Int, () -> Unit>? = null,
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
    if (hideAction != null) {
        binding.actionHide.setText(hideAction.first)
        binding.actionHide.setOnClickListener { dialog.dismiss(); hideAction.second() }
    } else {
        binding.actionHide.visibility = View.GONE
    }
    if (onRemove != null) {
        binding.actionRemove.setOnClickListener {
            dialog.dismiss()
            onRemove()
        }
    } else {
        binding.actionRemove.visibility = View.GONE
    }
    binding.actionEditLink.visibility = View.GONE
    binding.actionRemoveLink.visibility = View.GONE
    dialog.showImmersive()
    return dialog
}

fun showLinkActionsDialog(
    context: Context,
    label: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
): Dialog {
    val binding = DialogTileActionsBinding.inflate(LayoutInflater.from(context))
    binding.appIcon.setImageResource(R.drawable.ic_link)
    binding.appLabel.text = label
    binding.actionAppInfo.visibility = View.GONE
    binding.actionUninstall.visibility = View.GONE
    binding.actionHide.visibility = View.GONE
    binding.actionRemove.visibility = View.GONE

    val dialog = Dialog(context, R.style.Theme_MotoLauncher_Dialog)
    dialog.setContentView(binding.root)
    binding.actionEditLink.setOnClickListener { dialog.dismiss(); onEdit() }
    binding.actionRemoveLink.setOnClickListener { dialog.dismiss(); onRemove() }
    dialog.showImmersive()
    return dialog
}
