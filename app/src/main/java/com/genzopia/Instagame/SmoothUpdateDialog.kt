package com.genzopia.Instagame

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment

/**
 * Dismissible dialog shown when a smooth (optional) update is available.
 * User can choose "Update Now" to go to Play Store or "Later" to dismiss.
 */
class SmoothUpdateDialog : DialogFragment() {

    companion object {
        @JvmField val TAG = "SmoothUpdateDialog"
        private const val ARG_MIN_VERSION = "min_version"

        @JvmStatic fun newInstance(minVersion: String): SmoothUpdateDialog =
            SmoothUpdateDialog().apply {
                arguments = Bundle().apply { putString(ARG_MIN_VERSION, minVersion) }
            }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val minVersion = arguments?.getString(ARG_MIN_VERSION) ?: ""
        val message = if (minVersion.isNotBlank())
            "A new version ($minVersion) is available with improvements. Update now for the best experience."
        else
            "A new version is available with improvements. Update now for the best experience."

        return AlertDialog.Builder(requireContext())
            .setTitle("Update Available")
            .setMessage(message)
            .setPositiveButton("Update Now") { _, _ -> openPlayStore() }
            .setNegativeButton("Later") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .create()
    }

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
    }
}
