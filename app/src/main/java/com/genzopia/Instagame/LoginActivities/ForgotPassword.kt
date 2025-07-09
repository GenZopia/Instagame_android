package com.genzopia.Instagame.LoginActivities

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.R
import com.google.firebase.auth.FirebaseAuth


class ForgotPassword : AppCompatActivity() {

    private lateinit var emailaddress: EditText
    private lateinit var resetPasswordButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth
    private lateinit var animator: ValueAnimator
    private lateinit var headlineForgotPass : TextView
    private lateinit var headlineForgotPass1 : TextView

    private fun isDarkMode(context: ForgotPassword): Boolean {
        val nightModeFlags: Int =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()
        emailaddress = findViewById(R.id.resetEmailAddress)
        resetPasswordButton = findViewById(R.id.resetBtn)
        headlineForgotPass = findViewById(R.id.headlineForgotPass)
        headlineForgotPass1 = findViewById(R.id.headline1ForgotPass)

        if (isDarkMode(this)) {
            headlineForgotPass.setTextColor(Color.WHITE)
            headlineForgotPass1.setTextColor(Color.WHITE)
        }
        else {
            headlineForgotPass.setTextColor(Color.BLACK)
            headlineForgotPass1.setTextColor(Color.BLACK)
        }

        resetPasswordButton.setOnClickListener {
            val email = emailaddress.text.toString().trim()

            if (email.isEmpty()) {
                emailaddress.error = "Please enter your email"
                return@setOnClickListener
            }

            startAnimatingButton()

            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                stopAnimatingButton()

                if (task.isSuccessful) {
                    Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startAnimatingButton() {
        animator = ValueAnimator.ofInt(0, 3)
        animator.duration = 2500
        animator.repeatCount = ValueAnimator.INFINITE
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            when (value) {
                0 -> resetPasswordButton.text = "Resetting the password."
                1 -> resetPasswordButton.text = "Resetting the password.."
                2 -> resetPasswordButton.text = "Resetting the password..."
                3 -> resetPasswordButton.text = "Resetting the password...."
            }
        }
        animator.start()
        resetPasswordButton.isEnabled = false
    }

    private fun stopAnimatingButton() {
        animator.cancel()
        resetPasswordButton.text = "Reset Password"
        resetPasswordButton.isEnabled = true
    }
}
