package com.genzopia.Instagame.channel_view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.genzopia.Instagame.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.content.Context;
import android.util.Log;

public class VideoDetailActivity extends AppCompatActivity {
    
    private String videoId;
    private String currentUserId;
    private boolean isOwnVideo = false;
    private Boolean isVerified = false; // Add this field
    
    // UI Components
    private StyledPlayerView playerView;
    private ExoPlayer player;
    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView gameDropdown;
    private TextInputLayout titleInputLayout;
    private TextInputLayout descriptionInputLayout;
    private TextInputLayout gameDropdownLayout;
    private MaterialButton saveButton;
    private MaterialButton backButton;
    private TextView viewCountText;
    private TextView likeCountText;
    private TextView shareCountText;
    private TextView uploadDateText;
    private ChipGroup gameTagChipGroup;
    
    // Game data
    private List<String> gameNames = new ArrayList<>();
    private Map<String, String> gameNameToId = new HashMap<>();
    private ArrayAdapter<String> gameAdapter;
    
    // Firebase references
    private DatabaseReference videoRef;
    private DatabaseReference gamesRef;
    private ValueEventListener videoListener;
    private ValueEventListener gamesListener;
    private androidx.appcompat.app.AlertDialog dialog; // Store dialog reference

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_detail);
        
        // Get video ID from intent
        videoId = getIntent().getStringExtra("video_id");
        if (videoId == null) {
            Toast.makeText(this, "Video ID not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        initializeViews();
        setupGameDropdown();
        loadVideoData();
        setupClickListeners();
    }
    
    private void initializeViews() {
        playerView = findViewById(R.id.playerView);
        titleInput = findViewById(R.id.titleInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        gameDropdown = findViewById(R.id.gameDropdown);
        titleInputLayout = findViewById(R.id.titleInputLayout);
        descriptionInputLayout = findViewById(R.id.descriptionInputLayout);
        gameDropdownLayout = findViewById(R.id.gameDropdownLayout);
        saveButton = findViewById(R.id.saveButton);
        backButton = findViewById(R.id.backButton);
        viewCountText = findViewById(R.id.viewCountText);
        likeCountText = findViewById(R.id.likeCountText);
        shareCountText = findViewById(R.id.shareCountText);
        uploadDateText = findViewById(R.id.uploadDateText);
        gameTagChipGroup = findViewById(R.id.gameTagChipGroup);
        
        // Setup ExoPlayer
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
    }
    
    private void setupGameDropdown() {
        gamesRef = FirebaseDatabase.getInstance().getReference("games");
        gamesListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameNames.clear();
                gameNameToId.clear();
                
                for (DataSnapshot gameSnapshot : snapshot.getChildren()) {
                    String gameId = gameSnapshot.getKey();
                    String gameName = gameSnapshot.child("game_name").getValue(String.class);
                    
                    if (gameName != null && !gameName.isEmpty()) {
                        gameNames.add(gameName);
                        gameNameToId.put(gameName, gameId);
                    }
                }
                
                // Sort games alphabetically for better UX
                java.util.Collections.sort(gameNames);
                
                gameAdapter = new ArrayAdapter<>(VideoDetailActivity.this, 
                    android.R.layout.simple_dropdown_item_1line, gameNames);
                gameDropdown.setAdapter(gameAdapter);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(VideoDetailActivity.this, "Failed to load games", Toast.LENGTH_SHORT).show();
            }
        };
        gamesRef.addValueEventListener(gamesListener);
        
        // Set click listener to show search dialog
        gameDropdown.setOnClickListener(v -> showGameSearchDialog());
        gameDropdown.setFocusable(false); // Prevent keyboard from showing
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
                ArrayAdapter<String> filteredAdapter = new ArrayAdapter<>(VideoDetailActivity.this,
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
            
            // Add game chip
            gameTagChipGroup.removeAllViews();
            Chip gameChip = new Chip(this);
            gameChip.setText(selectedGame);
            gameChip.setChipBackgroundColorResource(R.color.button_primary);
            gameChip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            gameTagChipGroup.addView(gameChip);
            
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
    
    private void loadVideoData() {
        videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoId);
        videoListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Check if user owns this video
                    String videoUserId = snapshot.child("user_id").getValue(String.class);
                    isOwnVideo = currentUserId.equals(videoUserId);
                    
                    // Load video metadata
                    String title = snapshot.child("video_title").getValue(String.class);
                    String description = snapshot.child("description").getValue(String.class);
                    String gameId = snapshot.child("game_id").getValue(String.class);
                    String viewCount = snapshot.child("view_count").getValue(String.class);
                    String likeCount = snapshot.child("like_count").getValue(String.class);
                    String shareCount = snapshot.child("share_count").getValue(String.class);
                    String createdAt = snapshot.child("created_at").getValue(String.class);
                    isVerified = snapshot.child("is_verified").getValue(Boolean.class);
                    
                    // Set UI values
                    titleInput.setText(title != null ? title : "");
                    descriptionInput.setText(description != null ? description : "");
                    viewCountText.setText(viewCount != null ? viewCount + " views" : "0 views");
                    likeCountText.setText(likeCount != null ? likeCount + " likes" : "0 likes");
                    shareCountText.setText(shareCount != null ? shareCount + " shares" : "0 shares");
                    
                    if (createdAt != null) {
                        uploadDateText.setText("Uploaded on " + formatDate(createdAt));
                    }
                    
                    // Load game name
                    if (gameId != null && !gameId.isEmpty()) {
                        loadGameName(gameId);
                    }
                    
                    // Setup video player
                    setupVideoPlayer();
                    
                    // Update UI based on ownership
                    updateUIForOwnership();
                } else {
                    Toast.makeText(VideoDetailActivity.this, "Video not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(VideoDetailActivity.this, "Failed to load video data", Toast.LENGTH_SHORT).show();
            }
        };
        videoRef.addValueEventListener(videoListener);
    }
    
    private void loadGameName(String gameId) {
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String gameName = snapshot.child("game_name").getValue(String.class);
                    if (gameName != null && !gameName.isEmpty()) {
                        gameDropdown.setText(gameName);
                        
                        // Add game chip
                        gameTagChipGroup.removeAllViews();
                        Chip gameChip = new Chip(VideoDetailActivity.this);
                        gameChip.setText(gameName);
                        gameChip.setChipBackgroundColorResource(R.color.button_primary);
                        gameChip.setTextColor(ContextCompat.getColor(VideoDetailActivity.this, android.R.color.white));
                        gameTagChipGroup.addView(gameChip);
                    }
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Handle error
            }
        });
    }
    
    private void setupVideoPlayer() {
        // Get signed video URL from worker
        String videoUrl = "https://video-signer.genzopia.workers.dev/?path=video/" + videoId;
        Log.d("VideoDetailActivity", "Requesting signed URL: " + videoUrl);
        
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(videoUrl).build();
        
        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                Log.e("VideoDetailActivity", "Failed to get signed URL: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(VideoDetailActivity.this, "Failed to load video", Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                try {
                    String body = response.body().string();
                    org.json.JSONObject obj = new org.json.JSONObject(body);
                    
                    if (obj.optBoolean("success")) {
                        String signedUrl = obj.optString("url");
                        Log.d("VideoDetailActivity", "Got signed URL: " + signedUrl);
                        
                        runOnUiThread(() -> {
                            // Setup video player with signed URL
                            MediaItem mediaItem = MediaItem.fromUri(signedUrl);
                            player.setMediaItem(mediaItem);
                            player.prepare();
                            player.setPlayWhenReady(true);
                            
                            // Add error listener
                            player.addListener(new com.google.android.exoplayer2.Player.Listener() {
                                @Override
                                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                                    Log.e("VideoDetailActivity", "Player error: " + error.getMessage());
                                    runOnUiThread(() -> {
                                        Toast.makeText(VideoDetailActivity.this, "Error playing video", Toast.LENGTH_SHORT).show();
                                    });
                                }
                                
                                @Override
                                public void onPlaybackStateChanged(int playbackState) {
                                    if (playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                                        Log.d("VideoDetailActivity", "Video ready to play");
                                    }
                                }
                            });
                        });
                    } else {
                        Log.e("VideoDetailActivity", "Worker returned error: " + obj.optString("error", "Unknown error"));
                        runOnUiThread(() -> {
                            Toast.makeText(VideoDetailActivity.this, "Failed to get video URL", Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    Log.e("VideoDetailActivity", "Error parsing worker response: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(VideoDetailActivity.this, "Error loading video", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void updateUIForOwnership() {
        if (isOwnVideo) {
            // Enable editing
            titleInput.setEnabled(true);
            descriptionInput.setEnabled(true);
            gameDropdown.setEnabled(true);
            saveButton.setVisibility(View.VISIBLE);
            
            // Show verification status
            if (isVerified != null && isVerified) {
                // Show verified badge
            } else {
                // Show pending badge
            }
        } else {
            // Disable editing for other users' videos
            titleInput.setEnabled(false);
            descriptionInput.setEnabled(false);
            gameDropdown.setEnabled(false);
            saveButton.setVisibility(View.GONE);
        }
    }
    
    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        
        saveButton.setOnClickListener(v -> saveVideoChanges());
    }
    
    private void saveVideoChanges() {
        String newTitle = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String newDescription = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";
        String newGameInput = gameDropdown.getText() != null ? gameDropdown.getText().toString().trim() : "";
        
        // Validate inputs
        if (newTitle.isEmpty()) {
            titleInputLayout.setError("Title required");
            return;
        }
        if (newTitle.length() > 50) {
            titleInputLayout.setError("Title must be less than 50 characters");
            return;
        }
        if (newDescription.length() > 200) {
            descriptionInputLayout.setError("Description must be less than 200 characters");
            return;
        }
        if (newGameInput.isEmpty()) {
            gameDropdownLayout.setError("Please select a game");
            return;
        }
        
        // Get game ID
        String newGameId = gameNameToId.get(newGameInput);
        if (newGameId == null) {
            gameDropdownLayout.setError("Invalid game selection");
            return;
        }
        
        // Update Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("video_title", newTitle);
        updates.put("description", newDescription);
        updates.put("game_id", newGameId);
        
        videoRef.updateChildren(updates)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Video updated successfully", Toast.LENGTH_SHORT).show();
                saveButton.setEnabled(false);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "Failed to update video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    private String formatDate(String dateString) {
        // Simple date formatting - you can enhance this
        return dateString.replace("-", "/");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
        if (videoListener != null && videoRef != null) {
            videoRef.removeEventListener(videoListener);
        }
        if (gamesListener != null && gamesRef != null) {
            gamesRef.removeEventListener(gamesListener);
        }
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
} 