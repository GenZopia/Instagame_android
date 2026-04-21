package com.genzopia.Instagame.channel_view;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.common.BaseActivity;
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
import com.google.firebase.database.Transaction;
import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.ui.PlayerView;
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

public class VideoDetailActivity extends BaseActivity {

    // Result codes
    public static final int RESULT_VIDEO_DELETED = 1001;
    public static final int RESULT_VIDEO_UPDATED = 1002;

    private String videoId;
    private String currentUserId;
    private boolean isOwnVideo = false;
    private Boolean isVerified = false; // Add this field

    // Original values for change tracking
    private String originalTitle = "";
    private String originalDescription = "";
    private String originalGameId = "";
    private String originalGameName = "";

    // UI Components
    private PlayerView playerView;
    private ExoPlayer player;
    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private AutoCompleteTextView gameDropdown;
    private TextInputLayout titleInputLayout;
    private TextInputLayout descriptionInputLayout;
    private TextInputLayout gameDropdownLayout;
    private MaterialButton saveButton;
    private MaterialButton backButton;
    private MaterialButton deleteButton;
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

    @OptIn(markerClass = UnstableApi.class)
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
        deleteButton = findViewById(R.id.deleteButton);
        viewCountText = findViewById(R.id.viewCountText);
        likeCountText = findViewById(R.id.likeCountText);
        shareCountText = findViewById(R.id.shareCountText);
        uploadDateText = findViewById(R.id.uploadDateText);
        gameTagChipGroup = findViewById(R.id.gameTagChipGroup);

        // Setup ExoPlayer with improved configuration
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // Configure PlayerView for better video rendering
        playerView.setUseController(true);
        playerView.setResizeMode(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

        // Add player listener for better error handling and visibility
        player.addListener(new androidx.media3.common.Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Log.d("VideoDetailActivity", "Playback state changed: " + playbackState);
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    Log.d("VideoDetailActivity", "Video ready to play");
                    // Ensure video is visible
                    playerView.setVisibility(View.VISIBLE);

                    // Check video dimensions
                    if (player.getVideoSize().width > 0 && player.getVideoSize().height > 0) {
                        Log.d("VideoDetailActivity", "Video dimensions: " + player.getVideoSize().width + "x" + player.getVideoSize().height);
                    } else {
                        Log.w("VideoDetailActivity", "Video has no valid dimensions");
                    }
                } else if (playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
                    Log.d("VideoDetailActivity", "Video is buffering");
                } else if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    Log.d("VideoDetailActivity", "Video playback ended");
                } else if (playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    Log.d("VideoDetailActivity", "Video is idle");
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                Log.e("VideoDetailActivity", "Player error: " + error.getMessage());
                Log.e("VideoDetailActivity", "Error cause: " + error.getCause());

                runOnUiThread(() -> {
                    Toast.makeText(VideoDetailActivity.this, "Error playing video", Toast.LENGTH_SHORT).show();

                    // Try to recover from error
                    if (player != null) {
                        Log.d("VideoDetailActivity", "Attempting to recover from error");
                        player.prepare();
                        player.setPlayWhenReady(true);
                    }
                });
            }

            @Override
            public void onVideoSizeChanged(androidx.media3.common.VideoSize videoSize) {
                Log.d("VideoDetailActivity", "Video size changed: " + videoSize.width + "x" + videoSize.height);
                if (videoSize.width > 0 && videoSize.height > 0) {
                    playerView.setVisibility(View.VISIBLE);
                }
            }
        });
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
                        Log.d("VideoDetailActivity", "Loaded game: '" + gameName + "' -> " + gameId);
                    }
                }

                // Sort games alphabetically for better UX
                java.util.Collections.sort(gameNames);

                gameAdapter = new ArrayAdapter<>(VideoDetailActivity.this,
                        android.R.layout.simple_dropdown_item_1line, gameNames);
                gameDropdown.setAdapter(gameAdapter);

                Log.d("VideoDetailActivity", "Total games loaded: " + gameNames.size());
                Log.d("VideoDetailActivity", "Game map size: " + gameNameToId.size());
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
        // Check if games are loaded
        if (gameNames.isEmpty()) {
            Toast.makeText(this, "Games are still loading, please try again", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("VideoDetailActivity", "Opening game search dialog with " + gameNames.size() + " games");

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
            Log.d("VideoDetailActivity", "Game selected from dialog: '" + selectedGame + "'");
            Log.d("VideoDetailActivity", "Game exists in map: " + gameNameToId.containsKey(selectedGame));

            gameDropdown.setText(selectedGame);

            // Add game chip
            gameTagChipGroup.removeAllViews();
            Chip gameChip = new Chip(this);
            gameChip.setText(selectedGame);
            gameChip.setChipBackgroundColorResource(R.color.button_primary);
            gameChip.setTextColor(ContextCompat.getColor(this, android.R.color.white));
            gameTagChipGroup.addView(gameChip);

            // Check for changes after game selection
            checkForChanges();

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

                    // Store original values for change tracking
                    originalTitle = title != null ? title : "";
                    originalDescription = description != null ? description : "";
                    originalGameId = gameId != null ? gameId : "";

                    // Set UI values
                    titleInput.setText(originalTitle);
                    descriptionInput.setText(originalDescription);
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
                        originalGameName = gameName;
                        gameDropdown.setText(gameName);

                        // Add game chip
                        gameTagChipGroup.removeAllViews();
                        Chip gameChip = new Chip(VideoDetailActivity.this);
                        gameChip.setText(gameName);
                        gameChip.setChipBackgroundColorResource(R.color.button_primary);
                        gameChip.setTextColor(ContextCompat.getColor(VideoDetailActivity.this, android.R.color.white));
                        gameTagChipGroup.addView(gameChip);

                        // Check for changes after setting original values
                        checkForChanges();
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

                            // Ensure video starts playing and is visible
                            player.setPlayWhenReady(true);
                            playerView.setVisibility(View.VISIBLE);

                            Log.d("VideoDetailActivity", "Video player setup completed");

                            // Add a timeout to ensure video starts playing
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                if (player != null && !player.isPlaying()) {
                                    Log.d("VideoDetailActivity", "Video not playing after 3 seconds, forcing play");
                                    player.setPlayWhenReady(true);
                                    playerView.setVisibility(View.VISIBLE);
                                }
                            }, 3000);
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
            deleteButton.setVisibility(View.VISIBLE);

            // Show verification status
            if (isVerified != null && isVerified) {
                // Show verified badge
            } else {
                // Show pending badge
            }

            // Initialize save button state
            checkForChanges();
        } else {
            // Disable editing for other users' videos
            titleInput.setEnabled(false);
            descriptionInput.setEnabled(false);
            gameDropdown.setEnabled(false);
            saveButton.setVisibility(View.GONE);
            deleteButton.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        saveButton.setOnClickListener(v -> saveVideoChanges());

        deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog());

        // Add text change listeners for change tracking
        titleInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkForChanges();
            }
        });

        descriptionInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkForChanges();
            }
        });

        gameDropdown.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                checkForChanges();
            }
        });
    }

    private void checkForChanges() {
        if (!isOwnVideo) {
            saveButton.setEnabled(false);
            return;
        }

        String currentTitle = titleInput.getText() != null ? titleInput.getText().toString().trim() : "";
        String currentDescription = descriptionInput.getText() != null ? descriptionInput.getText().toString().trim() : "";
        String currentGameName = gameDropdown.getText() != null ? gameDropdown.getText().toString().trim() : "";

        boolean hasChanges = !currentTitle.equals(originalTitle) ||
                !currentDescription.equals(originalDescription) ||
                !currentGameName.equals(originalGameName);

        saveButton.setEnabled(hasChanges);

        // Update button appearance based on state
        if (hasChanges) {
            saveButton.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.button_primary));
            saveButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        } else {
            saveButton.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.darker_gray));
            saveButton.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        }
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
        Log.d("VideoDetailActivity", "Selected game: '" + newGameInput + "'");
        Log.d("VideoDetailActivity", "Available games in map: " + gameNameToId.keySet());
        Log.d("VideoDetailActivity", "Game ID found: " + newGameId);

        if (newGameId == null) {
            // Try to find the game by trimming whitespace
            for (String gameName : gameNameToId.keySet()) {
                if (gameName.trim().equals(newGameInput.trim())) {
                    newGameId = gameNameToId.get(gameName);
                    Log.d("VideoDetailActivity", "Found game after trimming: " + gameName + " -> " + newGameId);
                    break;
                }
            }
        }

        if (newGameId == null) {
            gameDropdownLayout.setError("Invalid game selection: " + newGameInput);
            Log.e("VideoDetailActivity", "Game not found in map. Available games: " + gameNameToId.keySet());
            return;
        }

        // Update Firebase
        Map<String, Object> updates = new HashMap<>();
        updates.put("video_title", newTitle);
        updates.put("description", newDescription);
        updates.put("game_id", newGameId);

        // Store game ID in final variable for lambda
        final String finalGameId = newGameId;

        videoRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Video updated successfully", Toast.LENGTH_SHORT).show();

                    // Update original values to current values
                    originalTitle = newTitle;
                    originalDescription = newDescription;
                    originalGameName = newGameInput;
                    originalGameId = finalGameId;

                    // Set result to indicate video was updated
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("video_id", videoId);
                    setResult(RESULT_VIDEO_UPDATED, resultIntent);

                    // Disable save button and update appearance
                    checkForChanges();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Delete Video")
                .setMessage("Are you sure you want to delete this video? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteVideo())
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteVideo() {
        // Show loading dialog
        androidx.appcompat.app.AlertDialog loadingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(R.layout.loading_dialog)
                .setCancelable(false)
                .create();
        loadingDialog.show();

        Log.d("VideoDetailActivity", "Deleting video: " + videoId);
        Log.d("VideoDetailActivity", "Current user ID: " + currentUserId);

        // First, get the video data to extract the user_id
        DatabaseReference videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoId);
        videoRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // Check if activity is still valid
                if (isFinishing() || isDestroyed()) {
                    Log.d("VideoDetailActivity", "Activity is finishing, skipping video data processing");
                    return;
                }

                if (snapshot.exists()) {
                    // Get the user_id from the video data
                    String videoUserId = snapshot.child("user_id").getValue(String.class);
                    Log.d("VideoDetailActivity", "Found user_id from video: " + videoUserId);
                    Log.d("VideoDetailActivity", "Video data snapshot: " + snapshot.getValue());

                    if (videoUserId != null && !videoUserId.isEmpty()) {
                        Log.d("VideoDetailActivity", "User ID is valid, proceeding with deletion");
                        // Now perform deletion with correct user_id
                        performCorrectDeletion(videoId, videoUserId, loadingDialog);
                    } else {
                        Log.e("VideoDetailActivity", "user_id not found in video data or is empty");
                        Log.e("VideoDetailActivity", "Available fields in video data:");
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Log.e("VideoDetailActivity", "Field: " + child.getKey() + " = " + child.getValue());
                        }
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                try {
                                    if (loadingDialog != null && loadingDialog.isShowing()) {
                                        loadingDialog.dismiss();
                                    }
                                    Toast.makeText(VideoDetailActivity.this, "Video data is corrupted", Toast.LENGTH_SHORT).show();
                                } catch (Exception e) {
                                    Log.e("VideoDetailActivity", "Error dismissing dialog: " + e.getMessage());
                                }
                            }
                        });
                    }
                } else {
                    Log.e("VideoDetailActivity", "Video not found in videos node: " + videoId);
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            try {
                                if (loadingDialog != null && loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
                                Toast.makeText(VideoDetailActivity.this, "Video not found", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Log.e("VideoDetailActivity", "Error dismissing dialog: " + e.getMessage());
                            }
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("VideoDetailActivity", "Failed to get video data: " + error.getMessage());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        try {
                            if (loadingDialog != null && loadingDialog.isShowing()) {
                                loadingDialog.dismiss();
                            }
                            Toast.makeText(VideoDetailActivity.this, "Failed to get video data: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            Log.e("VideoDetailActivity", "Error dismissing dialog: " + e.getMessage());
                        }
                    }
                });
            }
        });
    }

    private void performCorrectDeletion(String videoIdToDelete, String videoUserId, androidx.appcompat.app.AlertDialog loadingDialog) {
        Log.d("VideoDetailActivity", "=== performCorrectDeletion called ===");
        Log.d("VideoDetailActivity", "Performing correct deletion for video ID: " + videoIdToDelete + " with user ID: " + videoUserId);

        // Create references with correct IDs (no sanitization needed since Firebase shows no extensions)
        DatabaseReference videoRef = FirebaseDatabase.getInstance().getReference("videos").child(videoIdToDelete);
        DatabaseReference userVideoRef = FirebaseDatabase.getInstance().getReference("users")
                .child(videoUserId).child("videos").child(videoIdToDelete);

        Log.d("VideoDetailActivity", "Video ref path: " + videoRef.toString());
        Log.d("VideoDetailActivity", "User video ref path: " + userVideoRef.toString());

        Log.d("VideoDetailActivity", "Deleting from videos node: " + videoRef.toString());
        Log.d("VideoDetailActivity", "Deleting from user videos node: " + userVideoRef.toString());

        // Use a completion-based approach to ensure both deletions complete
        final boolean[] videoDeleted = {false};
        final boolean[] userVideoDeleted = {false};
        final boolean[] hasError = {false};

        // Function to check if both deletions are complete
        Runnable checkCompletion = () -> {
            Log.d("VideoDetailActivity", "=== checkCompletion called ===");
            Log.d("VideoDetailActivity", "videoDeleted: " + videoDeleted[0] + ", userVideoDeleted: " + userVideoDeleted[0] + ", hasError: " + hasError[0]);

            if (videoDeleted[0] && userVideoDeleted[0] && !hasError[0]) {
                Log.d("VideoDetailActivity", "Both deletions completed successfully");
                runOnUiThread(() -> {
                    try {
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        Toast.makeText(VideoDetailActivity.this, "Video deleted successfully", Toast.LENGTH_SHORT).show();

                        // Store deletion info in SharedPreferences for the parent activity to check
                        android.content.SharedPreferences prefs = getSharedPreferences("VideoDeletionPrefs", android.content.Context.MODE_PRIVATE);
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("deleted_video_id", videoIdToDelete);
                        editor.putLong("deletion_timestamp", System.currentTimeMillis());
                        editor.apply();

                        Log.d("VideoDetailActivity", "Stored deletion info in SharedPreferences: " + videoIdToDelete);

                        // Set result to indicate video was deleted
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("video_id", videoIdToDelete);
                        setResult(RESULT_VIDEO_DELETED, resultIntent);

                        Log.d("VideoDetailActivity", "=== Setting result and finishing activity ===");
                        finish(); // Close the activity
                    } catch (Exception e) {
                        Log.e("VideoDetailActivity", "Error dismissing dialog: " + e.getMessage());
                    }
                });
            } else if (hasError[0]) {
                Log.d("VideoDetailActivity", "Deletion completed with errors");
                runOnUiThread(() -> {
                    try {
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        Toast.makeText(VideoDetailActivity.this, "Video deletion completed with some errors", Toast.LENGTH_SHORT).show();
                        finish(); // Close the activity
                    } catch (Exception e) {
                        Log.e("VideoDetailActivity", "Error dismissing dialog: " + e.getMessage());
                    }
                });
            }
        };

        // Delete from videos node first
        Log.d("VideoDetailActivity", "Starting deletion from videos node");
        videoRef.removeValue().addOnSuccessListener(aVoid -> {
            Log.d("VideoDetailActivity", "Successfully deleted from videos node");
            videoDeleted[0] = true;
            checkCompletion.run();
        }).addOnFailureListener(e -> {
            Log.e("VideoDetailActivity", "Failed to delete from videos node: " + e.getMessage());
            hasError[0] = true;
            checkCompletion.run();
        });

        // Delete from user videos node simultaneously
        Log.d("VideoDetailActivity", "Starting deletion from user videos node");
        userVideoRef.removeValue().addOnSuccessListener(aVoid -> {
            Log.d("VideoDetailActivity", "Successfully deleted from user videos node");
            userVideoDeleted[0] = true;
            checkCompletion.run();
        }).addOnFailureListener(e -> {
            Log.e("VideoDetailActivity", "Failed to delete from user videos: " + e.getMessage());
            Log.e("VideoDetailActivity", "Error details: " + e.toString());
            hasError[0] = true;
            checkCompletion.run();
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