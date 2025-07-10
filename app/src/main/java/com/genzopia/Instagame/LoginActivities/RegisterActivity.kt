package com.genzopia.Instagame.LoginActivities

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.MainActivity
import com.genzopia.Instagame.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private var selectedImageUri: Uri? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profilePicture.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        // Date picker for date of birth
        binding.txtDOB.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    binding.txtDOB.setText("$day/${month + 1}/$year")
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Profile image picker
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

    private fun registerUser(
        email: String,
        password: String,
        fullName: String,
        dob: String,
        mobileNo: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user_id = auth.currentUser?.uid ?: return@addOnCompleteListener
                    uploadProfileImage(user_id, email, fullName, dob, mobileNo)
                } else {
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun uploadProfileImage(
        user_id: String,
        email: String,
        fullName: String,
        dob: String,
        mobileNo: String
    ) {
        val storageRef = storage.reference.child("profile_images/$user_id.jpg")
        
        storageRef.putFile(selectedImageUri!!)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    saveUserToDatabase(user_id, email, fullName, dob, mobileNo, downloadUrl.toString())
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to upload profile image", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserToDatabase(
        user_id: String,
        email: String,
        fullName: String,
        dob: String,
        mobileNo: String,
        profilePhotoUrl: String
    ) {
        val user = User(user_id, email, fullName, dob, mobileNo)
        user.profile_photo_url = profilePhotoUrl

        database.reference.child("users").child(user_id)
            .setValue(user)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save user data", Toast.LENGTH_SHORT).show()
            }
    }
}
