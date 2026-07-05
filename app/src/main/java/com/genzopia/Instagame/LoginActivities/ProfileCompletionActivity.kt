package com.genzopia.Instagame.LoginActivities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.analytics.InstagameAnalytics
import com.genzopia.Instagame.analytics.SessionTracker
import com.genzopia.Instagame.common.BaseActivity
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.R
import com.genzopia.Instagame.databinding.ActivityProfileCompletionBinding
import com.google.firebase.auth.FirebaseAuth
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.*

class ProfileCompletionActivity : BaseActivity() {
    private val TAG = "ProfileCompletion"
    
    private lateinit var binding: ActivityProfileCompletionBinding
    private lateinit var auth: FirebaseAuth
    
    private var needsDob = false
    private var needsMobile = false
    private var needsFullName = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = FirebaseAuth.getInstance()
        
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // No user signed in, go back to login
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        
        // Check if email is verified
        currentUser.reload().addOnCompleteListener { reloadTask ->
            if (!reloadTask.isSuccessful || !currentUser.isEmailVerified) {
                // Email not verified, show verification UI
                showVerificationUI()
            } else {
                // Email verified, check for missing profile data
                checkMissingProfileData(currentUser.uid)
            }
        }
        
        setupUI()
    }
    
    private fun showVerificationUI() {
        binding.tvTitle.text = "Verify Your Email"
        binding.tvSubtitle.text = "Please verify your email to continue"
        binding.layoutDob.visibility = View.GONE
        binding.layoutMobile.visibility = View.GONE
        binding.layoutFullName.visibility = View.GONE
        binding.btnComplete.visibility = View.GONE
        binding.layoutVerification.visibility = View.VISIBLE
        
        binding.btnSendVerification.setOnClickListener {
            sendVerificationEmail()
        }
        
        binding.btnCheckVerification.setOnClickListener {
            checkEmailVerification()
        }
    }
    
    private fun sendVerificationEmail() {
        val user = auth.currentUser ?: return
        
        binding.btnSendVerification.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        
        user.sendEmailVerification()
            .addOnCompleteListener { task ->
                binding.progressBar.visibility = View.GONE
                binding.btnSendVerification.isEnabled = true
                
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Verification email sent to ${user.email}",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send verification email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
    
    private fun checkEmailVerification() {
        val user = auth.currentUser ?: return
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCheckVerification.isEnabled = false
        
        user.reload().addOnCompleteListener { reloadTask ->
            binding.progressBar.visibility = View.GONE
            binding.btnCheckVerification.isEnabled = true
            
            if (!reloadTask.isSuccessful) {
                Toast.makeText(
                    this,
                    "Failed to check verification status",
                    Toast.LENGTH_SHORT
                ).show()
                return@addOnCompleteListener
            }
            
            if (user.isEmailVerified) {
                Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_SHORT).show()
                checkMissingProfileData(user.uid)
            } else {
                Toast.makeText(
                    this,
                    "Email not verified yet. Please check your inbox.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun checkMissingProfileData(userId: String) {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = com.genzopia.Instagame.gateway.GatewayClient.api.getMyProfile()
                binding.progressBar.visibility = View.GONE
                if (resp.isSuccessful && resp.body() != null) {
                    val p = resp.body()!!
                    needsDob = p.full_name.isEmpty() // reuse field check — DOB not in DTO, treat as optional
                    needsMobile = false
                    needsFullName = p.full_name.isEmpty()
                    if (needsFullName) showProfileCompletionUI() else goToMainActivity()
                } else {
                    needsDob = true; needsMobile = true; needsFullName = true
                    showProfileCompletionUI()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ProfileCompletionActivity,
                    "Failed to load profile data: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showProfileCompletionUI() {
        val missing = mutableListOf<String>()
        if (needsFullName) missing.add("full_name")
        if (needsDob) missing.add("date_of_birth")
        if (needsMobile) missing.add("mobile_no")
        InstagameAnalytics.trackProfileCompletionViewed(missing)
        SessionTracker.onScreenChanged("profile_completion")
        binding.tvTitle.text = "Complete Your Profile"
        binding.tvSubtitle.text = "Please provide the following information"
        binding.layoutVerification.visibility = View.GONE
        binding.btnComplete.visibility = View.VISIBLE
        
        // Show/hide fields based on what's missing
        binding.layoutFullName.visibility = if (needsFullName) View.VISIBLE else View.GONE
        binding.layoutDob.visibility = if (needsDob) View.VISIBLE else View.GONE
        binding.layoutMobile.visibility = if (needsMobile) View.VISIBLE else View.GONE
        
        binding.btnComplete.setOnClickListener {
            saveProfileData()
        }
    }
    
    private fun setupUI() {
        // Setup DOB picker
        try {
            binding.txtDOB.inputType = InputType.TYPE_NULL
            binding.txtDOB.isFocusable = false
            val showDobPicker = {
                try {
                    val now = Calendar.getInstance()
                    val dpd = DatePickerDialog(this, { _, y, m, d ->
                        binding.txtDOB.setText(getString(R.string.dob_format, d, m + 1, y))
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
        
        // Setup mobile number
        try {
            binding.ccp.registerCarrierNumberEditText(binding.txtMobileNumber)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mobile EditText with CountryCodePicker: ${e.message}")
            binding.txtMobileNumber.filters = arrayOf(InputFilter.LengthFilter(15))
        }
    }
    
    private fun saveProfileData() {
        val userId = auth.currentUser?.uid ?: return

        val fullName = if (needsFullName) binding.txtFullName.text?.toString()?.trim() else null
        val dob = if (needsDob) binding.txtDOB.text?.toString()?.trim() else null
        val mobile = if (needsMobile) binding.txtMobileNumber.text?.toString()?.trim() else null

        if (needsFullName && fullName.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show(); return
        }
        if (needsDob && dob.isNullOrEmpty()) {
            Toast.makeText(this, "Please select your date of birth", Toast.LENGTH_SHORT).show(); return
        }
        if (needsMobile && mobile.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter your mobile number", Toast.LENGTH_SHORT).show(); return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnComplete.isEnabled = false

        // Update profile via gateway PATCH /users/me
        val req = com.genzopia.Instagame.gateway.UpdateProfileRequest(
            full_name = fullName
        )
        lifecycleScope.launch {
            try {
                val resp = com.genzopia.Instagame.gateway.GatewayClient.api.updateMyProfile(req)
                binding.progressBar.visibility = View.GONE
                if (resp.isSuccessful) {
                    val filled = listOfNotNull(
                        if (fullName != null) "full_name" else null,
                        if (dob != null) "date_of_birth" else null,
                        if (mobile != null) "mobile_no" else null
                    )
                    InstagameAnalytics.trackProfileCompletionSubmitted(filled)
                    if (fullName != null) {
                        InstagameAnalytics.identifyUser(userId, fullName, "", null, "profile_completion")
                    }
                    Toast.makeText(this@ProfileCompletionActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    goToMainActivity()
                } else {
                    binding.btnComplete.isEnabled = true
                    Toast.makeText(this@ProfileCompletionActivity, "Failed to update profile: HTTP ${resp.code()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnComplete.isEnabled = true
                Toast.makeText(this@ProfileCompletionActivity, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun goToMainActivity() {
        // Show privacy policy once; skip if already accepted
        val next = if (PrivacyPolicyActivity.hasAccepted(this)) {
            Intent(this, MainActivity::class.java)
        } else {
            PrivacyPolicyActivity.newIntent(this)
        }
        startActivity(next)
        finish()
    }
}
