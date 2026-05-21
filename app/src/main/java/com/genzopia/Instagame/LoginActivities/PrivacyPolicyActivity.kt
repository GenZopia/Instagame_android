package com.genzopia.Instagame.LoginActivities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.R
import com.genzopia.Instagame.common.BaseActivity
import com.google.firebase.auth.FirebaseAuth

class PrivacyPolicyActivity : BaseActivity() {

    companion object {
        private const val PREFS_NAME = "privacy_prefs"
        private const val KEY_ACCEPTED = "privacy_policy_accepted"

        /**
         * Returns true if the user has already accepted the privacy policy.
         * Call this before navigating to MainActivity to decide whether to
         * show this screen.
         */
        @JvmStatic
        fun hasAccepted(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ACCEPTED, false)
        }

        /**
         * Convenience: build an intent to start this activity.
         */
        @JvmStatic
        fun newIntent(context: Context): Intent =
            Intent(context, PrivacyPolicyActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val checkboxAccept = findViewById<CheckBox>(R.id.checkboxAccept)
        val btnAccept = findViewById<MaterialButton>(R.id.btnAccept)
        val tvDecline = findViewById<TextView>(R.id.tvDecline)

        // Enable the accept button only when the checkbox is ticked
        checkboxAccept.setOnCheckedChangeListener { _, isChecked ->
            btnAccept.isEnabled = isChecked
            btnAccept.alpha = if (isChecked) 1f else 0.5f
        }
        btnAccept.alpha = 0.5f

        btnAccept.setOnClickListener {
            // Persist acceptance so we never show this screen again
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ACCEPTED, true)
                .apply()

            goToMain()
        }

        tvDecline.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Are you sure?")
                .setMessage("You must accept the Privacy Policy and Terms of Service to use Instagame. Declining will sign you out.")
                .setPositiveButton("Decline & Sign Out") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Go Back", null)
                .show()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
