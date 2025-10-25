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

        // Register the phone edittext with CountryCodePicker (shows flags & search dialog)
        // Note: we replaced Spinner with CountryCodePicker in layout (id: ccp)
        try {
            binding.ccp.registerCarrierNumberEditText(binding.txtMobileNumber)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mobile EditText with CountryCodePicker: ${e.message}")
            // fallback: still set a length filter to avoid very long input
            binding.txtMobileNumber.filters = arrayOf(InputFilter.LengthFilter(15))
        }

        // Profile image picker: clicking either the photo or the plus launches picker
        binding.profilePicture.setOnClickListener { getContent.launch("image/*") }
        binding.avatarPlus.setOnClickListener { getContent.launch("image/*") }

        // DOB field: disable keyboard and show a themed Material Date Picker dialog on click/focus
        try {
            // Disable keyboard so the MaterialDatePicker is used instead
            binding.txtDOB.inputType = InputType.TYPE_NULL
            binding.txtDOB.isFocusable = false

            val showDobPicker = {
                // show the picker
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

        // Add click wrapper to recolor the CCP dialog views after it opens so text is visible in dark mode
        binding.ccp.setOnClickListener {
            // Debug: dump CCP internal structure to logcat to help identify dialog fields
            dumpCcpInternalStructure()

            // Open the picker dialog (default behavior)
            binding.ccp.performClick()

            // Retry loop: try multiple times (every 120ms) to catch the dialog when it's inflated on slower devices
            val maxAttempts = 10
            var attempt = 0
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    try {
                        val colorOnSurface = resolveThemeColor("colorOnSurface", android.R.color.white)
                        val colorSurface = resolveThemeColor("colorSurface", android.R.color.background_light)

                        val decor = window?.decorView
                        var found = false

                        // First try to recolor CCP internal dialog via reflection (more reliable on some devices)
                        try {
                            val reflOk = tryRecolorCcpInternalDialog()
                            if (reflOk) found = true
                        } catch (_: Exception) {}

                        if (decor != null) {
                            // Attempt to recolor; recolorDialogViewsRecursively will no-op if nothing to change
                            recolorDialogViewsRecursively(decor, colorOnSurface, colorSurface)
                            // If there's a RecyclerView or ListView with children, assume we succeeded
                            found = found || findDialogListOrItems(decor)
                            if (found) Log.d(TAG, "Recolored CCP dialog on attempt $attempt")
                        }

                        attempt++
                        if (!found && attempt < maxAttempts) {
                            handler.postDelayed(this, 120)
                        }
                    } catch (ex: Exception) {
                        Log.w(TAG, "Failed to recolor CCP dialog views: ${ex.message}")
                        attempt++
                        if (attempt < maxAttempts) handler.postDelayed(this, 120)
                    }
                }
            }
            handler.postDelayed(runnable, 120)
        }

    }

    // Small helper to heuristically detect if the dialog list is present (RecyclerView/ListView with children)
    private fun findDialogListOrItems(view: android.view.View): Boolean {
        try {
            when (view) {
                is androidx.recyclerview.widget.RecyclerView -> return view.childCount > 0
                is android.widget.ListView -> return view.childCount > 0
                is android.view.ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        if (findDialogListOrItems(view.getChildAt(i))) return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    // Recursively traverse a view and set text colors for TextView/EditText and tint for ImageView
    private fun recolorDialogViewsRecursively(view: android.view.View, textColor: Int, bgColor: Int) {
        try {
            when (view) {
                is android.widget.EditText -> {
                    view.setTextColor(textColor)
                    view.setHintTextColor(textColor)
                    view.background?.setTint(textColor)
                }
                is android.widget.TextView -> {
                    view.setTextColor(textColor)
                }
                is androidx.recyclerview.widget.RecyclerView -> {
                    // Recolor visible children
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        recolorDialogViewsRecursively(child, textColor, bgColor)
                    }
                }
                is android.widget.ListView -> {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        recolorDialogViewsRecursively(child, textColor, bgColor)
                    }
                }
                is android.view.ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        recolorDialogViewsRecursively(view.getChildAt(i), textColor, bgColor)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // Attempt to find and recolor CCP's internal dialog/popup via reflection. Returns true if something was recolored.
    private fun tryRecolorCcpInternalDialog(): Boolean {
        try {
            val colorOnSurface = resolveThemeColor("colorOnSurface", android.R.color.white)
            val colorSurface = resolveThemeColor("colorSurface", android.R.color.background_light)

            val ccpObj = binding.ccp
            val cls = ccpObj.javaClass

            // Search fields for Dialog, PopupWindow, View, RecyclerView, ListView
            for (f in cls.declaredFields) {
                try {
                    f.isAccessible = true
                    val value = f.get(ccpObj) ?: continue
                    when (value) {
                        is android.app.Dialog -> {
                            val decor = value.window?.decorView
                            if (decor != null) {
                                recolorDialogViewsRecursively(decor, colorOnSurface, colorSurface)
                                return true
                            }
                        }
                        is android.widget.PopupWindow -> {
                            val cv = value.contentView
                            if (cv != null) {
                                recolorDialogViewsRecursively(cv, colorOnSurface, colorSurface)
                                return true
                            }
                        }
                        is android.view.View -> {
                            recolorDialogViewsRecursively(value, colorOnSurface, colorSurface)
                            return true
                        }
                        is androidx.recyclerview.widget.RecyclerView -> {
                            recolorDialogViewsRecursively(value, colorOnSurface, colorSurface)
                            return true
                        }
                        is android.widget.ListView -> {
                            recolorDialogViewsRecursively(value, colorOnSurface, colorSurface)
                            return true
                        }
                    }
                } catch (_: Exception) {}
            }

            // Search methods that return a dialog or view
            for (m in cls.declaredMethods) {
                try {
                    if (m.parameterCount == 0) {
                        m.isAccessible = true
                        val ret = m.invoke(ccpObj) ?: continue
                        when (ret) {
                            is android.app.Dialog -> {
                                val decor = ret.window?.decorView
                                if (decor != null) {
                                    recolorDialogViewsRecursively(decor, colorOnSurface, colorSurface)
                                    return true
                                }
                            }
                            is android.widget.PopupWindow -> {
                                val cv = ret.contentView
                                if (cv != null) {
                                    recolorDialogViewsRecursively(cv, colorOnSurface, colorSurface)
                                    return true
                                }
                            }
                            is android.view.View -> {
                                recolorDialogViewsRecursively(ret, colorOnSurface, colorSurface)
                                return true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return false
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
        // Mobile validation moved to registration click; here we only ensure it's not empty
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

        // Determine filename (fall back to user_id.jpg) and ensure non-null String for Java interop
        val safeFilename: String = (queryFileName(uri) ?: "$user_id.jpg")

        // Build multipart request
        val client = OkHttpClient()
        val mediaType = (contentResolver.getType(uri) ?: "image/jpeg").toMediaTypeOrNull()

        val multipartBody = MultipartBody.Builder()
            .addFormDataPart("file", safeFilename, fileBytes.toRequestBody(mediaType))
            .addFormDataPart("name", safeFilename)
            .addFormDataPart("path", "$user_id/$safeFilename")
            .build()

        val request = Request.Builder()
            .url("https://file-upload-worker.genzopia.workers.dev/")
            .addHeader("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
            .post(multipartBody)
            .build()

        // Show a simple progress toast
        runOnUiThread { Toast.makeText(this@RegisterActivity.applicationContext, "Uploading profile image...", Toast.LENGTH_SHORT).show() }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "upload failed: ${e.message}")
                callback.onFailure(e.message ?: "Network error")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val bodyStrSafe = try { it.body?.string()?.trim() ?: "" } catch (_: Exception) { "" }

                    // Debug logging and toast of worker response
                    Log.d(TAG, "upload response code=${it.code} body=$bodyStrSafe")
                    runOnUiThread { Toast.makeText(this@RegisterActivity, "Worker response: ${if (bodyStrSafe.isEmpty()) "<empty>" else bodyStrSafe}", Toast.LENGTH_LONG).show() }

                    if (!it.isSuccessful) {
                        val errMsg = "${it.code} ${bodyStrSafe}"
                        callback.onFailure(errMsg)
                        return
                    }

                    if (bodyStrSafe.isEmpty()) {
                        callback.onFailure("Upload succeeded but returned empty URL")
                        return
                    }

                    // Try to extract a URL if the worker returned JSON and also the returned path if present
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
                        // ignore JSON parse errors; fallback to the raw body
                        downloadUrl = bodyStrSafe
                    }

                    if (downloadUrl.isNullOrEmpty()) {
                        callback.onFailure("Upload returned invalid URL")
                        return
                    }

                    // Return both the download URL and any path the worker returned
                    callback.onSuccess(downloadUrl, returnedPath)
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
        val multipartBody = MultipartBody.Builder()
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

    // Resolve a theme color attribute by name (falls back to textColorPrimary and then a given fallback resource)
    private fun resolveThemeColor(attrName: String, fallbackResId: Int): Int {
        val typed = android.util.TypedValue()
        val attrId = resources.getIdentifier(attrName, "attr", packageName)
        if (attrId != 0 && theme.resolveAttribute(attrId, typed, true)) {
            return if (typed.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, typed.resourceId) else typed.data
        }
        // Fallback to android:textColorPrimary if available
        if (theme.resolveAttribute(android.R.attr.textColorPrimary, typed, true)) {
            return if (typed.resourceId != 0) androidx.core.content.ContextCompat.getColor(this, typed.resourceId) else typed.data
        }
        return androidx.core.content.ContextCompat.getColor(this, fallbackResId)
    }

    // Debug helper: dump CCP internal fields and methods to logcat to help tailor recolor logic
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
