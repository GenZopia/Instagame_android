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

import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.databinding.ActivityLoginBinding;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.genzopia.Instagame.gateway.ProfileStatusResponse;
import com.genzopia.Instagame.gateway.UserProfileDTO;
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

/**
 * LoginActivity — all profile reads route through the Gateway.
 * No direct Firebase Realtime Database calls remain here.
 */
public class LoginActivity extends BaseActivity {

    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private final int RC_SIGN_IN = 11;
    private NotificationPermissionManager notificationPermissionManager;
    private String sharedPrefFile = "LoginPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        FirebaseApp.initializeApp(this);

        notificationPermissionManager = new NotificationPermissionManager(this);

        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginScreenViewed();
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("login");

        mAuth = FirebaseAuth.getInstance();

        // Pre-fill saved credentials
        Context appCtx = getApplicationContext();
        String savedEmail = appCtx.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                .getString("email", "");
        String savedPassword = appCtx.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                .getString("password", "");
        binding.txtEmailAddress.setText(savedEmail);
        binding.txtPassword.setText(savedPassword);

        // Theme
        EditText passwordEditText = findViewById(R.id.txtPassword);
        ImageView togglePassword = findViewById(R.id.togglePassword);
        TextView txtForgotPassword = findViewById(R.id.txtForgotPassword);
        TextView txtSignUpNow = findViewById(R.id.txtSignUpNow);

        boolean dark = isDarkMode(this);
        togglePassword.setColorFilter(dark ? Color.WHITE : Color.BLACK);
        txtForgotPassword.setTextColor(dark ? Color.WHITE : Color.BLACK);
        txtSignUpNow.setTextColor(dark ? Color.WHITE : Color.BLACK);

        txtForgotPassword.setOnClickListener(v -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackForgotPasswordTapped();
            startActivity(new Intent(LoginActivity.this, ForgotPassword.class));
        });

        txtSignUpNow.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));

        binding.togglePassword.setVisibility(View.GONE);
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                togglePassword.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
        togglePassword.setOnClickListener(v -> togglePasswordVisibility(passwordEditText, togglePassword));

        // ── Email / password login ─────────────────────────────────────────
        binding.btnLoginNow.setOnClickListener(v -> {
            String email = binding.txtEmailAddress.getText().toString().trim();
            String pass  = binding.txtPassword.getText().toString();

            if (email.isEmpty()) { binding.txtEmailAddress.setError("Please Enter Email"); return; }
            if (pass.isEmpty())  { binding.txtPassword.setError("Please Enter Password"); return; }

            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginMethodSelected("email");
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginAttempted("email");
            binding.btnLoginNow.setText("Logging In...");
            binding.btnLoginNow.startAnimation(AnimationUtils.loadAnimation(this, R.anim.text_fade));

            mAuth.signInWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser u = mAuth.getCurrentUser();
                    if (u == null) return;

                    appCtx.getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE).edit()
                            .putString("email", email)
                            .putString("password", pass)
                            .apply();

                    // Warm the image loader token so profile photos load immediately
                    com.genzopia.Instagame.glide.GlideImageLoader.warmToken();

                    // Identify user via gateway profile fetch (no direct Firebase DB read)
                    new Thread(() -> {
                        try {
                            retrofit2.Response<UserProfileDTO> resp =
                                    GatewayClient.INSTANCE.getCallApi().getMyProfile().execute();
                            if (resp.isSuccessful() && resp.body() != null) {
                                UserProfileDTO p = resp.body();
                                // /profile/retrieve returns image bytes directly — safe for Glide
                                String photoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils
                                        .retrieveUrl(u.getUid());
                                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                        .trackLoginSuccess("email", u.getUid(),
                                                p.getFull_name() != null ? p.getFull_name() : "");
                                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                        .identifyUser(u.getUid(),
                                                p.getFull_name() != null ? p.getFull_name() : "",
                                                email, photoUrl, "email");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Profile fetch after email login failed: " + e.getMessage());
                        }
                    }).start();

                    checkUserStatusAndNavigate(u.getUid());                } else {
                    String errMsg = task.getException() != null ? task.getException().getMessage() : "unknown";
                    com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                            .trackLoginFailed("email", errMsg != null ? errMsg : "unknown");
                    Toast.makeText(this, "Login failed. Please check your credentials.", Toast.LENGTH_SHORT).show();
                    binding.btnLoginNow.clearAnimation();
                    binding.btnLoginNow.setText("Login");
                }
            });
        });

        // ── Google Sign-In ─────────────────────────────────────────────────
        if (mAuth.getCurrentUser() != null) {
            checkUserStatusAndNavigate(mAuth.getCurrentUser().getUid());
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.loginBtn).setOnClickListener(view -> {
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginMethodSelected("google");
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackLoginAttempted("google");
            startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
        });
    }

    // ── Google credential exchange ─────────────────────────────────────────

    void authWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Google credential exchange failed", task.getException());
                Toast.makeText(this, "Google sign-in failed. Try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) return;

            String emailAddress  = user.getEmail();
            String fullName      = user.getDisplayName();
            // /profile/retrieve returns image bytes directly (google redirect or worker proxy)
            String photoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.retrieveUrl(user.getUid());

            // Track and identify via Amplitude
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                    .trackLoginSuccess("google", user.getUid(), fullName != null ? fullName : "");
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                    .identifyUser(user.getUid(), fullName != null ? fullName : "",
                            emailAddress != null ? emailAddress : "", photoUrl, "google");

            getApplicationContext()
                    .getSharedPreferences(sharedPrefFile, Context.MODE_PRIVATE)
                    .edit().putString("email", emailAddress).apply();

            // Warm image loader token before navigating
            com.genzopia.Instagame.glide.GlideImageLoader.warmToken();

            // Profile creation/update is handled server-side by /auth/google endpoint.
            // Just check completion status via gateway.
            checkUserStatusAndNavigate(user.getUid());
        });
    }

    // ── Profile completion check via gateway ──────────────────────────────

    /**
     * Calls GET /auth/profile-status/{uid} to decide whether to show
     * ProfileCompletionActivity or navigate to MainActivity.
     * No direct Firebase Realtime Database read.
     */
    void checkUserStatusAndNavigate(String uid) {
        new Thread(() -> {
            try {
                retrofit2.Response<ProfileStatusResponse> resp =
                        GatewayClient.INSTANCE.getCallApi()
                                .getProfileStatus(uid)
                                .execute();

                runOnUiThread(() -> {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        // If gateway unreachable, fall through to ProfileCompletion as safe default
                        Log.w(TAG, "profile-status call failed: HTTP " + resp.code());
                        startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                        finish();
                        return;
                    }

                    ProfileStatusResponse status = resp.body();

                    if (!status.getExists() || status.getNeedsCompletion()) {
                        startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                        finish();
                        return;
                    }

                    // Request notification permission if eligible
                    if (notificationPermissionManager.shouldRequestPermission()) {
                        notificationPermissionManager.requestPermission(
                                LoginActivity.this,
                                NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION);
                    }

                    if (PrivacyPolicyActivity.hasAccepted(LoginActivity.this)) {
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    } else {
                        startActivity(PrivacyPolicyActivity.newIntent(LoginActivity.this));
                    }
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "checkUserStatusAndNavigate error: " + e.getMessage());
                runOnUiThread(() -> {
                    startActivity(new Intent(LoginActivity.this, ProfileCompletionActivity.class));
                    finish();
                });
            }
        }).start();
    }

    // ── Permission result ──────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @androidx.annotation.NonNull String[] permissions,
            @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            notificationPermissionManager.handlePermissionResult(granted);
            if (!granted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationPermissionManager.markPermanentlyDenied();
                }
            }
            if (granted) FCMTokenManager.INSTANCE.registerToken(this);
        }
    }

    // ── Activity result (Google Sign-In) ───────────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                authWithGoogle(account.getIdToken());
            } else {
                Toast.makeText(this, "Google account data is null", Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            switch (e.getStatusCode()) {
                case GoogleSignInStatusCodes.SIGN_IN_CANCELLED:
                    Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show(); break;
                case GoogleSignInStatusCodes.SIGN_IN_FAILED:
                    Toast.makeText(this, "Google sign-in failed. Try again.", Toast.LENGTH_SHORT).show(); break;
                case GoogleSignInStatusCodes.NETWORK_ERROR:
                    Toast.makeText(this, "Network error. Check your connection.", Toast.LENGTH_SHORT).show(); break;
                default:
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Unexpected error: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private boolean isDarkMode(Context context) {
        int flags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    @SuppressLint("PrivateResource")
    private void togglePasswordVisibility(EditText editText, ImageView toggle) {
        if (editText.getTransformationMethod() == PasswordTransformationMethod.getInstance()) {
            editText.setTransformationMethod(null);
            toggle.setImageResource(com.google.android.material.R.drawable.design_ic_visibility);
        } else {
            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            toggle.setImageResource(com.google.android.material.R.drawable.design_ic_visibility_off);
        }
    }
}
