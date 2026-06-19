package com.genzopia.Instagame

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.fragment.app.DialogFragment

/**
 * Non-dismissible dialog shown when the app version is below the required minimum.
 * Requirements: 2.2, 2.3, 2.4
 */
class ForceUpdateDialog : DialogFragment() {

    companion object {
        @JvmField val TAG = "ForceUpdateDialog"
        private const val ARG_MIN_VERSION = "min_version"

        @JvmStatic fun newInstance(minVersion: String): ForceUpdateDialog =
            ForceUpdateDialog().apply {
                arguments = Bundle().apply { putString(ARG_MIN_VERSION, minVersion) }
            }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val minVersion = arguments?.getString(ARG_MIN_VERSION) ?: ""
        val message = if (minVersion.isNotBlank())
            "This version is no longer supported. Please update to version $minVersion or later to continue."
        else
            "This version is no longer supported. Please update to the latest version to continue."

        // Req 2.3: setCancelable(false) prevents back/outside-touch dismissal
        return AlertDialog.Builder(requireContext())
            .setTitle("Update Required")
            .setMessage(message)
            .setPositiveButton("Update Now") { _, _ -> openPlayStore() }
            .setCancelable(false)
            .create()
            .also { it.setCanceledOnTouchOutside(false) }
    }

    override fun onResume() {
        super.onResume()
        // Req 2.3: block back button
        dialog?.setOnKeyListener { _, keyCode, _ -> keyCode == KeyEvent.KEYCODE_BACK

        }
    }

    /** Opens Play Store; falls back to browser if not installed. Req 2.4 */
    private fun openPlayStore() {
        val pkg = requireContext().packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$pkg")
                setPackage("com.android.vending")
            })
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
            })
        }
        activity?.finishAffinity()
    }
}
