package com.genzopia.Instagame.LoginActivities

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import android.text.InputFilter
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.BuildConfig
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {
    private val TAG = "RegisterActivity"

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var selectedImageUri: Uri? = null
    // Keep original register button text so we can restore it after loading
    private var registerBtnOriginalText: CharSequence? = null

    // State for email verification flow
    private var emailVerified = false
    private val verifyHandler = Handler(Looper.getMainLooper())
    private var verifyAttemptsLeft = 10
    private val verifyIntervalMs = 3000L

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profilePicture.setImageURI(it)
            binding.avatarPlus.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Disable register button until email is verified
        binding.btnRegister.isEnabled = false

        // capture original text
        registerBtnOriginalText = binding.btnRegister.text

        // Wire UI actions
        try {
            binding.ccp.registerCarrierNumberEditText(binding.txtMobileNumber)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mobile EditText with CountryCodePicker: ${e.message}")
            binding.txtMobileNumber.filters = arrayOf(InputFilter.LengthFilter(15))
        }

        binding.profilePicture.setOnClickListener { getContent.launch("image/*") }
        binding.avatarPlus.setOnClickListener { getContent.launch("image/*") }

        try {
            binding.txtDOB.inputType = InputType.TYPE_NULL
            binding.txtDOB.isFocusable = false
            val showDobPicker = {
                try {
                    val now = Calendar.getInstance()
                    val dpd = DatePickerDialog(this, { _, y, m, d ->
                        binding.txtDOB.setText(getString(com.genzopia.Instagame.R.string.dob_format, d, m + 1, y))
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                    dpd.datePicker.maxDate = System.currentTimeMillis()
                    dpd.show()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to show DatePickerDialog: ${e.message}")
                }
            }
            binding.txtDOB.setOnClickListener { showDobPicker() }
            binding.txtDOB.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDobPicker() }
        } catch (e: Exception) {
            Log.w(TAG, "DOB picker setup failed: ${e.message}")
        }

        // Email 'Verify' button — user clicks to create/resend verification email
        binding.btnVerifyEmail.setOnClickListener {
            val email = binding.txtRegisterEmailAddress.text?.toString()?.trim() ?: ""
            val password = binding.txtRegisterPass.text?.toString() ?: ""
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password before verifying", Toast.LENGTH_SHORT).show()
            } else {
                startEmailVerificationFlow(email, password)
            }
        }

        // Register button: requires that the email is verified and a profile image is selected
        binding.btnRegister.setOnClickListener {
            val email = binding.txtRegisterEmailAddress.text?.toString()?.trim() ?: ""
            val password = binding.txtRegisterPass.text?.toString() ?: ""
            val confirmPassword = binding.txtRegisterConfirmPass.text?.toString() ?: ""
            val fullName = binding.txtFullName.text?.toString() ?: ""
            val dob = binding.txtDOB.text?.toString() ?: ""
            val mobileNo = binding.txtMobileNumber.text?.toString() ?: ""

            if (!validateInputs(email, password, confirmPassword, fullName, dob, mobileNo)) return@setOnClickListener

            // Require email verification
            val current = auth.currentUser
            if (current == null || current.email?.trim()?.lowercase() != email.lowercase()) {
                Toast.makeText(this, "Please verify using the same email first (press VERIFY)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Reload the current user to ensure we have the latest verification state
            current.reload().addOnCompleteListener { reloadTask ->
                if (!reloadTask.isSuccessful) {
                    Log.w(TAG, "user.reload failed: ${reloadTask.exception}")
                    Toast.makeText(this, "Failed to verify user status. Please try again.", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                if (!current.isEmailVerified) {
                    Toast.makeText(this, "Please verify your email first (open the verification link).", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                // Use the signed-in & verified user to proceed with uploading image + saving DB
                val user_id = current.uid
                // Disable register UI while uploading to prevent double-submit
                runOnUiThread {
                    // Show top loading bar
                    binding.progressTop.visibility = View.VISIBLE
                    // show spinner over button and clear text
                    binding.btnRegisterProgress.visibility = View.VISIBLE
                    registerBtnOriginalText = registerBtnOriginalText ?: binding.btnRegister.text
                    binding.btnRegister.text = ""
                    binding.btnRegister.isEnabled = false
                    binding.btnVerifyEmail.isEnabled = false
                }

                uploadProfileImage(user_id, email, fullName, dob, mobileNo, object : UploadCallback {
                    override fun onSuccess(downloadUrl: String, uploadedPath: String?) {
                        // Hide loading UI on main thread before proceeding to DB save
                        runOnUiThread {
                            binding.progressTop.visibility = View.GONE
                            binding.btnRegisterProgress.visibility = View.GONE
                            binding.btnRegister.text = registerBtnOriginalText
                        }
                        saveUserToDatabaseWithRollback(user_id, email, fullName, dob, mobileNo, downloadUrl, uploadedPath)
                    }

                    override fun onFailure(message: String) {
                        runOnUiThread {
                            Toast.makeText(this@RegisterActivity, "Upload failed: $message. Rolling back user creation.", Toast.LENGTH_LONG).show()
                            // Re-enable register UI so user can try again
                            binding.progressTop.visibility = View.GONE
                            binding.btnRegisterProgress.visibility = View.GONE
                            binding.btnRegister.text = registerBtnOriginalText
                            binding.btnRegister.isEnabled = true
                            binding.btnVerifyEmail.isEnabled = true
                        }
                        rollbackDeleteUser()
                    }
                })
            }
        }

        // If user returns to the screen and is already signed in, refresh status
        checkExistingSignedInUser()
    }

    private fun checkExistingSignedInUser() {
        val current = auth.currentUser
        if (current != null) {
            // If the email matches the entered email field, update UI and start polling to detect verification change
            binding.txtRegisterEmailAddress.setText(current.email)
            // Show tick if already verified
            current.reload().addOnCompleteListener {
                if (current.isEmailVerified) {
                    onEmailVerified()
                } else {
                    // allow the user to resend verification
                    binding.btnRegister.isEnabled = false
                }
            }
        }
    }

    private fun startEmailVerificationFlow(email: String, password: String) {
        // If there's already a signed-in user with same email, just resend verification
        val current = auth.currentUser
        if (current != null && current.email?.equals(email, true) == true) {
            sendVerificationEmail(current)
            startPollingForVerification()
            return
        }

        // Try creating an auth account (if already exists, sign-in and resend)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Keep user signed in so we can poll for email verification
                        sendVerificationEmail(user)
                        startPollingForVerification()
                    }
                } else {
                    val msg = task.exception?.message ?: "Unknown error"
                    Log.d(TAG, "createUser (verify) failed: $msg")
                    // If account exists already, sign in and resend verification
                    if (msg.contains("email address is already in use", true) || msg.contains("already in use", true)) {
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this) { signInTask ->
                                if (signInTask.isSuccessful) {
                                    val user = auth.currentUser
                                    if (user != null) {
                                        sendVerificationEmail(user)
                                        startPollingForVerification()
                                    }
                                } else {
                                    val err = signInTask.exception?.message ?: "Sign in failed"
                                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
    }

    // Start a short polling loop to check user.reload() and detect email verification
    private fun startPollingForVerification() {
        verifyAttemptsLeft = 10
        binding.btnVerifyEmail.isEnabled = false
        binding.btnVerifyEmail.text = "Sent"
        verifyHandler.postDelayed(verifyRunnable, verifyIntervalMs)
    }

    private val verifyRunnable = object : Runnable {
        override fun run() {
            try {
                val current = auth.currentUser
                if (current == null) {
                    verifyHandler.postDelayed(this, verifyIntervalMs)
                    return
                }
                current.reload().addOnCompleteListener { t ->
                    if (current.isEmailVerified) {
                        onEmailVerified()
                    } else {
                        verifyAttemptsLeft--
                        if (verifyAttemptsLeft > 0) verifyHandler.postDelayed(this, verifyIntervalMs) else {
                            // allow resend after timeout
                            binding.btnVerifyEmail.isEnabled = true
                            binding.btnVerifyEmail.text = "Verify"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "verifyRunnable failed: ${e.message}")
            }
        }
    }

    private fun onEmailVerified() {
        emailVerified = true
        verifyHandler.removeCallbacks(verifyRunnable)
        runOnUiThread {
            // Show tick
            try { binding.imgEmailVerified.visibility = View.VISIBLE } catch (_: Exception) {}
            // Hide the verify button entirely when verified
            try { binding.btnVerifyEmail.visibility = View.GONE } catch (_: Exception) {}
            // Enable register button so user can complete signup
            binding.btnRegister.isEnabled = true
            Toast.makeText(this, "Email verified — you can now complete sign up.", Toast.LENGTH_SHORT).show()
        }
    }


    // Helper to send verification email and surface result to user (non-blocking)
    private fun sendVerificationEmail(user: FirebaseUser) {
        user.sendEmailVerification()
            .addOnCompleteListener { sendTask ->
                if (sendTask.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this, "Verification email sent to ${user.email}. Please check your inbox.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val msg = sendTask.exception?.message ?: "Failed to send verification email"
                    runOnUiThread {
                        Toast.makeText(this, "$msg", Toast.LENGTH_LONG).show()
                    }
                    Log.w(TAG, "sendVerificationEmail failed: ${sendTask.exception}")
                }
            }
    }

    private fun validateInputs(
        email: String,
        password: String,
        confirmPassword: String,
        fullName: String,
        dob: String,
        mobileNo: String
    ): Boolean {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || fullName.isEmpty() || dob.isEmpty() || mobileNo.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select a profile picture", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // Rest of existing upload/save/delete logic — unchanged except registerUser removal

    private interface UploadCallback {
        fun onSuccess(downloadUrl: String, uploadedPath: String?)
        fun onFailure(message: String)
    }

    private interface DeleteCallback {
        fun onComplete(success: Boolean)
    }

    private fun uploadProfileImage(
        user_id: String,
        email: String,
        fullName: String,
        dob: String,
        mobileNo: String,
        callback: UploadCallback
    ) {
        Log.d(TAG, "uploadProfileImage called for user=$user_id email=$email fullName=$fullName dob=$dob mobile=$mobileNo")

        val uri = selectedImageUri
        if (uri == null) {
            callback.onFailure("No image selected")
            return
        }

        val inputStream = contentResolver.openInputStream(uri)
        if (inputStream == null) {
            callback.onFailure("Failed to read selected image")
            return
        }

        val fileBytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            callback.onFailure("Failed to read image: ${e.message}")
            return
        }

        val safeFilename: String = (queryFileName(uri) ?: "$user_id.jpg")

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .build()
        val mediaType = (contentResolver.getType(uri) ?: "image/jpeg").toMediaTypeOrNull()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", safeFilename, fileBytes.toRequestBody(mediaType))
            .addFormDataPart("name", safeFilename)
            .addFormDataPart("path", "$user_id/$safeFilename")
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .post(multipartBody)
            .build()

        runOnUiThread { Toast.makeText(this@RegisterActivity.applicationContext, "Uploading profile image...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "upload failed: ${e.message}")
                // Ensure UI is re-enabled on failure
                runOnUiThread {
                    binding.btnRegister.isEnabled = true
                    binding.btnVerifyEmail.isEnabled = true
                }
                callback.onFailure(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStrSafe = try { it.body?.string()?.trim() ?: "" } catch (_: Exception) { "" }
                    Log.d(TAG, "upload response code=${it.code} body=$bodyStrSafe")
                    runOnUiThread { Toast.makeText(this@RegisterActivity, "Worker response: ${if (bodyStrSafe.isEmpty()) "<empty>" else bodyStrSafe}", Toast.LENGTH_LONG).show() }

                    if (!it.isSuccessful) {
                        val errMsg = "${it.code} ${bodyStrSafe}"
                        // Re-enable UI so user can retry
                        runOnUiThread {
                            binding.btnRegister.isEnabled = true
                            binding.btnVerifyEmail.isEnabled = true
                        }
                        callback.onFailure(errMsg)
                        return
                    }

                    if (bodyStrSafe.isEmpty()) {
                        runOnUiThread {
                            binding.btnRegister.isEnabled = true
                            binding.btnVerifyEmail.isEnabled = true
                        }
                        callback.onFailure("Upload succeeded but returned empty URL")
                        return
                    }

                    var downloadUrl: String?
                    var returnedPath: String? = null
                    try {
                        if (bodyStrSafe.trimStart().startsWith("{")) {
                            val obj = org.json.JSONObject(bodyStrSafe)
                            downloadUrl = when {
                                obj.has("url") -> obj.optString("url")
                                obj.has("link") -> obj.optString("link")
                                obj.has("file") -> obj.optString("file")
                                obj.has("location") -> obj.optString("location")
                                else -> bodyStrSafe
                            }
                            val pathStr = obj.optString("path", "")
                            if (pathStr.isNotEmpty()) returnedPath = pathStr
                        } else {
                            downloadUrl = bodyStrSafe
                        }
                    } catch (_: Exception) {
                        downloadUrl = bodyStrSafe
                    }

                    if (downloadUrl.isNullOrEmpty()) {
                        runOnUiThread {
                            binding.btnRegister.isEnabled = true
                            binding.btnVerifyEmail.isEnabled = true
                        }
                        callback.onFailure("Upload returned invalid URL")
                        return
                    }

                    callback.onSuccess(downloadUrl, returnedPath)
                }
            }
        })
    }

    private fun saveUserToDatabaseWithRollback(
        user_id: String,
        email: String,
        fullName: String,
        dob: String,
        mobileNo: String,
        profilePhotoUrl: String,
        uploadedPath: String?
    ) {
        val user = User(user_id, email, fullName, dob, mobileNo)
        user.profile_photo_url = profilePhotoUrl

        database.reference.child("users").child(user_id)
            .setValue(user)
            .addOnSuccessListener {
                runOnUiThread {
                    // ensure loading UI is hidden
                    binding.progressTop.visibility = View.GONE
                    binding.btnRegisterProgress.visibility = View.GONE
                    binding.btnRegister.text = registerBtnOriginalText

                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { dbEx ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to save user data: ${dbEx.message}. Rolling back...", Toast.LENGTH_LONG).show()
                }

                val pathToDelete = when {
                    !uploadedPath.isNullOrBlank() -> uploadedPath
                    profilePhotoUrl.contains("/") -> "$user_id/${profilePhotoUrl.substringAfterLast('/') }"
                    else -> "$user_id/$profilePhotoUrl"
                }

                Log.d(TAG, "Attempting to delete uploaded file at path: $pathToDelete")

                deleteUploadedFileWithRetry(pathToDelete, 3, object : DeleteCallback {
                    override fun onComplete(success: Boolean) {
                        Log.d(TAG, "deleteUploadedFile completed success=$success")
                        // hide loading UI and restore button so user can retry
                        runOnUiThread {
                            binding.progressTop.visibility = View.GONE
                            binding.btnRegisterProgress.visibility = View.GONE
                            binding.btnRegister.text = registerBtnOriginalText
                        }
                        rollbackDeleteUser()
                    }
                })
            }
    }

    private fun deleteUploadedFileWithRetry(path: String, attemptsLeft: Int, callback: DeleteCallback) {
        val client = OkHttpClient()

        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("path", path)
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .delete(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "delete request failed: ${e.message}; attemptsLeft=$attemptsLeft")
                if (attemptsLeft > 1) {
                    val delay = (1000L * Math.pow(2.0, (3 - attemptsLeft).toDouble())).toLong()
                    Handler(Looper.getMainLooper()).postDelayed({
                        deleteUploadedFileWithRetry(path, attemptsLeft - 1, callback)
                    }, delay)
                } else {
                    callback.onComplete(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        Log.d(TAG, "delete successful for path=$path")
                        callback.onComplete(true)
                    } else {
                        Log.d(TAG, "delete failed code=${it.code} for path=$path; attemptsLeft=$attemptsLeft")
                        if (attemptsLeft > 1) {
                            val delay = (1000L * Math.pow(2.0, (3 - attemptsLeft).toDouble())).toLong()
                            Handler(Looper.getMainLooper()).postDelayed({
                                deleteUploadedFileWithRetry(path, attemptsLeft - 1, callback)
                            }, delay)
                        } else {
                            callback.onComplete(false)
                        }
                    }
                }
            }
        })
    }

    private fun rollbackDeleteUser() {
        val current = auth.currentUser
        // Try to delete the created Firebase user if present
        if (current != null) {
            current.delete()
                .addOnCompleteListener {
                    // sign out locally
                    auth.signOut()
                    runOnUiThread {
                        // Reset UI so user can restart verification/signup flow
                        try { binding.imgEmailVerified.visibility = View.GONE } catch (_: Exception) {}
                        try { binding.btnVerifyEmail.visibility = View.VISIBLE } catch (_: Exception) {}
                        try {
                            binding.btnVerifyEmail.isEnabled = true
                            binding.btnVerifyEmail.text = "Verify"
                        } catch (_: Exception) {}
                        binding.btnRegister.isEnabled = false
                        // hide loading UI if visible and restore button text
                        binding.progressTop.visibility = View.GONE
                        binding.btnRegisterProgress.visibility = View.GONE
                        binding.btnRegister.text = registerBtnOriginalText

                        Toast.makeText(this, "Rolled back registration (user deleted)", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { delEx ->
                    // Even if delete failed, sign out and reset UI so the user can retry
                    auth.signOut()
                    runOnUiThread {
                        try { binding.imgEmailVerified.visibility = View.GONE } catch (_: Exception) {}
                        try { binding.btnVerifyEmail.visibility = View.VISIBLE } catch (_: Exception) {}
                        try {
                            binding.btnVerifyEmail.isEnabled = true
                            binding.btnVerifyEmail.text = "Verify"
                        } catch (_: Exception) {}
                        binding.btnRegister.isEnabled = false
                        binding.progressTop.visibility = View.GONE
                        binding.btnRegisterProgress.visibility = View.GONE
                        binding.btnRegister.text = registerBtnOriginalText

                        Toast.makeText(this, "Rollback: failed to delete user: ${delEx.message}", Toast.LENGTH_LONG).show()
                    }
                }
        } else {
            // No current user — just ensure UI is reset
            auth.signOut()
            runOnUiThread {
                try { binding.imgEmailVerified.visibility = View.GONE } catch (_: Exception) {}
                try { binding.btnVerifyEmail.visibility = View.VISIBLE } catch (_: Exception) {}
                try {
                    binding.btnVerifyEmail.isEnabled = true
                    binding.btnVerifyEmail.text = "Verify"
                } catch (_: Exception) {}
                binding.btnRegister.isEnabled = false

                binding.progressTop.visibility = View.GONE
                binding.btnRegisterProgress.visibility = View.GONE
                binding.btnRegister.text = registerBtnOriginalText

                Log.d(TAG, "rollbackDeleteUser: current user null")
            }
        }
    }


    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(0)
                }
            }
        } catch (_: Exception) {
        }
        return name
    }

    private fun resolveThemeColor(attrName: String, fallbackResId: Int): Int {
        val typed = android.util.TypedValue()
        val attrId = resources.getIdentifier(attrName, "attr", packageName)
        if (attrId != 0 && theme.resolveAttribute(attrId, typed, true)) {
            return if (typed.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, typed.resourceId) else typed.data
        }
        if (theme.resolveAttribute(android.R.attr.textColorPrimary, typed, true)) {
            return if (typed.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, typed.resourceId) else typed.data
        }
        return androidx.core.content.ContextCompat.getColor(this, fallbackResId)
    }

    private fun dumpCcpInternalStructure() {
        try {
            val ccp = binding.ccp
            val cls = ccp.javaClass
            Log.d(TAG, "--- CCP class: ${cls.name}")
            for (f in cls.declaredFields) {
                try {
                    f.isAccessible = true
                    val value = try { f.get(ccp) } catch (e: Exception) { "<unreadable>" }
                    Log.d(TAG, "CCP.field: ${f.name} (${f.type.simpleName}) = ${value?.let { it::class.simpleName } ?: "null"}")
                } catch (e: Exception) {
                    Log.d(TAG, "CCP.field: ${f.name} (error: ${e.message})")
                }
            }
            for (m in cls.declaredMethods) {
                try {
                    Log.d(TAG, "CCP.method: ${m.name} (params=${m.parameterCount})")
                } catch (e: Exception) {
                    Log.d(TAG, "CCP.method: ${m.name} (error)")
                }
            }
            Log.d(TAG, "--- end CCP dump")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dump CCP internals: ${e.message}")
        }
    }

}
