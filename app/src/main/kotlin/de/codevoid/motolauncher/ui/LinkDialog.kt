package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import de.codevoid.motolauncher.R

/**
 * Asks for a link's name and address. OK stays disabled until both are filled in and the
 * address is one Android can open, and reports the name, the address and the caller's
 * extras as a fragment result under the request key. It survives a recreate, typed text
 * included.
 */
class LinkDialog : DialogFragment() {

    private lateinit var labelInput: EditText
    private lateinit var urlInput: EditText

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_link, null)
        labelInput = view.findViewById(R.id.linkLabel)
        urlInput = view.findViewById(R.id.linkUrl)
        if (savedInstanceState == null) {
            labelInput.setText(args.getString(ARG_LABEL))
            urlInput.setText(args.getString(ARG_URL))
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (args.getBoolean(ARG_EDITING)) R.string.link_action_edit else R.string.add_link)
            .setView(view)
            .setPositiveButton(R.string.ok) { _, _ ->
                val result = Bundle(args.getBundle(ARG_EXTRAS)).apply {
                    putString(LABEL, labelInput.text.toString().trim())
                    putString(URL, normalizeLink(urlInput.text.toString()))
                }
                parentFragmentManager.setFragmentResult(requireNotNull(args.getString(ARG_REQUEST)), result)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        labelInput.doAfterTextChanged { updateOk() }
        urlInput.doAfterTextChanged { updateOk() }
        dialog.hideSystemBars()
        return dialog
    }

    // The buttons exist once super.onStart has shown the dialog.
    override fun onStart() {
        super.onStart()
        updateOk()
    }

    private fun updateOk() {
        (dialog as AlertDialog?)?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled =
            labelInput.text.isNotBlank() && normalizeLink(urlInput.text.toString()) != null
    }

    companion object {
        /** Result keys: the trimmed name and the address as [normalizeLink] returns it. */
        const val LABEL = "label"
        const val URL = "url"

        private const val ARG_REQUEST = "request"
        private const val ARG_LABEL = "label"
        private const val ARG_URL = "url"
        private const val ARG_EDITING = "editing"
        private const val ARG_EXTRAS = "extras"

        /** Opens the dialog filled with [label] and [url]; [editing] titles it "Edit link". */
        fun show(
            fragmentManager: FragmentManager,
            requestKey: String,
            label: String,
            url: String,
            editing: Boolean,
            extras: Bundle = Bundle(),
        ) {
            if (fragmentManager.findFragmentByTag(requestKey) != null) return
            LinkDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST, requestKey)
                    putString(ARG_LABEL, label)
                    putString(ARG_URL, url)
                    putBoolean(ARG_EDITING, editing)
                    putBundle(ARG_EXTRAS, extras)
                }
            }.show(fragmentManager, requestKey)
        }
    }
}

/**
 * [input] as a link Android can open: trimmed, with https:// added when it names no
 * scheme. Null when it is empty, or a web address without a host.
 */
fun normalizeLink(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val link = if (Uri.parse(trimmed).scheme == null) "https://$trimmed" else trimmed
    val uri = Uri.parse(link)
    val web = uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)
    return link.takeIf { !web || !uri.host.isNullOrEmpty() }
}
