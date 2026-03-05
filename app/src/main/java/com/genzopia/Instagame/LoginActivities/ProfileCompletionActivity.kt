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
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.R
import com.genzopia.Instagame.databinding.ActivityProfileCompletionBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.*

class ProfileCompletionActivity : AppCompatActivity() {
    private val TAG = "ProfileCompletion"
    
    private lateinit var binding: ActivityProfileCompletionBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    
    private var needsDob = false
    private var needsMobile = false
    private var needsFullName = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileCompletionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        
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
                // Update isverified in database
                database.reference.child("users")
                    .child(user.uid)
                    .child("isverified")
                    .setValue(true)
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "Email verified successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Now check for missing profile data
                        checkMissingProfileData(user.uid)
                    }
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
        
        database.reference.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    binding.progressBar.visibility = View.GONE
                    
                    if (!snapshot.exists()) {
                        // User data doesn't exist, need all fields
                        needsDob = true
                        needsMobile = true
                        needsFullName = true
                        showProfileCompletionUI()
                        return
                    }
                    
                    val dob = snapshot.child("date_of_birth").getValue(String::class.java)
                    val mobile = snapshot.child("mobile_no").getValue(String::class.java)
                    val fullName = snapshot.child("full_name").getValue(String::class.java)
                    
                    needsDob = dob.isNullOrEmpty()
                    needsMobile = mobile.isNullOrEmpty() || mobile == "-1"
                    needsFullName = fullName.isNullOrEmpty()
                    
                    if (needsDob || needsMobile || needsFullName) {
                        showProfileCompletionUI()
                    } else {
                        // Profile is complete, go to main activity
                        goToMainActivity()
                    }
                }
                
                override fun onCancelled(error: DatabaseError) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@ProfileCompletionActivity,
                        "Failed to load profile data: ${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            })
    }
    
    private fun showProfileCompletionUI() {
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
        
        val updates = mutableMapOf<String, Any>()
        
        if (needsFullName) {
            val fullName = binding.txtFullName.text?.toString()?.trim()
            if (fullName.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
                return
            }
            updates["full_name"] = fullName
        }
        
        if (needsDob) {
            val dob = binding.txtDOB.text?.toString()?.trim()
            if (dob.isNullOrEmpty()) {
                Toast.makeText(this, "Please select your date of birth", Toast.LENGTH_SHORT).show()
                return
            }
            updates["date_of_birth"] = dob
        }
        
        if (needsMobile) {
            val mobile = binding.txtMobileNumber.text?.toString()?.trim()
            if (mobile.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter your mobile number", Toast.LENGTH_SHORT).show()
                return
            }
            updates["mobile_no"] = mobile
        }
        
        if (updates.isEmpty()) {
            goToMainActivity()
            return
        }
        
        binding.progressBar.visibility = View.VISIBLE
        binding.btnComplete.isEnabled = false
        
        database.reference.child("users").child(userId)
            .updateChildren(updates)
            .addOnSuccessListener {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                goToMainActivity()
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                binding.btnComplete.isEnabled = true
                Toast.makeText(
                    this,
                    "Failed to update profile: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
    
    private fun goToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
