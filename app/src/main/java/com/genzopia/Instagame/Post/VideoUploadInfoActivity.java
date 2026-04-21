package com.genzopia.Instagame.Post;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import de.hdodenhof.circleimageview.CircleImageView;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class VideoUploadInfoActivity extends BaseActivity {
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
    // Add mapping for game name to game id
    private java.util.Map<String, String> gameNameToId = new java.util.HashMap<>();
    private String devid="";
    
    // TextInputLayout references for error display
    private TextInputLayout titleInputLayout;
    private TextInputLayout gameDropdownLayout;
    private TextInputLayout descInputLayout;
    
    // Firebase references
    private DatabaseReference userRef;
    private ValueEventListener userListener;
    private androidx.appcompat.app.AlertDialog dialog; // Store dialog reference
    
    // Video upload variables
    private String videoUniqueId;
    private LinearProgressIndicator uploadProgressBar;
    private BroadcastReceiver uploadProgressReceiver;

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
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressBar.setVisibility(View.GONE);
        uploadProgressBar.setIndeterminate(true);

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
        
        // Generate unique video ID
        videoUniqueId = "video_" + UUID.randomUUID().toString().replace("-", "");

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

        // Fetch game names and ids from Firebase
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference("games");
        gamesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameNames.clear();
                gameNameToId.clear();
                for (DataSnapshot gameSnap : snapshot.getChildren()) {
                    String name = gameSnap.child("game_name").getValue(String.class);
                    String id = gameSnap.getKey();
                    if (name != null && id != null) {
                        gameNames.add(name);
                        gameNameToId.put(name, id);
                    }
                }
                // Sort games alphabetically for better UX
                java.util.Collections.sort(gameNames);
                
                // Set the current user's ID as the developer ID
                devid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                gameAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Set click listener to show search dialog
        gameDropdown.setOnClickListener(v -> showGameSearchDialog());
        gameDropdown.setFocusable(false); // Prevent keyboard from showing

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
            
            // Get the game id for the matched game
            String gameId = gameNameToId.get(matchedGame);
            if(this.devid!=""){
            String devid=this.devid;}
            // Move file I/O to background thread
            new Thread(() -> {
                File originalFile = FileUtils.getFileFromUri(this, videoUri);
                String fileExtension = getFileExtension(originalFile != null ? originalFile.getName() : ".mp4");
                runOnUiThread(() -> {
                    // Start foreground service for upload
                    Intent serviceIntent = new Intent(this, VideoUploadForegroundService.class);
                    serviceIntent.setAction(VideoUploadForegroundService.ACTION_UPLOAD);
                    serviceIntent.putExtra(VideoUploadForegroundService.DEVID,devid);
                    serviceIntent.putExtra(VideoUploadForegroundService.EXTRA_TITLE, title);
                    serviceIntent.putExtra(VideoUploadForegroundService.EXTRA_DESCRIPTION, description);
                    serviceIntent.putExtra(VideoUploadForegroundService.EXTRA_GAME_ID, gameId);
                    serviceIntent.putExtra(VideoUploadForegroundService.EXTRA_VIDEO_URI, videoUri.toString());
                    serviceIntent.putExtra(VideoUploadForegroundService.EXTRA_FILE_EXTENSION, fileExtension);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    // Show progress bar
                    uploadProgressBar.setVisibility(View.VISIBLE);
                    uploadProgressBar.setIndeterminate(true);
                    btnConfirmUpload.setEnabled(false);
                });
            }).start();
        });

        // BroadcastReceiver for upload progress
        uploadProgressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(VideoUploadForegroundService.EXTRA_PROGRESS)) {
                    int progress = intent.getIntExtra(VideoUploadForegroundService.EXTRA_PROGRESS, 0);
                    uploadProgressBar.setVisibility(View.VISIBLE);
                    uploadProgressBar.setIndeterminate(false);
                    uploadProgressBar.setProgressCompat(progress, true);
                }
                if (intent.hasExtra(VideoUploadForegroundService.EXTRA_RESULT)) {
                    String result = intent.getStringExtra(VideoUploadForegroundService.EXTRA_RESULT);
                    uploadProgressBar.setVisibility(View.GONE);
                    btnConfirmUpload.setEnabled(true);
                    if ("success".equals(result)) {
                        Toast.makeText(context, "Upload succeeded!", Toast.LENGTH_LONG).show();
                        finish();
                    } else if ("fail".equals(result)) {
                        Toast.makeText(context, "Upload failed!", Toast.LENGTH_LONG).show();
                    } else if ("cancelled".equals(result)) {
                        Toast.makeText(context, "Upload cancelled.", Toast.LENGTH_LONG).show();
                    }
                }
            }
        };
    }
    
    private void showGameSearchDialog() {
        // Create custom dialog with search functionality
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Select Game");
        
        // Create dialog layout
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_game_search, null);
        builder.setView(dialogView);
        
        // Get views from dialog layout
        android.widget.EditText searchEditText = dialogView.findViewById(R.id.searchEditText);
        android.widget.ListView gameListView = dialogView.findViewById(R.id.gameListView);
        TextView noGamesText = dialogView.findViewById(R.id.noGamesText);
        
        // Create adapter for the list
        ArrayAdapter<String> searchAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, gameNames);
        gameListView.setAdapter(searchAdapter);
        
        // Setup search functionality
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String query = s.toString().toLowerCase().trim();
                java.util.List<String> filteredGames = new java.util.ArrayList<>();
                
                for (String gameName : gameNames) {
                    if (gameName.toLowerCase().contains(query)) {
                        filteredGames.add(gameName);
                    }
                }
                
                // Update adapter with filtered results
                ArrayAdapter<String> filteredAdapter = new ArrayAdapter<>(VideoUploadInfoActivity.this,
                    android.R.layout.simple_list_item_1, filteredGames);
                gameListView.setAdapter(filteredAdapter);
                
                // Show/hide no results text
                if (filteredGames.isEmpty() && !query.isEmpty()) {
                    noGamesText.setVisibility(android.view.View.VISIBLE);
                    gameListView.setVisibility(android.view.View.GONE);
                } else {
                    noGamesText.setVisibility(android.view.View.GONE);
                    gameListView.setVisibility(android.view.View.VISIBLE);
                }
            }
        });
        
        // Setup list item click
        gameListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedGame = (String) parent.getItemAtPosition(position);
            gameDropdown.setText(selectedGame);
            gameTagText.setText("@" + selectedGame);
            gameDropdownLayout.setError(null); // Clear error when valid selection is made
            
            // Dismiss dialog
            if (dialog != null) {
                dialog.dismiss();
            }
        });
        
        // Create and show dialog
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        this.dialog = dialog; // Store reference for dismissal
        
        // Focus on search box when dialog opens
        dialog.setOnShowListener(dialogInterface -> {
            searchEditText.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) 
                getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });
        
        dialog.show();
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
                String sanitizedPhotoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(profilePhotoUrl);
                if (sanitizedPhotoUrl != null) {
                    Glide.with(VideoUploadInfoActivity.this)
                            .load(sanitizedPhotoUrl)
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

    
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return fileName.substring(lastDotIndex);
        }
        return ".mp4"; // Default extension
    }

    private boolean isReceiverRegistered = false;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {
        super.onResume();
        // Only register if receiver is initialized and not already registered
        if (uploadProgressReceiver != null && !isReceiverRegistered) {
            try {
                registerReceiver(uploadProgressReceiver, new IntentFilter(VideoUploadForegroundService.BROADCAST_PROGRESS));
                isReceiverRegistered = true;
            } catch (Exception e) {
                // Handle any registration errors
                isReceiverRegistered = false;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (uploadProgressReceiver != null && isReceiverRegistered) {
            try {
                unregisterReceiver(uploadProgressReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver was not registered, ignore the exception
                isReceiverRegistered = false;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userListener != null && userRef != null) {
            userRef.removeEventListener(userListener);
        }
        if (uploadProgressReceiver != null && isReceiverRegistered) {
            try {
                unregisterReceiver(uploadProgressReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver was not registered, ignore the exception
                isReceiverRegistered = false;
            }
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
} 