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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.Executors


class ForgotPassword : AppCompatActivity() {

    private lateinit var emailaddress: EditText
    private lateinit var resetPasswordButton: Button
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

        // Ensure Firebase is initialized for this process (idempotent)
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.w("ForgotPassword", "FirebaseApp.initializeApp() failed or already initialized", e)
        }

        // Log all FirebaseApp instances and options to ensure we're connected to the right project
        try {
            val apps = FirebaseApp.getApps(this)
            for (app in apps) {
                try {
                    val opts = app.options
                    Log.d("ForgotPassword", "FirebaseApp name=${app.name}, projectId=${opts.projectId}, applicationId=${opts.applicationId}")
                } catch (e: Exception) {
                    Log.w("ForgotPassword", "Failed reading options for app ${app.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.w("ForgotPassword", "FirebaseApp.getApps() failed", e)
        }

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
            val emailOriginal = emailaddress.text.toString().trim()

            if (emailOriginal.isEmpty()) {
                emailaddress.error = getString(R.string.error_generic, "Please enter your email")
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(emailOriginal).matches()) {
                emailaddress.error = getString(R.string.error_generic, "Please enter a valid email")
                return@setOnClickListener
            }

            // Normalize email for checks (lowercase) — helps avoid mismatch
            val email = emailOriginal.lowercase()
            Log.d("ForgotPassword", "Reset requested: original='$emailOriginal' normalized='$email'")

            // Start animation / disable button while we check and send
            startAnimatingButton()

            // Debug info: log which Firebase project and app id we're using
            try {
                val firebaseOptions = FirebaseApp.getInstance().options
                Log.d("ForgotPassword", "Runtime ApplicationId=${applicationContext.packageName}")
                Log.d("ForgotPassword", "Firebase projectId=${firebaseOptions.projectId}, appId=${firebaseOptions.applicationId}")
            } catch (e: Exception) {
                Log.w("ForgotPassword", "Failed to read FirebaseApp options", e)
            }

            // First check whether this email has any sign-in methods (i.e., account exists)
            auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { fetchTask ->
                Log.d("ForgotPassword", "fetchSignInMethodsForEmail completed: success=${fetchTask.isSuccessful}")
                if (fetchTask.isSuccessful) {
                    val signInMethods = fetchTask.result?.signInMethods
                    Log.d("ForgotPassword", "signInMethods for $email = ${signInMethods?.joinToString()}")
                } else {
                    Log.w("ForgotPassword", "fetchSignInMethodsForEmail exception", fetchTask.exception)
                }

                if (!isFinishing) {
                    if (fetchTask.isSuccessful) {
                        val signInMethods = fetchTask.result?.signInMethods
                        if (signInMethods == null || signInMethods.isEmpty()) {
                            // Fallback: sometimes fetchSignInMethodsForEmail can be empty/unreliable.
                            // Try to send the reset email anyway and handle user-not-found properly.
                            Log.w("ForgotPassword", "No signInMethods returned for $email — attempting sendPasswordResetEmail fallback")

                            auth.sendPasswordResetEmail(email).addOnCompleteListener { fallbackSend ->
                                stopAnimatingButton()
                                if (fallbackSend.isSuccessful) {
                                    Toast.makeText(this, getString(R.string.password_reset_email_sent), Toast.LENGTH_LONG).show()
                                    // Also run REST debug to surface server response if email doesn't arrive
                                    debugSendPasswordResetViaRest(email)
                                    finish()
                                } else {
                                    val ex = fallbackSend.exception
                                    when (ex) {
                                        is FirebaseAuthInvalidUserException -> {
                                            // Explicitly no user found
                                            Toast.makeText(this, getString(R.string.no_account_found), Toast.LENGTH_LONG).show()
                                        }
                                        is FirebaseNetworkException -> {
                                            Toast.makeText(this, getString(R.string.network_error_try_again), Toast.LENGTH_LONG).show()
                                        }
                                        else -> {
                                            Toast.makeText(this, getString(R.string.error_generic, ex?.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    Log.e("ForgotPassword", "Fallback sendPasswordResetEmail failed", ex)
                                    // Debug REST to capture server error details
                                    debugSendPasswordResetViaRest(email)
                                }
                            }

                            return@addOnCompleteListener
                        }

                        // If providers include 'password', we can send a reset email. Otherwise, notify user
                        val hasPasswordProvider = signInMethods.any { it.equals("password", ignoreCase = true) }
                        if (!hasPasswordProvider) {
                            // Account exists but not with password provider (e.g., google.com, facebook.com)
                            stopAnimatingButton()
                            val providers = signInMethods.joinToString(", ")
                            Toast.makeText(this, getString(R.string.provider_account_message, providers), Toast.LENGTH_LONG).show()
                            Log.d("ForgotPassword", "Account for $email uses providers: $providers — cannot send password reset")
                            return@addOnCompleteListener
                        }

                        // Account uses email/password — proceed to send the password reset email
                        auth.sendPasswordResetEmail(email).addOnCompleteListener { sendTask ->
                            stopAnimatingButton()

                            if (sendTask.isSuccessful) {
                                Toast.makeText(this, getString(R.string.password_reset_email_sent), Toast.LENGTH_LONG).show()
                                // REST debug in case email doesn't arrive
                                debugSendPasswordResetViaRest(email)
                                finish()
                            } else {
                                val ex = sendTask.exception
                                when (ex) {
                                    is FirebaseNetworkException -> {
                                        Toast.makeText(this, getString(R.string.network_error_try_again), Toast.LENGTH_LONG).show()
                                    }
                                    else -> {
                                        Toast.makeText(this, getString(R.string.error_generic, ex?.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                                    }
                                }
                                Log.e("ForgotPassword", "sendPasswordResetEmail failed", ex)
                                // Debug REST to capture server error details
                                debugSendPasswordResetViaRest(email)
                            }
                        }
                    } else {
                        // fetchSignInMethodsForEmail failed (network or other error)
                        stopAnimatingButton()
                        val ex = fetchTask.exception
                        when (ex) {
                            is FirebaseNetworkException -> Toast.makeText(this, getString(R.string.network_error_try_again), Toast.LENGTH_LONG).show()
                            else -> Toast.makeText(this, getString(R.string.error_checking_account, ex?.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                        }
                        Log.e("ForgotPassword", "fetchSignInMethodsForEmail failed", ex)
                    }
                } else {
                    // Activity finishing; ensure animator stopped
                    stopAnimatingButton()
                }
            }
        }
    }

    private fun debugSendPasswordResetViaRest(email: String) {
        // Call the Identity Toolkit REST endpoint to get a verbose response we can inspect
        val apiKey = try {
            FirebaseApp.getInstance().options.apiKey
        } catch (e: Exception) {
            Log.w("ForgotPassword", "Unable to read API key from FirebaseOptions", e)
            return
        }

        val url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey"
        val json = "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"$email\"}"
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = json.toRequestBody(mediaType)
        val client = OkHttpClient()
        val request = Request.Builder().url(url).post(body).build()

        Executors.newSingleThreadExecutor().execute {
            try {
                val resp = client.newCall(request).execute()
                val respBody = resp.body?.string()
                Log.d("ForgotPassword", "REST sendOobCode status=${resp.code} body=$respBody")
                runOnUiThread {
                    Toast.makeText(this, "Debug REST response: ${resp.code} - ${respBody?.take(250)}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("ForgotPassword", "REST sendOobCode failed", e)
                runOnUiThread {
                    Toast.makeText(this, "Debug REST error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAnimatingButton() {
        // Safely initialize animator only once
        if (!::animator.isInitialized) {
            animator = ValueAnimator.ofInt(0, 3)
            animator.duration = 2500
            animator.repeatCount = ValueAnimator.INFINITE
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                when (value) {
                    0 -> resetPasswordButton.text = getString(R.string.resetting_password_1)
                    1 -> resetPasswordButton.text = getString(R.string.resetting_password_2)
                    2 -> resetPasswordButton.text = getString(R.string.resetting_password_3)
                    3 -> resetPasswordButton.text = getString(R.string.resetting_password_4)
                }
            }
        }

        if (!animator.isRunning) animator.start()
        resetPasswordButton.isEnabled = false
    }

    private fun stopAnimatingButton() {
        if (::animator.isInitialized && animator.isRunning) {
            animator.cancel()
        }
        resetPasswordButton.text = getString(R.string.reset_password)
        resetPasswordButton.isEnabled = true
    }

    override fun onDestroy() {
        if (::animator.isInitialized && animator.isRunning) {
            animator.cancel()
        }
        super.onDestroy()
    }
}
