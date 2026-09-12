package de.codevoid.motolauncher.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import de.codevoid.motolauncher.R

fun Context.showLinkDialog(
    existingLabel: String = "",
    existingUrl: String = "",
    onConfirm: (label: String, url: String) -> Unit,
) {
    val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_link, null)
    val labelInput = view.findViewById<EditText>(R.id.linkLabel).apply { setText(existingLabel) }
    val urlInput = view.findViewById<EditText>(R.id.linkUrl).apply { setText(existingUrl) }
    AlertDialog.Builder(this)
        .setTitle(R.string.add_link)
        .setView(view)
        .setPositiveButton(R.string.ok) { _, _ ->
            val label = labelInput.text.toString().trim()
            val url = urlInput.text.toString().trim()
            if (label.isNotEmpty() && url.isNotEmpty()) onConfirm(label, url)
        }
        .setNegativeButton(R.string.cancel, null)
        .create()
        .showImmersive()
}
