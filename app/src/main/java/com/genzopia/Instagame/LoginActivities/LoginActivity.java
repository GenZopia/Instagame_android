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
import com.genzopia.Instagame.utils.FCMTokenManager;
import com.genzopia.Instagame.utils.NotificationPermissionManager;
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

    private NotificationPermissionManager notificationPermissionManager;

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

        notificationPermissionManager = new NotificationPermissionManager(this);

        // Analytics
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginScreenViewed();
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("login");

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
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackForgotPasswordTapped();
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
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginMethodSelected("email");
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginAttempted("email");
                binding.btnLoginNow.setText("Logging In...");
                binding.btnLoginNow.startAnimation(AnimationUtils.loadAnimation(LoginActivity.this, R.anim.text_fade));

                firebaseAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        sharedPrefContext.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                                .putString("email", email)
                                .putString("password", pass)
                                .apply();
                        FirebaseUser u = firebaseAuth.getCurrentUser();
                        if (u != null) {
                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                    .trackLoginSuccess("email", u.getUid(),
                                            u.getDisplayName() != null ? u.getDisplayName() : "");
                            // Identify user in Amplitude — fetch name + photo from DB
                            database.getReference("users").child(u.getUid()).get()
                                    .addOnSuccessListener(snapshot -> {
                                        String name = snapshot.child("full_name").getValue(String.class);
                                        String photoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                                        String sanitizedPhoto = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(photoUrl);
                                        android.util.Log.d("AmplitudeDebug", "Login DB fetch → name='" + name + "' rawPhoto='" + photoUrl + "' sanitized='" + sanitizedPhoto + "'");
                                        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                .identifyUser(u.getUid(),
                                                        name != null ? name : "",
                                                        email,
                                                        sanitizedPhoto,
                                                        "email");
                                    })
                                    .addOnFailureListener(e -> android.util.Log.e("AmplitudeDebug", "Login DB fetch FAILED: " + e.getMessage()));
                        }
                        checkUserStatusAndNavigate();
                    } else {
                        String errMsg = task.getException() != null ? task.getException().getMessage() : "unknown";
                        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                .trackLoginFailed("email", errMsg != null ? errMsg : "unknown");
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
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginMethodSelected("google");
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginAttempted("google");
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
                            String mobileNumber = user.getPhoneNumber();

                            // Check if this user already has data in the database (e.g. registered via email/password)
                            database.getReference()
                                    .child("users")
                                    .child(user.getUid())
                                    .get()
                                    .addOnCompleteListener(fetchTask -> {
                                        if (fetchTask.isSuccessful() && fetchTask.getResult() != null
                                                && fetchTask.getResult().exists()) {
                                            // ── Existing user ──────────────────────────────────────────
                                            // Only fill in fields that are missing/empty — never overwrite
                                            // data the user already set during email/password registration.
                                            com.google.firebase.database.DataSnapshot snap = fetchTask.getResult();

                                            java.util.Map<String, Object> updates = new java.util.HashMap<>();

                                            String existingName = snap.child("full_name").getValue(String.class);
                                            if (existingName == null || existingName.isEmpty()) {
                                                updates.put("full_name", fullName != null ? fullName : "");
                                            }

                                            String existingPhoto = snap.child("profile_photo_url").getValue(String.class);
                                            if ((existingPhoto == null || existingPhoto.isEmpty())
                                                    && !profilePhotoUrl.isEmpty()) {
                                                updates.put("profile_photo_url", profilePhotoUrl);
                                            }

                                            String existingMobile = snap.child("mobile_no").getValue(String.class);
                                            if ((existingMobile == null || existingMobile.isEmpty()
                                                    || existingMobile.equals("-1"))
                                                    && mobileNumber != null && !mobileNumber.isEmpty()) {
                                                updates.put("mobile_no", mobileNumber);
                                            }

                                            // Always ensure email is stored
                                            String existingEmail = snap.child("email").getValue(String.class);
                                            if (existingEmail == null || existingEmail.isEmpty()) {
                                                updates.put("email", emailAddress);
                                            }

                                            if (!updates.isEmpty()) {
                                                database.getReference()
                                                        .child("users")
                                                        .child(user.getUid())
                                                        .updateChildren(updates)
                                                        .addOnCompleteListener(updateTask -> {
                                                            getApplicationContext()
                                                                    .getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                                                                    .edit().putString("email", emailAddress).apply();
                                                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                                    .trackLoginSuccess("google", user.getUid(), fullName != null ? fullName : "");
                                                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                                    .identifyUser(user.getUid(), fullName != null ? fullName : "",
                                                                            emailAddress != null ? emailAddress : "", profilePhotoUrl, "google");
                                                            checkUserStatusAndNavigate();
                                                        });
                                            } else {
                                                getApplicationContext()
                                                        .getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                                                        .edit().putString("email", emailAddress).apply();
                                                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                        .trackLoginSuccess("google", user.getUid(), fullName != null ? fullName : "");
                                                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                        .identifyUser(user.getUid(), fullName != null ? fullName : "",
                                                                emailAddress != null ? emailAddress : "", profilePhotoUrl, "google");
                                                checkUserStatusAndNavigate();
                                            }

                                        } else {
                                            // ── New user (first time Google sign-in) ───────────────────
                                            // No existing data — safe to create a fresh record.
                                            User firebaseUser = new User(
                                                    user.getUid(),
                                                    emailAddress,
                                                    fullName != null ? fullName : "",
                                                    "",   // DOB — collected in ProfileCompletionActivity
                                                    mobileNumber != null ? mobileNumber : "-1"
                                            );
                                            firebaseUser.setProfile_photo_url(profilePhotoUrl);

                                            database.getReference()
                                                    .child("users")
                                                    .child(user.getUid())
                                                    .setValue(firebaseUser)
                                                    .addOnCompleteListener(task1 -> {
                                                        if (task1.isSuccessful()) {
                                                            getApplicationContext()
                                                                    .getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                                                                    .edit().putString("email", emailAddress).apply();
                                                            // Identify new Google user in Amplitude
                                                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                                    .trackLoginSuccess("google", user.getUid(), fullName != null ? fullName : "");
                                                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                                                    .identifyUser(user.getUid(),
                                                                            fullName != null ? fullName : "",
                                                                            emailAddress != null ? emailAddress : "",
                                                                            profilePhotoUrl, "google");
                                                            checkUserStatusAndNavigate();
                                                        } else {
                                                            Toast.makeText(LoginActivity.this,
                                                                    task1.getException().getLocalizedMessage(),
                                                                    Toast.LENGTH_SHORT).show();
                                                        }
                                                    });
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
                            // Request notification permission if eligible (Android 13+)
                            if (notificationPermissionManager.shouldRequestPermission()) {
                                notificationPermissionManager.requestPermission(
                                        LoginActivity.this,
                                        NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION
                                );
                            }
                            // Show privacy policy once; skip if already accepted
                            if (PrivacyPolicyActivity.hasAccepted(LoginActivity.this)) {
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            } else {
                                startActivity(PrivacyPolicyActivity.newIntent(LoginActivity.this));
                            }
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
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            notificationPermissionManager.handlePermissionResult(granted);
            if (!granted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Check for permanent denial
                if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationPermissionManager.markPermanentlyDenied();
                }
            }
            if (granted) {
                FCMTokenManager.INSTANCE.registerToken(this);
            }
        }
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

