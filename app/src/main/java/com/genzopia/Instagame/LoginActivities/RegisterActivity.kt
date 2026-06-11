package com.genzopia.Instagame.LoginActivities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.common.BaseActivity
import androidx.core.content.FileProvider
import com.genzopia.Instagame.analytics.InstagameAnalytics
import com.genzopia.Instagame.analytics.SessionTracker
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class RegisterActivity : BaseActivity(), AvatarBottomSheetFragment.Listener {

    private val TAG = "RegisterActivity"

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var selectedImageUri: Uri? = null
    private var registerBtnOriginalText: CharSequence? = null
    private var cameraTempUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profilePicture.setImageURI(it)
            binding.avatarPlus.visibility = View.GONE
            InstagameAnalytics.trackRegisterPhotoSelected("gallery")
        }
    }

    private val takePicture = registerForActivityResult(TakePicture()) { success: Boolean ->
        if (success && cameraTempUri != null) {
            selectedImageUri = cameraTempUri
            binding.profilePicture.setImageURI(cameraTempUri)
            binding.avatarPlus.visibility = View.GONE
        } else {
            Log.d(TAG, "takePicture canceled or failed")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        InstagameAnalytics.trackRegisterScreenViewed()
        SessionTracker.onScreenChanged("register")

        binding.btnRegister.isEnabled = true
        registerBtnOriginalText = binding.btnRegister.text

        // Verification UI is removed — hide those views if they exist in the layout

       

        binding.txtMobileNumber.filters = arrayOf(InputFilter.LengthFilter(10))

        binding.profilePicture.setOnClickListener { getContent.launch("image/*") }
        binding.avatarPlus.setOnClickListener { getContent.launch("image/*") }
        binding.btnChooseAvatar.setOnClickListener {
//            AvatarBottomSheetFragment().show(supportFragmentManager, "avatarPicker")
            Toast.makeText(this, "Comming Soon", Toast.LENGTH_SHORT).show()
        }

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
                    Log.w(TAG, "DatePickerDialog failed: ${e.message}")
                }
            }
            binding.txtDOB.setOnClickListener { showDobPicker() }
            binding.txtDOB.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDobPicker() }
        } catch (e: Exception) {
            Log.w(TAG, "DOB picker setup failed: ${e.message}")
        }

        binding.btnRegister.setOnClickListener {
            val email = binding.txtRegisterEmailAddress.text?.toString()?.trim() ?: ""
            val password = binding.txtRegisterPass.text?.toString() ?: ""
            val confirmPassword = binding.txtRegisterConfirmPass.text?.toString() ?: ""
            val fullName = binding.txtFullName.text?.toString() ?: ""
            val dob = binding.txtDOB.text?.toString() ?: ""
            val mobileNo = binding.txtMobileNumber.text?.toString() ?: ""

            if (!validateInputs(email, password, confirmPassword, fullName, dob, mobileNo)) return@setOnClickListener

            InstagameAnalytics.trackRegisterAttempted()

            binding.progressTop.visibility = View.VISIBLE
            binding.btnRegisterProgress.visibility = View.VISIBLE
            registerBtnOriginalText = registerBtnOriginalText ?: binding.btnRegister.text
            binding.btnRegister.text = ""
            binding.btnRegister.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser ?: return@addOnCompleteListener
                        uploadProfileImage(user.uid, email, fullName, dob, mobileNo, object : UploadCallback {
                            override fun onSuccess(downloadUrl: String, uploadedPath: String?, photoId: String) {
                                runOnUiThread {
                                    binding.progressTop.visibility = View.GONE
                                    binding.btnRegisterProgress.visibility = View.GONE
                                    binding.btnRegister.text = registerBtnOriginalText
                                }
                                // Identify user immediately after account creation
                                InstagameAnalytics.identifyUser(user.uid, fullName, email, downloadUrl, "email")
                                InstagameAnalytics.trackRegisterSuccess(user.uid, fullName)
                                saveUserToDatabaseWithRollback(user.uid, email, fullName, dob, mobileNo, downloadUrl, uploadedPath, photoId)
                            }
                            override fun onFailure(message: String) {
                                runOnUiThread {
                                    Toast.makeText(this@RegisterActivity, "Upload failed: $message", Toast.LENGTH_LONG).show()
                                    binding.progressTop.visibility = View.GONE
                                    binding.btnRegisterProgress.visibility = View.GONE
                                    binding.btnRegister.text = registerBtnOriginalText
                                    binding.btnRegister.isEnabled = true
                                }
                                rollbackDeleteUser()
                            }
                        })
                    } else {
                        val msg = task.exception?.message ?: "Registration failed"
                        runOnUiThread {
                            InstagameAnalytics.trackRegisterFailed(msg)
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            binding.progressTop.visibility = View.GONE
                            binding.btnRegisterProgress.visibility = View.GONE
                            binding.btnRegister.text = registerBtnOriginalText
                            binding.btnRegister.isEnabled = true
                        }
                    }
                }
        }

        binding.txtSignInInstead.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private fun validateInputs(
        email: String, password: String, confirmPassword: String,
        fullName: String, dob: String, mobileNo: String
    ): Boolean {
        if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() ||
            fullName.isEmpty() || dob.isEmpty() || mobileNo.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (mobileNo.length != 10) {
            Toast.makeText(this, "Mobile number must be 10 digits", Toast.LENGTH_SHORT).show()
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

    // ── Callbacks ─────────────────────────────────────────────────────────────

    private interface UploadCallback {
        fun onSuccess(downloadUrl: String, uploadedPath: String?, photoId: String)
        fun onFailure(message: String)
    }

    private interface DeleteCallback {
        fun onComplete(success: Boolean)
    }

    // ── Upload profile image ──────────────────────────────────────────────────

    private fun uploadProfileImage(
        user_id: String, email: String, fullName: String,
        dob: String, mobileNo: String, callback: UploadCallback
    ) {
        val uri = selectedImageUri ?: run { callback.onFailure("No image selected"); return }
        val inputStream = contentResolver.openInputStream(uri) ?: run { callback.onFailure("Failed to read selected image"); return }

        val fileBytes = try {
            inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            callback.onFailure("Failed to read image: ${e.message}")
            return
        }

        val ext = (queryFileName(uri) ?: "$user_id.jpg").substringAfterLast('.', "jpg")
        val photoId = "${user_id}_${System.currentTimeMillis()}"
        val safeFilename = "$photoId.$ext"
        val r2Path = "instagame/$user_id"

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
            .addFormDataPart("path", r2Path)
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .post(multipartBody)
            .build()

        runOnUiThread { Toast.makeText(applicationContext, "Uploading profile image...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "upload failed: ${e.message}")
                runOnUiThread { binding.btnRegister.isEnabled = true }
                callback.onFailure(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = try { it.body?.string()?.trim() ?: "" } catch (_: Exception) { "" }
                    Log.d(TAG, "upload response code=${it.code} body=$body")
                    runOnUiThread { Toast.makeText(this@RegisterActivity, "Worker response: ${body.ifEmpty { "<empty>" }}", Toast.LENGTH_LONG).show() }

                    if (!it.isSuccessful) {
                        runOnUiThread { binding.btnRegister.isEnabled = true }
                        callback.onFailure("${it.code} $body")
                        return
                    }
                    if (body.isEmpty()) {
                        runOnUiThread { binding.btnRegister.isEnabled = true }
                        callback.onFailure("Upload succeeded but returned empty URL")
                        return
                    }

                    var downloadUrl: String?
                    var returnedPath: String? = null
                    try {
                        if (body.trimStart().startsWith("{")) {
                            val obj = org.json.JSONObject(body)
                            var key = obj.optString("key", "")
                            // Fix: worker sometimes returns key with filename duplicated
                            // e.g. "instagame/uid/file.jpg/file.jpg" → "instagame/uid/file.jpg"
                            if (key.isNotEmpty()) {
                                val parts = key.split("/")
                                if (parts.size >= 2 && parts[parts.size - 1] == parts[parts.size - 2]) {
                                    key = parts.dropLast(1).joinToString("/")
                                    Log.d(TAG, "Fixed duplicate key: $key")
                                }
                            }
                            downloadUrl = when {
                                key.isNotEmpty() -> "https://file-upload-worker.genzopia.workers.dev/?key=$key"
                                obj.has("url") -> obj.optString("url")
                                obj.has("link") -> obj.optString("link")
                                obj.has("file") -> obj.optString("file")
                                obj.has("location") -> obj.optString("location")
                                else -> body
                            }
                            val pathStr = if (key.isNotEmpty()) key else obj.optString("path", "")
                            if (pathStr.isNotEmpty()) returnedPath = pathStr
                        } else {
                            downloadUrl = body
                        }
                    } catch (_: Exception) {
                        downloadUrl = body
                    }

                    if (downloadUrl.isNullOrEmpty()) {
                        runOnUiThread { binding.btnRegister.isEnabled = true }
                        callback.onFailure("Upload returned invalid URL")
                        return
                    }

                    callback.onSuccess(downloadUrl, returnedPath ?: r2Path, photoId)
                }
            }
        })
    }

    // ── Save to database ──────────────────────────────────────────────────────

    private fun saveUserToDatabaseWithRollback(
        user_id: String, email: String, fullName: String, dob: String,
        mobileNo: String, profilePhotoUrl: String, uploadedPath: String?, photoId: String
    ) {
        val user = User(user_id, email, fullName, dob, mobileNo)
        user.profile_photo_url = profilePhotoUrl
        user.profile_photo_id = photoId

        database.reference.child("users").child(user_id)
            .setValue(user)
            .addOnSuccessListener {
                runOnUiThread {
                    binding.progressTop.visibility = View.GONE
                    binding.btnRegisterProgress.visibility = View.GONE
                    binding.btnRegister.text = registerBtnOriginalText
                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                    // Show privacy policy on first launch; skip if already accepted
                    val next = if (PrivacyPolicyActivity.hasAccepted(this)) {
                        Intent(this, MainActivity::class.java)
                    } else {
                        PrivacyPolicyActivity.newIntent(this)
                    }
                    startActivity(next)
                    finish()
                }
            }
            .addOnFailureListener { dbEx ->
                runOnUiThread {
                    Toast.makeText(this, "Failed to save user data: ${dbEx.message}. Rolling back...", Toast.LENGTH_LONG).show()
                }
                val pathToDelete = when {
                    !uploadedPath.isNullOrBlank() -> uploadedPath
                    profilePhotoUrl.contains("/") -> "instagame/$user_id/${profilePhotoUrl.substringAfterLast('/')}"
                    else -> "instagame/$user_id/$profilePhotoUrl"
                }
                deleteUploadedFileWithRetry(pathToDelete, 3, object : DeleteCallback {
                    override fun onComplete(success: Boolean) {
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

    // ── Delete uploaded file with retry ───────────────────────────────────────

    private fun deleteUploadedFileWithRetry(path: String, attemptsLeft: Int, callback: DeleteCallback) {
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("path", path)
            .build()
        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .delete(multipartBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (attemptsLeft > 1) {
                    val delay = (1000L * Math.pow(2.0, (3 - attemptsLeft).toDouble())).toLong()
                    Handler(Looper.getMainLooper()).postDelayed({ deleteUploadedFileWithRetry(path, attemptsLeft - 1, callback) }, delay)
                } else callback.onComplete(false)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        callback.onComplete(true)
                    } else if (attemptsLeft > 1) {
                        val delay = (1000L * Math.pow(2.0, (3 - attemptsLeft).toDouble())).toLong()
                        Handler(Looper.getMainLooper()).postDelayed({ deleteUploadedFileWithRetry(path, attemptsLeft - 1, callback) }, delay)
                    } else callback.onComplete(false)
                }
            }
        })
    }

    // ── Rollback user creation ────────────────────────────────────────────────

    private fun rollbackDeleteUser() {
        val current = auth.currentUser
        val resetUi = {
            runOnUiThread {
                binding.progressTop.visibility = View.GONE
                binding.btnRegisterProgress.visibility = View.GONE
                binding.btnRegister.text = registerBtnOriginalText
                binding.btnRegister.isEnabled = true
            }
        }
        if (current != null) {
            current.delete()
                .addOnCompleteListener {
                    auth.signOut()
                    resetUi()
                    runOnUiThread { Toast.makeText(this, "Rolled back registration", Toast.LENGTH_SHORT).show() }
                }
                .addOnFailureListener { e ->
                    auth.signOut()
                    resetUi()
                    runOnUiThread { Toast.makeText(this, "Rollback error: ${e.message}", Toast.LENGTH_LONG).show() }
                }
        } else {
            auth.signOut()
            resetUi()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    // ── AvatarBottomSheetFragment.Listener ────────────────────────────────────

    override fun onAvatarSelected(resId: Int) {
        try {
            val uri = drawableResToCacheUri(resId) ?: return
            selectedImageUri = uri
            runOnUiThread { binding.profilePicture.setImageURI(uri); binding.avatarPlus.visibility = View.GONE }
        } catch (e: Exception) {
            Log.w(TAG, "onAvatarSelected failed: ${e.message}")
        }
    }

    override fun onChooseFromGallery() {
        getContent.launch("image/*")
    }

    override fun onTakePhoto() {
        try {
            val avatarsDir = File(cacheDir, "avatars").apply { if (!exists()) mkdirs() }
            val file = File.createTempFile("avatar_${System.currentTimeMillis()}", ".jpg", avatarsDir)
            cameraTempUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            cameraTempUri?.let { takePicture.launch(it) }
                ?: Toast.makeText(this, "Failed to prepare camera capture", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "onTakePhoto failed: ${e.message}")
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawableResToCacheUri(resId: Int): Uri? {
        return try {
            val bmp = BitmapFactory.decodeResource(resources, resId)
            val avatarsDir = File(cacheDir, "avatars").apply { if (!exists()) mkdirs() }
            val file = File(avatarsDir, "avatar_${resId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        } catch (e: Exception) {
            Log.w(TAG, "drawableResToCacheUri failed: ${e.message}")
            null
        }
    }
}
