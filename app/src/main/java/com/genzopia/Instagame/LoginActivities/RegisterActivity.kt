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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.BuildConfig
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
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

class RegisterActivity : AppCompatActivity() {
    private val TAG = "RegisterActivity"

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            // Show selected image in the profile picture view
            // binding is initialized in onCreate before this callback is ever invoked
            binding.profilePicture.setImageURI(it)
            // hide the plus overlay when an image is selected
            binding.avatarPlus.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Date picker for date of birth
        binding.txtDOB.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    binding.txtDOB.setText(getString(com.genzopia.Instagame.R.string.dob_format, day, month + 1, year))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Profile image picker: clicking either the photo or the plus launches picker
        binding.profilePicture.setOnClickListener {
            getContent.launch("image/*")
        }
        binding.avatarPlus.setOnClickListener {
            getContent.launch("image/*")
        }

        // Profile image picker (existing behavior also kept for older codepaths)
        binding.profilePicture.setOnClickListener {
            getContent.launch("image/*")
        }

        // Register button click
        binding.btnRegister.setOnClickListener {
            val email = binding.txtRegisterEmailAddress.text.toString()
            val password = binding.txtRegisterPass.text.toString()
            val confirmPassword = binding.txtRegisterConfirmPass.text.toString()
            val fullName = binding.txtFullName.text.toString()
            val dob = binding.txtDOB.text.toString()
            val mobileNo = binding.txtMobileNumber.text.toString()

            if (validateInputs(email, password, confirmPassword, fullName, dob, mobileNo)) {
                registerUser(email, password, fullName, dob, mobileNo)
            }
        }

        // Sign in link click
        binding.txtSignInInstead.setOnClickListener {
            finish() // Go back to login activity
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

    @Suppress("UNUSED_PARAMETER")
    private fun registerUser(
        email: String,
        password: String,
        fullName: String,
        dob: String,
        mobileNo: String
    ) {
        // Log parameters to avoid 'never used' warnings and aid debugging
        Log.d(TAG, "registerUser called with email=$email fullName=$fullName dob=$dob mobile=$mobileNo")

        // Create auth first to get a stable user id used in the upload path
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user_id = auth.currentUser?.uid ?: run {
                        Toast.makeText(this, "Registration failed: unable to get user id", Toast.LENGTH_SHORT).show()
                        return@addOnCompleteListener
                    }

                    // Upload image using the created user id; if upload or DB write fails we will rollback
                    uploadProfileImage(user_id, email, fullName, dob, mobileNo, object : UploadCallback {
                        override fun onSuccess(downloadUrl: String, uploadedPath: String?) {
                            // After successful upload, write to database
                            saveUserToDatabaseWithRollback(user_id, email, fullName, dob, mobileNo, downloadUrl, uploadedPath)
                        }

                        override fun onFailure(message: String) {
                            // Upload failed -> delete created user and inform
                            runOnUiThread {
                                Toast.makeText(this@RegisterActivity, "Upload failed: $message. Rolling back user creation.", Toast.LENGTH_LONG).show()
                            }
                            rollbackDeleteUser()
                        }
                    })
                } else {
                    val err = task.exception?.message ?: "Unknown error"
                    Toast.makeText(this, "Registration failed: $err", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Upload callback interface (now includes returned uploadedPath if worker returns it)
    private interface UploadCallback {
        fun onSuccess(downloadUrl: String, uploadedPath: String?)
        fun onFailure(message: String)
    }

    // Delete callback for worker deletion
    private interface DeleteCallback {
        fun onComplete(success: Boolean)
    }

    // Upload profile image to the user's HTTP worker instead of Firebase Storage.
    // This function accepts an UploadCallback to continue the transactional flow.
    private fun uploadProfileImage(
        user_id: String,
        email: String,
        fullName: String,
        dob: String,
        mobileNo: String,
        callback: UploadCallback
    ) {
        // Use parameters in a debug log to avoid 'parameter never used' warnings and aid debugging
        Log.d(TAG, "uploadProfileImage called for user=$user_id email=$email fullName=$fullName dob=$dob mobile=$mobileNo")

        val uri = selectedImageUri
        if (uri == null) {
            callback.onFailure("No image selected")
            return
        }

        // Read bytes from the selected URI
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

        // Determine filename (fall back to user_id.jpg)
        val filename = queryFileName(uri) ?: "$user_id.jpg"

        // Build multipart request
        val client = OkHttpClient()
        val mediaType = (contentResolver.getType(uri) ?: "image/jpeg").toMediaTypeOrNull()

        val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", filename, fileBytes.toRequestBody(mediaType))
            .addFormDataPart("name", filename)
            .addFormDataPart("path", "$user_id/$filename")
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .post(multipartBody)
            .build()

        // Show a simple progress toast
        runOnUiThread { Toast.makeText(this, "Uploading profile image...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "upload failed: ${e.message}")
                callback.onFailure(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStr = try { it.body?.string()?.trim() } catch (_: Exception) { null }

                    // Debug logging and toast of worker response
                    Log.d(TAG, "upload response code=${it.code} body=$bodyStr")
                    runOnUiThread { Toast.makeText(this@RegisterActivity, "Worker response: ${bodyStr ?: "<empty>"}", Toast.LENGTH_LONG).show() }

                    if (!it.isSuccessful) {
                        val errMsg = "${it.code} ${bodyStr ?: ""}"
                        callback.onFailure(errMsg)
                        return
                    }

                    if (bodyStr.isNullOrEmpty()) {
                        callback.onFailure("Upload succeeded but returned empty URL")
                        return
                    }

                    // Try to extract a URL if the worker returned JSON and also the returned path if present
                    var downloadUrl: String? = bodyStr
                    var returnedPath: String? = null
                    try {
                        if (bodyStr.trimStart().startsWith("{")) {
                            val obj = org.json.JSONObject(bodyStr)
                            downloadUrl = obj.optString("url", obj.optString("link", obj.optString("file", obj.optString("location", bodyStr))))
                            returnedPath = obj.optString("path", returnedPath)
                        }
                    } catch (_: Exception) {
                        // ignore JSON parse errors; fallback to the raw body
                    }

                    if (downloadUrl.isNullOrEmpty()) {
                        callback.onFailure("Upload returned invalid URL")
                        return
                    }

                    // Return both the download URL and any path the worker returned
                    callback.onSuccess(downloadUrl!!, returnedPath)
                }
            }
        })
    }

    // Save to database and rollback on failure: delete uploaded file and delete user
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
                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            .addOnFailureListener { dbEx ->
                // DB write failed: attempt to remove uploaded file and delete user
                runOnUiThread {
                    Toast.makeText(this, "Failed to save user data: ${dbEx.message}. Rolling back...", Toast.LENGTH_LONG).show()
                }

                // Determine deletion path: prefer returned uploadedPath; else try to derive from the URL
                val pathToDelete = when {
                    !uploadedPath.isNullOrBlank() -> uploadedPath
                    profilePhotoUrl.contains("/") -> "$user_id/${profilePhotoUrl.substringAfterLast('/')}"
                    else -> "$user_id/${profilePhotoUrl}"
                }

                Log.d(TAG, "Attempting to delete uploaded file at path: $pathToDelete")

                // Attempt to delete uploaded file from worker with retries
                deleteUploadedFileWithRetry(pathToDelete, 3, object : DeleteCallback {
                    override fun onComplete(success: Boolean) {
                        Log.d(TAG, "deleteUploadedFile completed success=$success")
                        // Regardless of file deletion success, delete the created auth user
                        rollbackDeleteUser()
                    }
                })
            }
    }

    // Delete with exponential backoff retries (attemptsLeft times)
    private fun deleteUploadedFileWithRetry(path: String, attemptsLeft: Int, callback: DeleteCallback) {
        val client = OkHttpClient()

        // Build a form body with 'path' as form-data (multipart)
        val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("path", path)
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .delete(multipartBody) // send DELETE with body containing form-data
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "delete request failed: ${e.message}; attemptsLeft=$attemptsLeft")
                if (attemptsLeft > 1) {
                    // schedule retry with exponential backoff (ms)
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

    // Delete the currently signed-in Firebase user (created during registration) and sign out.
    private fun rollbackDeleteUser() {
        val current = auth.currentUser
        if (current == null) {
            Log.d(TAG, "rollbackDeleteUser: current user null")
            return
        }
        current.delete()
            .addOnCompleteListener {
                // sign out locally
                auth.signOut()
                runOnUiThread {
                    Toast.makeText(this, "Rolled back registration (user deleted)", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { delEx ->
                runOnUiThread {
                    Toast.makeText(this, "Rollback: failed to delete user: ${delEx.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun queryFileName(uri: Uri): String? {
        // Try to resolve display name from content resolver; return null if not found
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


}
