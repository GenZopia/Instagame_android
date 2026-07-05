package com.genzopia.Instagame.LoginActivities

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.core.content.FileProvider
import com.genzopia.Instagame.BuildConfig
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.analytics.InstagameAnalytics
import com.genzopia.Instagame.analytics.SessionTracker
import com.genzopia.Instagame.common.BaseActivity
import com.genzopia.Instagame.databinding.ActivityRegisterBinding
import com.genzopia.Instagame.gateway.GatewayClient
import com.genzopia.Instagame.gateway.RegisterRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RegisterActivity : BaseActivity(), AvatarBottomSheetFragment.Listener {

    private val TAG = "RegisterActivity"
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        InstagameAnalytics.trackRegisterScreenViewed()
        SessionTracker.onScreenChanged("register")

        binding.btnRegister.isEnabled = true
        registerBtnOriginalText = binding.btnRegister.text
        binding.txtMobileNumber.filters = arrayOf(InputFilter.LengthFilter(10))

        binding.profilePicture.setOnClickListener { getContent.launch("image/*") }
        binding.avatarPlus.setOnClickListener { getContent.launch("image/*") }
        binding.btnChooseAvatar.setOnClickListener {
            Toast.makeText(this, "Coming Soon", Toast.LENGTH_SHORT).show()
        }

        // DOB picker
        try {
            binding.txtDOB.inputType = InputType.TYPE_NULL
            binding.txtDOB.isFocusable = false
            val showDob = {
                val now = Calendar.getInstance()
                val dpd = DatePickerDialog(this, { _, y, m, d ->
                    binding.txtDOB.setText(getString(com.genzopia.Instagame.R.string.dob_format, d, m + 1, y))
                }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH))
                dpd.datePicker.maxDate = System.currentTimeMillis()
                dpd.show()
            }
            binding.txtDOB.setOnClickListener { showDob() }
            binding.txtDOB.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDob() }
        } catch (e: Exception) {
            Log.w(TAG, "DOB picker setup failed: ${e.message}")
        }

        binding.btnRegister.setOnClickListener {
            val email    = binding.txtRegisterEmailAddress.text?.toString()?.trim() ?: ""
            val password = binding.txtRegisterPass.text?.toString() ?: ""
            val confirm  = binding.txtRegisterConfirmPass.text?.toString() ?: ""
            val name     = binding.txtFullName.text?.toString() ?: ""
            val dob      = binding.txtDOB.text?.toString() ?: ""
            val mobile   = binding.txtMobileNumber.text?.toString() ?: ""

            if (!validate(email, password, confirm, name, dob, mobile)) return@setOnClickListener
            InstagameAnalytics.trackRegisterAttempted()
            setLoading(true)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Create user + profile server-side
                    val resp = GatewayClient.api.register(RegisterRequest(email, password, name, dob, mobile))

                    if (!resp.isSuccessful) {
                        val msg = if (resp.code() == 409) "Email already in use"
                                  else "Registration failed (${resp.code()})"
                        withContext(Dispatchers.Main) {
                            InstagameAnalytics.trackRegisterFailed(msg)
                            Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                            setLoading(false)
                        }
                        return@launch
                    }

                    val body = resp.body()!!
                    val uid  = body.userId

                    // 2. Sign in locally with the custom token the gateway returned
                    try {
                        gmsAwait(auth.signInWithCustomToken(body.idToken))
                    } catch (e: Exception) {
                        Log.w(TAG, "Custom token sign-in failed, falling back: ${e.message}")
                        gmsAwait(auth.signInWithEmailAndPassword(email, password))
                    }

                    // 3. Upload profile photo (now authenticated)
                    val photoKey = uploadPhoto(uid)

                    withContext(Dispatchers.Main) {
                        val photoUrl = photoKey?.let {
                            com.genzopia.Instagame.utils.ProfilePhotoUtils.toGatewayUrl(it)
                        }
                        InstagameAnalytics.identifyUser(uid, name, email, photoUrl, "email")
                        InstagameAnalytics.trackRegisterSuccess(uid, name)
                        Toast.makeText(this@RegisterActivity, "Registration successful", Toast.LENGTH_SHORT).show()
                        setLoading(false)
                        val next = if (PrivacyPolicyActivity.hasAccepted(this@RegisterActivity))
                            Intent(this@RegisterActivity, MainActivity::class.java)
                        else PrivacyPolicyActivity.newIntent(this@RegisterActivity)
                        startActivity(next)
                        finish()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Registration error: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        val msg = e.message ?: "Registration failed"
                        InstagameAnalytics.trackRegisterFailed(msg)
                        Toast.makeText(this@RegisterActivity, msg, Toast.LENGTH_LONG).show()
                        setLoading(false)
                    }
                    auth.signOut()
                }
            }
        }

        binding.txtSignInInstead.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    private fun validate(email: String, password: String, confirm: String,
                         name: String, dob: String, mobile: String): Boolean {
        if (email.isEmpty() || password.isEmpty() || confirm.isEmpty() ||
            name.isEmpty() || dob.isEmpty() || mobile.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show(); return false
        }
        if (mobile.length != 10) {
            Toast.makeText(this, "Mobile number must be 10 digits", Toast.LENGTH_SHORT).show(); return false
        }
        if (password != confirm) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show(); return false
        }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select a profile picture", Toast.LENGTH_SHORT).show(); return false
        }
        return true
    }

    private fun setLoading(on: Boolean) {
        binding.progressTop.visibility       = if (on) View.VISIBLE else View.GONE
        binding.btnRegisterProgress.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnRegister.text     = if (on) "" else registerBtnOriginalText
        binding.btnRegister.isEnabled = !on
    }

    // ── Photo upload ────────────────────────────────────────────────────────────

    private suspend fun uploadPhoto(uid: String): String? {
        val uri   = selectedImageUri ?: return null
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) { null } ?: return null

        val name      = queryName(uri) ?: "${uid}_${System.currentTimeMillis()}.jpg"
        val mediaType = (contentResolver.getType(uri) ?: "image/jpeg").toMediaTypeOrNull()
        val body      = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", name, bytes.toRequestBody(mediaType)).build()

        val idToken = try {
            val task = auth.currentUser?.getIdToken(false)
            if (task != null) gmsAwait(task).token else null
        } catch (e: Exception) { null }

        val req = Request.Builder()
            .url("${BuildConfig.GATEWAY_BASE_URL.trimEnd('/')}/upload/profile-photo")
            .addHeader("x-api-key", BuildConfig.GATEWAY_API_KEY)
            .apply { if (idToken != null) addHeader("Authorization", "Bearer $idToken") }
            .post(body).build()

        return try {
            val response = withContext(Dispatchers.IO) {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                    .newCall(req).execute()
            }
            response.use {
                val rb = it.body?.string()?.trim() ?: ""
                if (!it.isSuccessful) return null
                org.json.JSONObject(rb).optString("key", "").takeIf { k -> k.isNotEmpty() }
            }
        } catch (e: Exception) { null }
    }

    // ── GMS Task bridge (no kotlinx-coroutines-play-services needed) ────────────

    private suspend fun <T> gmsAwait(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnCompleteListener { t ->
                if (t.isSuccessful) cont.resume(t.result)
                else cont.resumeWithException(t.exception ?: Exception("Task failed"))
            }
        }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun queryName(uri: Uri): String? = try {
        contentResolver.query(uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (_: Exception) { null }

    // ── AvatarBottomSheetFragment.Listener ───────────────────────────────────────

    override fun onAvatarSelected(resId: Int) {
        try {
            val bmp  = BitmapFactory.decodeResource(resources, resId)
            val dir  = File(cacheDir, "avatars").apply { mkdirs() }
            val file = File(dir, "avatar_${resId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }
            val uri  = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            selectedImageUri = uri
            binding.profilePicture.setImageURI(uri)
            binding.avatarPlus.visibility = View.GONE
        } catch (e: Exception) { Log.w(TAG, "onAvatarSelected: ${e.message}") }
    }

    override fun onChooseFromGallery() { getContent.launch("image/*") }

    override fun onTakePhoto() {
        try {
            val dir  = File(cacheDir, "avatars").apply { mkdirs() }
            val file = File.createTempFile("avatar_${System.currentTimeMillis()}", ".jpg", dir)
            cameraTempUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            cameraTempUri?.let { takePicture.launch(it) }
                ?: Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "onTakePhoto: ${e.message}")
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }
}
