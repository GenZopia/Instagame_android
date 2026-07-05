package com.genzopia.Instagame.ui.profile;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.genzopia.Instagame.gateway.UpdateProfileRequest;
import com.genzopia.Instagame.gateway.UserProfileDTO;

import androidx.annotation.NonNull;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.genzopia.Instagame.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Edit profile screen — reads and writes profile data via the backend Gateway.
 * Requirements: 9.1, 9.2, 9.3
 */
public class EditProfileActivity extends BaseActivity {

    private MaterialToolbar topAppBar;
    private TextInputEditText inputFullName, inputBio, inputWebsite, inputPhone, inputStory;
    private MaterialButton btnSave;
    private ProgressBar progressBar;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        topAppBar    = findViewById(R.id.topAppBar);
        inputFullName = findViewById(R.id.inputFullName);
        inputBio     = findViewById(R.id.inputBio);
        inputWebsite = findViewById(R.id.inputWebsite);
        inputPhone   = findViewById(R.id.inputPhone);
        inputStory   = findViewById(R.id.inputStory);
        btnSave      = findViewById(R.id.btnSave);
        progressBar  = findViewById(R.id.progressBar);

        setSupportActionBar(topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle("Edit profile");
        }
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        sharedPreferences = getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE);

        btnSave.setOnClickListener(v -> saveProfile());
        loadExistingData();
    }

    private void loadExistingData() {
        progressBar.setVisibility(View.VISIBLE);
        GatewayClient.INSTANCE.getCallApi().getMyProfile()
                .enqueue(new Callback<UserProfileDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<UserProfileDTO> call,
                                           @NonNull Response<UserProfileDTO> resp) {
                        progressBar.setVisibility(View.GONE);
                        if (resp.isSuccessful() && resp.body() != null && !isFinishing()) {
                            UserProfileDTO p = resp.body();
                            inputFullName.setText(p.getFull_name() != null ? p.getFull_name() : "");
                            inputBio.setText(p.getBio() != null ? p.getBio() : "");
                            inputWebsite.setText(p.getWebsite() != null ? p.getWebsite() : "");
                            inputPhone.setText(p.getPhone() != null ? p.getPhone() : "");
                            inputStory.setText(p.getStory() != null ? p.getStory() : "");
                        } else if (!isFinishing()) {
                            Toast.makeText(EditProfileActivity.this,
                                    "Failed to load profile", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<UserProfileDTO> call, @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        if (!isFinishing()) {
                            Toast.makeText(EditProfileActivity.this,
                                    "Failed to load profile", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void saveProfile() {
        String fullName = inputFullName.getText() != null ? inputFullName.getText().toString().trim() : "";
        String bio      = inputBio.getText()      != null ? inputBio.getText().toString().trim()      : "";
        String website  = inputWebsite.getText()  != null ? inputWebsite.getText().toString().trim()  : "";
        String phone    = inputPhone.getText()    != null ? inputPhone.getText().toString().trim()    : "";
        String story    = inputStory.getText()    != null ? inputStory.getText().toString().trim()    : "";

        if (TextUtils.isEmpty(fullName)) {
            inputFullName.setError("Full name required");
            inputFullName.requestFocus();
            return;
        }
        if (!story.isEmpty() && story.split("\\s+").length > 30) {
            inputStory.setError("Story must be 30 words or less");
            inputStory.requestFocus();
            return;
        }

        final String websiteFinal = (!TextUtils.isEmpty(website) && !website.startsWith("http"))
                ? ("https://" + website) : website;

        progressBar.setVisibility(View.VISIBLE);
        btnSave.setEnabled(false);

        UpdateProfileRequest req = new UpdateProfileRequest(fullName, bio, websiteFinal, phone, story);
        GatewayClient.INSTANCE.getCallApi().updateMyProfile(req)
                .enqueue(new Callback<UserProfileDTO>() {
                    @Override
                    public void onResponse(@NonNull Call<UserProfileDTO> call,
                                           @NonNull Response<UserProfileDTO> resp) {
                        progressBar.setVisibility(View.GONE);
                        btnSave.setEnabled(true);
                        if (resp.isSuccessful()) {
                            sharedPreferences.edit()
                                    .putString("full_name", fullName)
                                    .putString("bio", bio)
                                    .putString("website", websiteFinal)
                                    .putString("phone", phone)
                                    .apply();
                            Toast.makeText(EditProfileActivity.this,
                                    "Profile updated", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(EditProfileActivity.this,
                                    "Failed to update profile", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<UserProfileDTO> call, @NonNull Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        btnSave.setEnabled(true);
                        Toast.makeText(EditProfileActivity.this,
                                "Failed to update profile", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
