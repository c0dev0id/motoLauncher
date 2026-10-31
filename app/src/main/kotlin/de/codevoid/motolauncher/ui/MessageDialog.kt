package de.codevoid.motolauncher.ui

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import de.codevoid.motolauncher.R

/**
 * An [AlertDialog] that tells something, or asks: with a request key its positive button
 * reports the caller's extras as a fragment result. Everything it shows comes from its
 * arguments, so it survives a recreate of the activity.
 */
class MessageDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val requestKey = args.getString(ARG_REQUEST)
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(args.getString(ARG_TITLE))
            .setMessage(args.getString(ARG_MESSAGE))
        if (requestKey == null) {
            builder.setPositiveButton(R.string.ok, null)
        } else {
            builder
                .setPositiveButton(args.getString(ARG_POSITIVE)) { _, _ ->
                    parentFragmentManager.setFragmentResult(requestKey, Bundle(args.getBundle(ARG_EXTRAS)))
                }
                .setNegativeButton(R.string.cancel, null)
        }
        return builder.create().also { it.hideSystemBars() }
    }

    companion object {
        private const val ARG_REQUEST = "request"
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_POSITIVE = "positive"
        private const val ARG_EXTRAS = "extras"
        private const val TAG = "message"

        /** Shows [message], with [title] when given, and an OK button. */
        fun tell(fragmentManager: FragmentManager, message: String, title: String? = null) {
            MessageDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MESSAGE, message)
                }
            }.show(fragmentManager, TAG)
        }

        /** Asks [question] with a [positive] button that reports [extras] under [requestKey], and Cancel. */
        fun ask(
            fragmentManager: FragmentManager,
            requestKey: String,
            question: String,
            positive: String,
            extras: Bundle = Bundle(),
        ) {
            if (fragmentManager.findFragmentByTag(requestKey) != null) return
            MessageDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_REQUEST, requestKey)
                    putString(ARG_TITLE, question)
                    putString(ARG_POSITIVE, positive)
                    putBundle(ARG_EXTRAS, extras)
                }
            }.show(fragmentManager, requestKey)
        }
    }
}
