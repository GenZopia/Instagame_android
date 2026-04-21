package com.genzopia.Instagame.LoginActivities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.FirebaseDatabase;



public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding binding;
    private FirebaseAuth firebaseAuth;
    private ProgressBar progressbar;
    private TextView txtForgotPassword;
    private TextView txtSignUpNow;
    private ImageView togglePassword;

    private GoogleSignInClient mGoogleSignInClient;
    private int RC_SIGN_IN = 11;
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;

    // Retrieve and pre-fill login information
    private String sharedPrefFile = "LoginPrefs";
    private String savedEmail;
    private String savedPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        FirebaseApp.initializeApp(this);

        firebaseAuth = FirebaseAuth.getInstance();

        // Initialize sharedPrefContext and retrieve saved email and password
        Context sharedPrefContext = getApplicationContext();
        savedEmail = sharedPrefContext.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                .getString("email", "");
        savedPassword = sharedPrefContext.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                .getString("password", "");

        EditText passwordEditText = findViewById(R.id.txtPassword);
        togglePassword = findViewById(R.id.togglePassword);
        txtForgotPassword = findViewById(R.id.txtForgotPassword);
        txtSignUpNow = findViewById(R.id.txtSignUpNow);

        txtForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPassword.class);
            startActivity(intent);
        });

        binding.txtEmailAddress.setText(savedEmail);
        binding.txtPassword.setText(savedPassword);

        // Eye Visibility
        if (isDarkMode(this)) {
            togglePassword.setColorFilter(Color.WHITE);
        } else {
            togglePassword.setColorFilter(Color.BLACK);
        }

        txtForgotPassword.setTextColor(isDarkMode(this) ? Color.WHITE : Color.BLACK);
        txtSignUpNow.setTextColor(isDarkMode(this) ? Color.WHITE : Color.BLACK);

        togglePassword.setOnClickListener(v -> togglePasswordVisibility(passwordEditText, togglePassword));

        binding.txtSignUpNow.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        binding.togglePassword.setVisibility(View.GONE);

        // Show/hide password toggle based on text input
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                togglePassword.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        binding.btnLoginNow.setOnClickListener(v -> {
            String email = binding.txtEmailAddress.getText().toString();
            String pass = binding.txtPassword.getText().toString();

            if (!email.isEmpty() && !pass.isEmpty()) {
                binding.btnLoginNow.setText("Logging In...");
                binding.btnLoginNow.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.text_fade));

                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sharedPrefContext.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                .putString("email", email)
                                .putString("password", pass)
                                .apply();
                        
                        // Check if user needs to complete profile or verify email
                        checkUserStatusAndNavigate();
                    } else {
                        Toast.makeText(LoginActivity.this, "Login failed. Please check your credentials.", Toast.LENGTH_SHORT).show();
                        binding.btnLoginNow.clearAnimation();
                        binding.btnLoginNow.setText("Login");
                    }
                });
            } else if (email.isEmpty()) {
                binding.txtEmailAddress.setError("Please Enter Email");
            } else if (pass.isEmpty()) {
                binding.txtPassword.setError("Please Enter Password");
            } else if (pass.length() < 6) {
                Toast.makeText(LoginActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LoginActivity.this, "Incorrect Fields", Toast.LENGTH_SHORT).show();
            }
        });

        // Google Sign-In
        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() != null) {
            goToNextActivity();
        }

        database = FirebaseDatabase.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.loginBtn).setOnClickListener(view -> {
            Intent intent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(intent, RC_SIGN_IN);
        });
    }

    void authWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            String emailAddress = user.getEmail();
                            String fullName = user.getDisplayName();
                            String profilePhotoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
                            String dob = "";  // Will be asked in ProfileCompletionActivity if needed
                            String mobileNumber = user.getPhoneNumber(); // Will be asked in ProfileCompletionActivity if needed

                            // Create the User object with the correct constructor
                            User firebaseUser = new User(
                                    user.getUid(), // userId
                                    emailAddress,
                                    fullName != null ? fullName : "",
                                    dob,
                                    mobileNumber != null ? mobileNumber : "-1"
                            );

                            // Set additional properties
                            firebaseUser.setProfile_photo_url(profilePhotoUrl);

                            // Store the user data in Firebase Realtime Database
                            database.getReference()
                                    .child("users")
                                    .child(user.getUid()) // Use UID instead of email as the key
                                    .setValue(firebaseUser)
                                    .addOnCompleteListener(task1 -> {
                                        if (task1.isSuccessful()) {
                                            getApplicationContext().getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                                    .putString("email", emailAddress)
                                                    .apply();
                                            
                                            // Check if user needs to complete profile
                                            checkUserStatusAndNavigate();
                                        } else {
                                            Toast.makeText(LoginActivity.this, task1.getException().getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        Log.e("err", task.getException().getLocalizedMessage());
                    }
                });
    }

    void goToNextActivity() {
        checkUserStatusAndNavigate();
    }
    
    void checkUserStatusAndNavigate() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(LoginActivity.this, LoginActivity.class));
            finish();
            return;
        }

        // Check if profile data is complete — no email verification check
        database.getReference()
                .child("users")
                .child(currentUser.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        if (!task.getResult().exists()) {
                            startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                            finish();
                            return;
                        }

                        String dob = task.getResult().child("date_of_birth").getValue(String.class);
                        String mobile = task.getResult().child("mobile_no").getValue(String.class);
                        String fullName = task.getResult().child("full_name").getValue(String.class);

                        boolean needsCompletion = (dob == null || dob.isEmpty()) ||
                                (mobile == null || mobile.isEmpty() || mobile.equals("-1")) ||
                                (fullName == null || fullName.isEmpty());

                        if (needsCompletion) {
                            startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                            finish();
                        } else {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    } else {
                        startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                        finish();
                    }
                });
    }

    public static String replacePeriods(String input) {
        // Replace all occurrences of '.' with ','
        return input.replace('.', ',');
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);

            try {
                // This will throw ApiException if sign-in failed or was cancelled
                GoogleSignInAccount account = task.getResult(ApiException.class);

                if (account != null) {
                    // Proceed with Firebase authentication using the ID token
                    authWithGoogle(account.getIdToken());
                } else {
                    Log.e("GoogleSignIn", "Account returned is null");
                    Toast.makeText(this, "Google account data is null", Toast.LENGTH_SHORT).show();
                }

            } catch (ApiException e) {
                int statusCode = e.getStatusCode();

                switch (statusCode) {
                    case GoogleSignInStatusCodes.SIGN_IN_CANCELLED:
                        Log.w("GoogleSignIn", "User cancelled the sign-in");
                        Toast.makeText(this, "Sign-in cancelled by user", Toast.LENGTH_SHORT).show();
                        break;

                    case GoogleSignInStatusCodes.SIGN_IN_FAILED:
                        Log.e("GoogleSignIn", "Sign-in failed");
                        Toast.makeText(this, "Google sign-in failed. Try again.", Toast.LENGTH_SHORT).show();
                        break;

                    case GoogleSignInStatusCodes.NETWORK_ERROR:
                        Log.e("GoogleSignIn", "Network error during sign-in");
                        Toast.makeText(this, "Network error. Check your connection.", Toast.LENGTH_SHORT).show();
                        break;

                    default:
                        Log.e("GoogleSignIn", "Unknown error: " + statusCode + " - " + e.getMessage());
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        break;
                }
            } catch (Exception ex) {
                Log.e("GoogleSignIn", "Unexpected error: " + ex.getMessage());
                Toast.makeText(this, "Unexpected error: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressLint("PrivateResource")
    private void togglePasswordVisibility(EditText passwordEditText, ImageView togglePassword) {
        if (passwordEditText.getTransformationMethod() == PasswordTransformationMethod.getInstance()) {
            // Show password
            passwordEditText.setTransformationMethod(null);
            togglePassword.setImageResource(com.google.android.material.R.drawable.design_ic_visibility);
        } else {
            // Hide password
            passwordEditText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            togglePassword.setImageResource(com.google.android.material.R.drawable.design_ic_visibility_off);
        }
    }
}

