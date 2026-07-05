package com.genzopia.Instagame.LoginActivities

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.genzopia.Instagame.common.BaseActivity
import com.genzopia.Instagame.R
import com.genzopia.Instagame.gateway.ForgotPasswordRequest
import com.genzopia.Instagame.gateway.GatewayClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/**
 * ForgotPassword — sends a password reset email via the backend Gateway.
 * The Firebase API key and Identity Toolkit call are handled server-side.
 */
class ForgotPassword : BaseActivity() {

    private lateinit var emailaddress: EditText
    private lateinit var resetPasswordButton: Button
    private lateinit var animator: ValueAnimator
    private lateinit var headlineForgotPass: TextView
    private lateinit var headlineForgotPass1: TextView

    private fun isDarkMode(): Boolean {
        val flags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return flags == Configuration.UI_MODE_NIGHT_YES
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        emailaddress = findViewById(R.id.resetEmailAddress)
        resetPasswordButton = findViewById(R.id.resetBtn)
        headlineForgotPass = findViewById(R.id.headlineForgotPass)
        headlineForgotPass1 = findViewById(R.id.headline1ForgotPass)

        val textColor = if (isDarkMode()) Color.WHITE else Color.BLACK
        headlineForgotPass.setTextColor(textColor)
        headlineForgotPass1.setTextColor(textColor)

        resetPasswordButton.setOnClickListener {
            val email = emailaddress.text.toString().trim().lowercase()

            if (email.isEmpty()) {
                emailaddress.error = getString(R.string.error_generic, "Please enter your email")
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailaddress.error = getString(R.string.error_generic, "Please enter a valid email")
                return@setOnClickListener
            }

            startAnimatingButton()
            sendResetViaGateway(email)
        }
    }

    private fun sendResetViaGateway(email: String) {
        GatewayClient.callApi
            .forgotPassword(ForgotPasswordRequest(email))
            .enqueue(object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    stopAnimatingButton()
                    // Gateway always returns 200 even for unknown emails (security best practice)
                    Toast.makeText(
                        this@ForgotPassword,
                        getString(R.string.password_reset_email_sent),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    stopAnimatingButton()
                    Log.e("ForgotPassword", "Gateway call failed: ${t.message}", t)
                    Toast.makeText(
                        this@ForgotPassword,
                        getString(R.string.network_error_try_again),
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    private fun startAnimatingButton() {
        if (!::animator.isInitialized) {
            animator = ValueAnimator.ofInt(0, 3).apply {
                duration = 2500
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { anim ->
                    resetPasswordButton.text = when (anim.animatedValue as Int) {
                        0 -> getString(R.string.resetting_password_1)
                        1 -> getString(R.string.resetting_password_2)
                        2 -> getString(R.string.resetting_password_3)
                        else -> getString(R.string.resetting_password_4)
                    }
                }
            }
        }
        if (!animator.isRunning) animator.start()
        resetPasswordButton.isEnabled = false
    }

    private fun stopAnimatingButton() {
        if (::animator.isInitialized && animator.isRunning) animator.cancel()
        resetPasswordButton.text = getString(R.string.reset_password)
        resetPasswordButton.isEnabled = true
    }

    override fun onDestroy() {
        if (::animator.isInitialized && animator.isRunning) animator.cancel()
        super.onDestroy()
    }
}
