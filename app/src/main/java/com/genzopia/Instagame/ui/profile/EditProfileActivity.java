package com.genzopia.Instagame.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.common.BaseActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.genzopia.Instagame.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends BaseActivity {

    private MaterialToolbar topAppBar;
    private TextInputEditText inputFullName, inputBio, inputWebsite, inputPhone;
    private MaterialButton btnSave;
    private ProgressBar progressBar;

    private FirebaseAuth auth;
    private DatabaseReference userRef;
    private ValueEventListener valueListener;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        topAppBar = findViewById(R.id.topAppBar);
        inputFullName = findViewById(R.id.inputFullName);
        inputBio = findViewById(R.id.inputBio);
        inputWebsite = findViewById(R.id.inputWebsite);
        inputPhone = findViewById(R.id.inputPhone);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Edit profile");
        }

        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        auth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);

        String userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
        if (userId == null) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference().child("users").child(userId);

        btnSave.setOnClickListener(v -> saveProfile());

        loadExistingData();
    }

    private void loadExistingData() {
        progressBar.setVisibility(View.VISIBLE);
        valueListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isFinishing()) {
                    String fullName = snapshot.child("full_name").getValue(String.class);
                    String bio = snapshot.child("bio").getValue(String.class);
                    String website = snapshot.child("website").getValue(String.class);
                    String phone = snapshot.child("mobile_no").getValue(String.class);

                    inputFullName.setText(fullName != null ? fullName : "");
                    inputBio.setText(bio != null ? bio : "");
                    inputWebsite.setText(website != null ? website : "");
                    inputPhone.setText(phone != null ? phone : "");
                }
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(EditProfileActivity.this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            }
        };
        userRef.addListenerForSingleValueEvent(valueListener);
    }

    private void saveProfile() {
        String fullName = inputFullName.getText() != null ? inputFullName.getText().toString().trim() : "";
        String bio = inputBio.getText() != null ? inputBio.getText().toString().trim() : "";
        String website = inputWebsite.getText() != null ? inputWebsite.getText().toString().trim() : "";
        String phone = inputPhone.getText() != null ? inputPhone.getText().toString().trim() : "";

        if (android.text.TextUtils.isEmpty(fullName)) {
            inputFullName.setError("Full name required");
            inputFullName.requestFocus();
            return;
        }

        // Normalize website into a final variable for use inside lambda
        final String websiteFinal = (!TextUtils.isEmpty(website) && !website.startsWith("http")) ? ("https://" + website) : website;

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("full_name", fullName);
        updates.put("bio", bio);
        updates.put("website", websiteFinal);
        updates.put("phone", phone);

        userRef.updateChildren(updates).addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            btnSave.setEnabled(true);
            if (task.isSuccessful()) {
                // Update shared prefs if you want quick local access
                sharedPreferences.edit()
                        .putString("full_name", fullName)
                        .putString("bio", bio)
                        .putString("website", websiteFinal)
                        .putString("phone", phone)
                        .apply();

                Toast.makeText(EditProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(EditProfileActivity.this, "Failed to update profile", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && valueListener != null) {
            userRef.removeEventListener(valueListener);
        }
    }
}
