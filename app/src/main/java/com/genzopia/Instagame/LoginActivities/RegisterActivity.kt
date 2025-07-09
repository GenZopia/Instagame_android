package com.genzopia.Instagame.LoginActivities

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.genzopia.Instagame.R
import com.genzopia.Instagame.databinding.ActivityRegisterBinding

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference


class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var storageRef: StorageReference
    private lateinit var signin: TextView
    private var selectedImageUri: Uri? = null
    private val IMAGE_PICK_CODE = 1



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK && data != null) {
            // Get the selected image URI
            selectedImageUri = data.data
            // Set the selected image to the circular button
            binding.profilePicture.setImageURI(selectedImageUri)
        }
    }

    private fun isDarkMode(context: RegisterActivity): Boolean {
        val nightModeFlags: Int =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRegisterBinding.inflate(layoutInflater)

        setContentView(binding.root)

        firebaseAuth = FirebaseAuth.getInstance()
        signin = binding.txtSignInInstead

        signin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }




        storageRef = FirebaseStorage.getInstance().reference

        binding.profilePicture.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, IMAGE_PICK_CODE)
        }

        // Add Full name, Mobile no., and profile photo URL (optional) in real-time database
        binding.btnRegister.setOnClickListener {
            val email = binding.txtRegisterEmailAddress.text.toString()
            val pass = binding.txtRegisterPass.text.toString()
            val dob = binding.txtDOB.text.toString()
            val fullName = binding.txtFullName.text.toString()
            val mobileNumber = binding.txtMobileNumber.text.toString()
            val confirmPass = binding.txtRegisterConfirmPass.text.toString()

            if (email.isNotEmpty() && pass.isNotEmpty() && confirmPass.isNotEmpty() && fullName.isNotEmpty() && mobileNumber.isNotEmpty() && dob.isNotEmpty()) {
                if (pass == confirmPass) {
                    // Change button text to "Signing Up..." and start animation
                    binding.btnRegister.text = "Signing Up..."
                    val animation = AnimationUtils.loadAnimation(this, R.anim.text_fade)
                    binding.btnRegister.startAnimation(animation)

                    firebaseAuth.createUserWithEmailAndPassword(email, pass)
                        .addOnCompleteListener { authTask ->
                            if (authTask.isSuccessful) {
                                val userId = firebaseAuth.currentUser?.uid

                                if (selectedImageUri != null) {
                                    val profileImageRef = storageRef.child("users/$email/profile.jpg")
                                    val uploadTask = profileImageRef.putFile(selectedImageUri!!)

                                    uploadTask.continueWithTask { task ->
                                        if (!task.isSuccessful) {
                                            task.exception?.let {
                                                throw it
                                            }
                                        }
                                        profileImageRef.downloadUrl
                                    }.addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val downloadUri = task.result
                                            saveUserToDatabase(fullName, mobileNumber, email, downloadUri.toString(), dob)
                                        } else {
                                            showToastAndResetButton("Failed to upload image")
                                        }
                                    }
                                } else {
                                    // If no image is selected, continue with registration without a profile photo URL
                                    saveUserToDatabase(fullName, mobileNumber, email, null, dob)
                                }
                            } else {
                                showToastAndResetButton("Failed to register")
                            }
                        }
                } else {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUserToDatabase(fullName: String, mobileNumber: String, email: String, profilePhotoUrl: String?, dob: String) {
        val Firebase_login_realtimeDatabase= Firebase_login_realtimeDatabase()
        Firebase_login_realtimeDatabase.create_user(email,fullName,profilePhotoUrl,dob,mobileNumber,false,0,this)
    }

    private fun showToastAndResetButton(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        binding.btnRegister.clearAnimation()
        binding.btnRegister.text = "Register"
    }
}
