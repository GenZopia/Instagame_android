package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.AutoCompleteTextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import de.hdodenhof.circleimageview.CircleImageView;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import android.widget.ArrayAdapter;
import android.text.Editable;
import android.text.TextWatcher;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

public class VideoUploadInfoActivity extends AppCompatActivity {
    private VideoView videoView;
    private TextInputEditText inputTitle;
    private TextInputEditText inputDescription;
    private ChipGroup gameTagChipGroup;
    private MaterialButton btnConfirmUpload;
    private Uri videoUri;
    // User info views
    private CircleImageView userAvatar;
    private TextView userName;
    private TextView userUsername;
    private ImageView editbutton;
    private AutoCompleteTextView gameDropdown;
    private TextView gameTagText;
    private ArrayAdapter<String> gameAdapter;
    private List<String> gameNames = new ArrayList<>();
    
    // TextInputLayout references for error display
    private TextInputLayout titleInputLayout;
    private TextInputLayout gameDropdownLayout;
    private TextInputLayout descInputLayout;
    
    // Firebase references
    private DatabaseReference userRef;
    private ValueEventListener userListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_upload_info);

        videoView = findViewById(R.id.video_view);
        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        gameTagChipGroup = findViewById(R.id.gameTagChipGroup);
        btnConfirmUpload = findViewById(R.id.btnConfirmUpload);
        userAvatar = findViewById(R.id.userAvatar);
        userName = findViewById(R.id.userName);

        editbutton=findViewById(R.id.editIcon);
        gameDropdown = findViewById(R.id.gameDropdown);
        gameTagText = findViewById(R.id.gameTagText);
        
        // Get TextInputLayout references for error display
        titleInputLayout = findViewById(R.id.titleInputLayout);
        gameDropdownLayout = findViewById(R.id.gameDropdownLayout);
        descInputLayout = findViewById(R.id.descInputLayout);

        editbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoUploadInfoActivity.super.onBackPressed();
            }
        });

        // Get video URI from intent
        String uriString = getIntent().getStringExtra("video_uri");
        if (uriString == null) {
            finish();
            return;
        }
        videoUri = Uri.parse(uriString);

        // Setup video preview
        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // Mute the video
            videoView.start();
        });

        // Setup game tag chips
        String[] gameTags = {"#Action", "#Adventure", "#Puzzle", "#Strategy", "#Sports","#Others"};
        for (String tag : gameTags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            gameTagChipGroup.addView(chip);
        }
        gameTagChipGroup.setSingleSelection(true);

        // Fetch user data from Firebase
        fetchUserData();

        gameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, gameNames);
        gameDropdown.setAdapter(gameAdapter);

        // Fetch game names from Firebase
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference("games");
        gamesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameNames.clear();
                for (DataSnapshot gameSnap : snapshot.getChildren()) {
                    String name = gameSnap.child("game_name").getValue(String.class);
                    if (name != null) gameNames.add(name);
                }
                gameAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        gameDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selected = gameAdapter.getItem(position);
            gameTagText.setText("@" + selected);
            gameDropdownLayout.setError(null); // Clear error when valid selection is made
        });

        // Add text change listener for game dropdown validation
        gameDropdown.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString().trim();
                if (!input.isEmpty()) {
                    boolean hasMatch = false;
                    for (String gameName : gameNames) {
                        if (gameName.equalsIgnoreCase(input)) {
                            hasMatch = true;
                            break;
                        }
                    }
                    if (!hasMatch) {
                        gameDropdownLayout.setError("No game match");
                    } else {
                        gameDropdownLayout.setError(null);
                        gameTagText.setText("@" + input);
                    }
                } else {
                    gameDropdownLayout.setError(null);
                    gameTagText.setText("");
                }
            }
        });

        btnConfirmUpload.setOnClickListener(v -> {
            // Clear previous errors
            titleInputLayout.setError(null);
            gameDropdownLayout.setError(null);
            descInputLayout.setError(null);
            
            String title = inputTitle.getText() != null ? inputTitle.getText().toString().trim() : "";
            String description = inputDescription.getText() != null ? inputDescription.getText().toString().trim() : "";
            String gameInput = gameDropdown.getText() != null ? gameDropdown.getText().toString().trim() : "";
            
            // Validate title
            if (title.isEmpty()) {
                titleInputLayout.setError("Title required");
                return;
            }
            if (title.length() > 50) {
                titleInputLayout.setError("Title must be less than 50 characters");
                return;
            }
            
            // Validate description
            if (description.length() > 200) {
                descInputLayout.setError("Description must be less than 200 characters");
                return;
            }
            
            // Validate game dropdown
            if (gameInput.isEmpty()) {
                gameDropdownLayout.setError("Please select a game");
                return;
            }
            
            // Check if game input matches any game in the list
            boolean hasGameMatch = false;
            String matchedGame = null;
            for (String gameName : gameNames) {
                if (gameName.equalsIgnoreCase(gameInput)) {
                    hasGameMatch = true;
                    matchedGame = gameName;
                    break;
                }
            }
            
            if (!hasGameMatch) {
                gameDropdownLayout.setError("No game match");
                return;
            }
            
            // All validations passed, proceed with upload
            doUploadWithInfo(title, description, "@" + matchedGame);
        });
    }

    private void fetchUserData() {
        // Get current user ID from Firebase Auth
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        userRef = FirebaseDatabase.getInstance().getReference()
                .child("users").child(userId);

        userListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!dataSnapshot.exists()) return;

                // Profile photo
                String profilePhotoUrl = dataSnapshot.child("profile_photo_url").getValue(String.class);
                if (profilePhotoUrl != null && !profilePhotoUrl.equals("-1")) {
                    Glide.with(VideoUploadInfoActivity.this)
                            .load(profilePhotoUrl)
                            .placeholder(R.drawable.profile)
                            .error(R.drawable.profile)
                            .into(userAvatar);
                }

                // Username and full name
                String fullName = dataSnapshot.child("full_name").getValue(String.class);
                String username = dataSnapshot.child("username").getValue(String.class);
                
                if (fullName != null && !fullName.isEmpty()) {
                    userName.setText(fullName);
                }
                
                if (username != null && !username.isEmpty()) {
                    userUsername.setText("@" + username);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(VideoUploadInfoActivity.this, "Failed to fetch user data", Toast.LENGTH_SHORT).show();
            }
        };
        userRef.addValueEventListener(userListener);
    }

    private void doUploadWithInfo(String title, String description, String gameTag) {
        File file = FileUtils.getFileFromUri(this, videoUri);
        if (file == null) {
            Toast.makeText(this, "Unable to read file", Toast.LENGTH_SHORT).show();
            return;
        }
        btnConfirmUpload.setEnabled(false);
        btnConfirmUpload.setText("Uploading...");
        String videoId = UUID.randomUUID().toString();
        FileUploader.uploadFileToWorker(
                file,
                "video",
                Map.of(
                        "video_id", videoId,
                        "title", title,
                        "description", description,
                        "game_tag", gameTag
                ),
                (success, response) -> runOnUiThread(() -> {
                    btnConfirmUpload.setEnabled(true);
                    btnConfirmUpload.setText("Upload");
                    if (success) {
                        Toast.makeText(this, "Upload succeeded!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Upload failed: " + response, Toast.LENGTH_LONG).show();
                    }
                })
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }
} 