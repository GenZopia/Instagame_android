package com.genzopia.Instagame.vertical_recylerview_custom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.ChannelActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.PlaybackException;
import com.genzopia.Instagame.ui.components.VideoDetailsBottomSheet;
import com.genzopia.Instagame.utils.ViewCountManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import de.hdodenhof.circleimageview.CircleImageView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;

public class VideoViewHolder extends RecyclerView.ViewHolder {
    FrameLayout videoContainer;
    PlayerView playerView;
    CircleImageView channelIcon;
    TextView title;
    TextView channelName;
    TextView viewsAndTime;
    TextView description; // Add description TextView
    private VideoItem currentItem;
    
    // Three-dot menu
    private ImageView threeDotMenu;
    
    // Action buttons
    private LinearLayout likeButton;
    private ImageView likeIcon;
    private LinearLayout followButton;
    private TextView followText;
    private LinearLayout shareButton;
    private ImageView shareIcon;
    private LinearLayout playButton;
    private ImageView playIcon;
    

    
    // Button states
    private boolean isLiked = false;
    private boolean isFollowing = false;
    
    // Seek bar and touch controls
    private View progressLine;
    private View progressContainer;
    private GestureDetector gestureDetector;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isHolding = false;
    private boolean isPausedByHold = false;
    public ExoPlayer exoPlayer; // Make public for HomeAdapter access
    
    // View count tracking
    private boolean hasIncrementedView = false;

    private VideoItem currentVideoItem = null;
    private android.graphics.drawable.Drawable storedThumbnail = null;
    private boolean hasThumbnail = false;

    @SuppressLint("ClickableViewAccessibility")
    public VideoViewHolder(@NonNull View itemView) {
        super(itemView);

        videoContainer = itemView.findViewById(R.id.videoContainer);
        playerView     = itemView.findViewById(R.id.playerView);
        channelIcon    = itemView.findViewById(R.id.channelIcon);
        title          = itemView.findViewById(R.id.title);
        channelName    = itemView.findViewById(R.id.channelName);
        viewsAndTime   = itemView.findViewById(R.id.viewsAndTime);
        threeDotMenu   = itemView.findViewById(R.id.threeDotMenu);
        description    = itemView.findViewById(R.id.description); // Initialize description TextView
        
        // Initialize action buttons
        likeButton = itemView.findViewById(R.id.likeButton);
        likeIcon = itemView.findViewById(R.id.likeIcon);
        followButton = itemView.findViewById(R.id.followButton);
        followText = itemView.findViewById(R.id.tv_follow_text);
        shareButton = itemView.findViewById(R.id.shareButton);
        shareIcon = itemView.findViewById(R.id.shareIcon);
        playButton = itemView.findViewById(R.id.playButton);
        playIcon = itemView.findViewById(R.id.playIcon);
        

        
        // Initialize progress line and container
        progressLine = itemView.findViewById(R.id.progress_line);
        progressContainer = itemView.findViewById(R.id.progress_container);
        
        // Ensure progress container is clickable and visible
        if (progressContainer != null) {
            progressContainer.setClickable(true);
            progressContainer.setFocusable(true);
            progressContainer.setVisibility(View.VISIBLE);
        }
        
        // Set up action button click listeners
        setupActionButtons();
        
        // Set up three-dot menu click listener
        threeDotMenu.setOnClickListener(v -> {
            android.util.Log.d("VideoViewHolder", "Three-dot menu clicked for video: " + (currentItem != null ? currentItem.id : "unknown"));
            showVideoDetailsBottomSheet();
        });
        
        // Initialize gesture detector
        gestureDetector = new GestureDetector(itemView.getContext(), new CustomGestureListener());

        playerView.setUseController(false);
        
        // Set up touch listener for video container
        videoContainer.setOnTouchListener((v, event) -> {
            // Check if touch is on progress container area - make it bigger
            float touchY = event.getY();
            float containerHeight = videoContainer.getHeight();
            float progressContainerHeight = 120f; // Increased height for bigger touch area
            
            if (touchY >= containerHeight - progressContainerHeight) {
                // Touch is in progress container area - handle seeking
                android.util.Log.d("VideoViewHolder", "Touch in seek area: y=" + touchY + ", containerHeight=" + containerHeight);
                return handleProgressLineTouch(event);
            }
            
            // Handle general video container touch
            boolean handled = gestureDetector.onTouchEvent(event);
            
            // Handle touch up for hold/pause functionality
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                onTouchUp();
            }
            
            return handled;
        });
    }

    private void setupActionButtons() {
        // Like button
        likeButton.setOnClickListener(v -> handleLikeClick());
        
        // Follow button
        followButton.setOnClickListener(v -> handleFollowClick());
        
        // Share button
        shareButton.setOnClickListener(v -> handleShareClick());
        
        // Play button
        playButton.setOnClickListener(v -> handlePlayClick());
        

        
        // Profile image click to navigate to channel
        channelIcon.setOnClickListener(v -> {
            if (currentItem != null && currentItem.developerId != null && !currentItem.developerId.isEmpty()) {
                // Navigate to ChannelActivity with developer ID
                Intent intent = new Intent(itemView.getContext(), ChannelActivity.class);
                intent.putExtra("developer_id", currentItem.developerId);
                itemView.getContext().startActivity(intent);
            }
        });
    }

    private void handleLikeClick() {
        if (currentItem == null) return;
        
        String videoId = currentItem.id;
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Prevent multiple rapid clicks
        if (likeButton.isEnabled()) {
            likeButton.setEnabled(false);
            
            // Optimistic update - update UI immediately
            boolean newLikeState = !isLiked;
            updateLikeUI(newLikeState);
            
            // Perform Firebase operations
            if (newLikeState) {
                likeVideoOptimistic(videoId, currentUserId);
            } else {
                unlikeVideoOptimistic(videoId, currentUserId);
            }
        }
    }

    private void likeVideoOptimistic(String videoId, String userId) {
        // Use Firebase transaction for atomic updates
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId);
        
        videoRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                String currentLikeCount = mutableData.child("like_count").getValue(String.class);
                int newLikeCount = 1;
                if (currentLikeCount != null) {
                    newLikeCount = Integer.parseInt(currentLikeCount) + 1;
                }
                mutableData.child("like_count").setValue(String.valueOf(newLikeCount));
                return com.google.firebase.database.Transaction.success(mutableData);
            }
            
            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    // Success - update user's liked videos
                    DatabaseReference userLikedVideosRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(userId)
                            .child("liked_videos")
                            .child(videoId);
                    userLikedVideosRef.setValue(true);
                    
                    Toast.makeText(itemView.getContext(), "Liked!", Toast.LENGTH_SHORT).show();
                } else {
                    // Rollback UI on failure
                    updateLikeUI(false);
                    Toast.makeText(itemView.getContext(), "Failed to like video", Toast.LENGTH_SHORT).show();
                }
                likeButton.setEnabled(true);
            }
        });
    }

    private void unlikeVideoOptimistic(String videoId, String userId) {
        // Use Firebase transaction for atomic updates
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId);
        
        videoRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                String currentLikeCount = mutableData.child("like_count").getValue(String.class);
                int newLikeCount = 0;
                if (currentLikeCount != null) {
                    newLikeCount = Math.max(0, Integer.parseInt(currentLikeCount) - 1);
                }
                mutableData.child("like_count").setValue(String.valueOf(newLikeCount));
                return com.google.firebase.database.Transaction.success(mutableData);
            }
            
            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    // Success - remove from user's liked videos
                    DatabaseReference userLikedVideosRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(userId)
                            .child("liked_videos")
                            .child(videoId);
                    userLikedVideosRef.removeValue();
                    
                    Toast.makeText(itemView.getContext(), "Unliked", Toast.LENGTH_SHORT).show();
                } else {
                    // Rollback UI on failure
                    updateLikeUI(true);
                    Toast.makeText(itemView.getContext(), "Failed to unlike video", Toast.LENGTH_SHORT).show();
                }
                likeButton.setEnabled(true);
            }
        });
    }

    private void updateLikeUI(boolean liked) {
        isLiked = liked;
        
        // Update like icon color
        if (liked) {
            likeIcon.setImageResource(R.drawable.ic_heart_filled);
            likeIcon.setColorFilter(android.graphics.Color.RED);
        } else {
            likeIcon.setImageResource(R.drawable.ic_heart);
            likeIcon.setColorFilter(android.graphics.Color.WHITE);
        }
    }

    private void handleFollowClick() {
        if (currentItem == null) return;
        
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String developerId = currentItem.developerId; // Use the actual developer ID
        
        // Prevent following yourself
        if (currentUserId.equals(developerId)) {
            Toast.makeText(itemView.getContext(), "You cannot follow yourself", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Prevent multiple rapid clicks
        if (followButton.isEnabled()) {
            followButton.setEnabled(false);
            
            // Optimistic update - update UI immediately
            boolean newFollowState = !isFollowing;
            updateFollowUI(newFollowState);
            
            // Perform Firebase operations
            if (newFollowState) {
                followUserOptimistic(currentUserId, developerId);
            } else {
                unfollowUserOptimistic(currentUserId, developerId);
            }
        }
    }

    private void followUserOptimistic(String currentUserId, String developerId) {
        // Use Firebase transaction for atomic updates
        DatabaseReference developerRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId);
        
        developerRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                String currentFollowerCount = mutableData.child("followers").getValue(String.class);
                int newFollowerCount = 1;
                if (currentFollowerCount != null) {
                    newFollowerCount = Integer.parseInt(currentFollowerCount) + 1;
                }
                mutableData.child("followers").setValue(String.valueOf(newFollowerCount));
                return com.google.firebase.database.Transaction.success(mutableData);
            }
            
            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    // Success - add to current user's following list
                    DatabaseReference currentUserFollowingRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(currentUserId)
                            .child("following_list")
                            .child(developerId);
                    
                    currentUserFollowingRef.setValue(true).addOnSuccessListener(aVoid -> {
                        Toast.makeText(itemView.getContext(), "Following", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        // Rollback UI on failure
                        updateFollowUI(false);
                        Toast.makeText(itemView.getContext(), "Failed to follow", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Rollback UI on failure
                    updateFollowUI(false);
                    Toast.makeText(itemView.getContext(), "Failed to follow", Toast.LENGTH_SHORT).show();
                }
                followButton.setEnabled(true);
            }
        });
    }

    private void unfollowUserOptimistic(String currentUserId, String developerId) {
        // Use Firebase transaction for atomic updates
        DatabaseReference developerRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(developerId);
        
        developerRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                String currentFollowerCount = mutableData.child("followers").getValue(String.class);
                int newFollowerCount = 0;
                if (currentFollowerCount != null) {
                    newFollowerCount = Math.max(0, Integer.parseInt(currentFollowerCount) - 1);
                }
                mutableData.child("followers").setValue(String.valueOf(newFollowerCount));
                return com.google.firebase.database.Transaction.success(mutableData);
            }
            
            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    // Success - remove from current user's following list
                    DatabaseReference currentUserFollowingRef = FirebaseDatabase.getInstance()
                            .getReference("users")
                            .child(currentUserId)
                            .child("following_list")
                            .child(developerId);
                    
                    currentUserFollowingRef.removeValue().addOnSuccessListener(aVoid -> {
                        Toast.makeText(itemView.getContext(), "Unfollowed", Toast.LENGTH_SHORT).show();
                    }).addOnFailureListener(e -> {
                        // Rollback UI on failure
                        updateFollowUI(true);
                        Toast.makeText(itemView.getContext(), "Failed to unfollow", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    // Rollback UI on failure
                    updateFollowUI(true);
                    Toast.makeText(itemView.getContext(), "Failed to unfollow", Toast.LENGTH_SHORT).show();
                }
                followButton.setEnabled(true);
            }
        });
    }

    private void updateFollowUI(boolean following) {
        isFollowing = following;
        
        if (following) {
            followText.setText("Following");
            followText.setTextColor(android.graphics.Color.RED);
        } else {
            followText.setText("Follow");
            followText.setTextColor(android.graphics.Color.WHITE);
        }
    }

    private void handleShareClick() {
        if (currentItem == null) return;
        
        String videoId = currentItem.id;
        String videoTitle = currentItem.title;
        
        // Increment share count in Firebase
        incrementShareCount(videoId);
        
        // Create share intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        
        // Create share text with video ID
        String shareText = "Check out this amazing video: " + videoTitle + "\n\nVideo ID: " + videoId + "\n\nShared from Instagame";
        
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Amazing video from Instagame");
        
        // Start activity chooser
        try {
            itemView.getContext().startActivity(Intent.createChooser(shareIntent, "Share via"));
        } catch (Exception e) {
            // Handle case where no sharing app is available
            Toast.makeText(itemView.getContext(), "No sharing app available", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void handleVideoPlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying()) {
                exoPlayer.setPlayWhenReady(false);
                android.util.Log.d("VideoViewHolder", "Video paused");
            } else {
                exoPlayer.setPlayWhenReady(true);
                android.util.Log.d("VideoViewHolder", "Video resumed");
            }
        }
    }
    
    private void handleMuteToggle() {
        if (exoPlayer != null) {
            float currentVolume = exoPlayer.getVolume();
            if (currentVolume > 0f) {
                // Mute
                exoPlayer.setVolume(0f);
                android.util.Log.d("VideoViewHolder", "Video muted");
            } else {
                // Unmute
                exoPlayer.setVolume(1f);
                android.util.Log.d("VideoViewHolder", "Video unmuted");
            }
        }
    }
    
    private void handlePlayClick() {
        if (currentItem == null) return;
        
        // Get the game ID from the current item
        String gameId = currentItem.gameId; // Assuming VideoItem has a gameId field
        
        // Pause current video before launching activity (same as double-click in reel view)
        if (exoPlayer != null && exoPlayer.isPlaying()) {
            exoPlayer.setPlayWhenReady(false);
        }
        
        // Launch Game_mode activity with game_id (same as double-click in reel view)
        if (gameId != null && !gameId.isEmpty()) {
            Intent intent = new Intent(itemView.getContext(), com.genzopia.Instagame.webgl_gameloading.Game_mode.class);
            intent.putExtra("game_id", gameId);
            itemView.getContext().startActivity(intent);
        } else {
            android.util.Log.e("VideoViewHolder", "Game ID is null or empty");
            Toast.makeText(itemView.getContext(), "Game information not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void incrementShareCount(String videoId) {
        // Use Firebase transaction for atomic update
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId);
        
        videoRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                String currentShareCount = mutableData.child("share_count").getValue(String.class);
                int newShareCount = 1;
                if (currentShareCount != null) {
                    newShareCount = Integer.parseInt(currentShareCount) + 1;
                }
                mutableData.child("share_count").setValue(String.valueOf(newShareCount));
                return com.google.firebase.database.Transaction.success(mutableData);
            }
            
            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (committed && error == null) {
                    // Success - share count updated
                    // You could also update the UI here if needed
                } else {
                    // Handle error silently for share count
                    // Share functionality still works even if count update fails
                }
            }
        });
    }

    private void checkIfLiked(String videoId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Use a more efficient approach - check if the user has liked this video
        DatabaseReference userLikedVideosRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUserId)
                .child("liked_videos")
                .child(videoId);
        
        userLikedVideosRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean liked = snapshot.exists();
                updateLikeUI(liked);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // On error, assume not liked
                updateLikeUI(false);
            }
        });
    }

    private void checkFollowState(String developerId) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Prevent checking if trying to follow yourself
        if (currentUserId.equals(developerId)) {
            followButton.setVisibility(View.GONE); // Hide follow button for own videos
            return;
        }
        
        // Check if current user is following this developer
        DatabaseReference currentUserFollowingRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(currentUserId)
                .child("following_list")
                .child(developerId);
        
        currentUserFollowingRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean following = snapshot.exists();
                updateFollowUI(following);
                
                // If already following, hide the follow button or show "Following"
                if (following) {
                    followButton.setVisibility(View.VISIBLE); // Keep visible but show "Following"
                } else {
                    followButton.setVisibility(View.VISIBLE); // Show "Follow" button
                }
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // On error, assume not following
                updateFollowUI(false);
                followButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private boolean handleProgressLineTouch(MotionEvent event) {
        if (exoPlayer != null && exoPlayer.getDuration() > 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Get touch coordinates relative to the progress container
                    float x = event.getX();
                    float width = progressContainer.getWidth();
                    
                    // Calculate progress (0.0 to 1.0)
                    float progress = x / width;
                    
                    // Clamp progress between 0 and 1
                    progress = Math.max(0f, Math.min(1f, progress));
                    
                    // Update progress line
                    progressLine.setScaleX(progress);
                    
                    // Seek video to the new position
                    long newPosition = (long) (progress * exoPlayer.getDuration());
                    exoPlayer.seekTo(newPosition);
                    
                    android.util.Log.d("VideoViewHolder", "Seeking to: " + newPosition + " (" + progress + ") at x=" + x + ", width=" + width);
                    return true;
                case MotionEvent.ACTION_UP:
                    android.util.Log.d("VideoViewHolder", "Seek bar touch ended");
                    return true;
            }
        } else {
            android.util.Log.d("VideoViewHolder", "Cannot seek - exoPlayer is null or duration is 0");
        }
        return false;
    }

    public void setupSeekBarAndTouchControls(PlayerView playerView, ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
        
        // Show PlayerView and switch from thumbnail to main player for actual playback
        playerView.setVisibility(View.VISIBLE);
        playerView.setPlayer(exoPlayer);
        android.util.Log.d("VideoViewHolder", "Switched to main player for video " + (currentItem != null ? currentItem.id : "unknown"));
        
        // Add player listener to track video loading and view count
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                android.util.Log.d("VideoViewHolder", "Playback state changed: " + playbackState);
                if (playbackState == Player.STATE_READY) {
                    android.util.Log.d("VideoViewHolder", "Video is ready to play");
                    
                    // Store video duration for view tracking
                    final VideoItem item = currentItem;
                    if (item != null && exoPlayer.getDuration() > 0) {
                        ViewCountManager.setVideoDuration(item.id, exoPlayer.getDuration());
                    }
                }
            }
            
            @Override
            public void onPlayerError(PlaybackException error) {
                android.util.Log.e("VideoViewHolder", "Player error: " + error.getMessage());
            }
            
            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                // Track view count when video reaches 60%
                final VideoItem item = currentItem;
                if (item != null && exoPlayer.getDuration() > 0) {
                    ViewCountManager.checkAndIncrementViewCount(
                        item.id, 
                        exoPlayer.getCurrentPosition(), 
                        exoPlayer.getDuration()
                    );
                }
            }
        });
        
        // Ensure progress line is always visible
        progressLine.setVisibility(View.VISIBLE);
        progressContainer.setVisibility(View.VISIBLE);
        progressLine.setScaleX(0f); // Start at 0 progress
        
        // Set pivot point to left edge so progress grows from left to right
        progressLine.setPivotX(0f);
        progressLine.setPivotY(progressLine.getHeight() / 2f);
        
        // Ensure progress bar stays on top
        progressLine.bringToFront();
        progressContainer.bringToFront();
        

        
        // Set up touch listener specifically for progress container
        progressContainer.setOnTouchListener((v, event) -> {
            android.util.Log.d("VideoViewHolder", "Progress container touch: action=" + event.getAction() + ", x=" + event.getX() + ", y=" + event.getY());
            return handleProgressLineTouch(event);
        });
        
        // Progress line is already in layout, just start updates
        startProgressUpdates();
        android.util.Log.d("VideoViewHolder", "Setup seek bar and touch controls");
        

    }

    void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                    float progress = (float) exoPlayer.getCurrentPosition() / exoPlayer.getDuration();
                    progressLine.setScaleX(progress);
                    
                    // Check for view count increment every 500ms
                    final VideoItem item = currentItem;
                    if (item != null && exoPlayer.getCurrentPosition() % 500 < 100) {
                        ViewCountManager.checkAndIncrementViewCount(
                            item.id,
                            exoPlayer.getCurrentPosition(),
                            exoPlayer.getDuration()
                        );
                    }
                }
                if (progressHandler != null) {
                    progressHandler.postDelayed(this, 100); // Update every 100ms
                }
            }
        };
        progressHandler.post(progressRunnable);
        android.util.Log.d("VideoViewHolder", "Started progress updates");
    }

    void stopProgressUpdates() {
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
            progressHandler = null;
        }
        progressRunnable = null;
    }

    // Method to handle touch up (release)
    public void onTouchUp() {
        if (isHolding && isPausedByHold) {
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(true);
                isPausedByHold = false;
            }
        }
        isHolding = false;
    }

    class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // Check if tap is in upper or lower half of the video
            float y = e.getY();
            float height = videoContainer.getHeight();
            
            android.util.Log.d("VideoViewHolder", "Single tap at y=" + y + ", height=" + height);
            
            if (y < height / 2) {
                // Upper half - toggle mute/unmute
                android.util.Log.d("VideoViewHolder", "Upper half tap - toggling mute/unmute");
                handleMuteToggle();
            } else {
                // Lower half - toggle play/pause
                android.util.Log.d("VideoViewHolder", "Lower half tap - toggling play/pause");
                handleVideoPlayPause();
            }
            return true;
        }
        
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            // Double tap - launch game (same as play button)
            android.util.Log.d("VideoViewHolder", "Double tap - launching game");
            handlePlayClick();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            // Handle hold to pause
            if (!isHolding && e1 != null) {
                long pressDuration = e2.getEventTime() - e1.getDownTime();
                if (pressDuration > 200) { // 200ms hold threshold
                    isHolding = true;
                    android.util.Log.d("VideoViewHolder", "Hold detected - pausing");
                    if (exoPlayer != null && exoPlayer.isPlaying()) {
                        exoPlayer.setPlayWhenReady(false);
                        isPausedByHold = true;
                    }
                }
            }
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // Alternative long press detection
            android.util.Log.d("VideoViewHolder", "Long press detected - pausing");
            isHolding = true;
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.setPlayWhenReady(false);
                isPausedByHold = true;
            }
        }
    }

    public void bind(VideoItem videoItem, Context context) {
        this.currentItem = videoItem;
        
        // Reset view tracking for new video
        resetViewTracking();
        
        // Reset PlayerView to ensure clean state
        resetPlayerView();
        
        // Set channel info
        channelName.setText(videoItem.channelName);
        title.setText(videoItem.title);
        viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);
        description.setText(videoItem.description); // Set description text
        
        // Load channel icon using Glide
        if (videoItem.channelIconUrl != null && !videoItem.channelIconUrl.isEmpty()) {
            Glide.with(context)
                    .load(videoItem.channelIconUrl)
                    .placeholder(R.drawable.demo_user)
                    .error(R.drawable.demo_user)
                    .into(channelIcon);
        } else {
            channelIcon.setImageResource(R.drawable.demo_user);
        }
        
        // Show video using the video URL
        showVideo(videoItem);
        
        // Ensure PlayerView is hidden initially to show thumbnail
        playerView.setVisibility(View.INVISIBLE);
        
        // Check if current user has liked this video
        checkIfLiked(videoItem.id);
        
        // Check follow state
        checkFollowState(videoItem.developerId); // Use the actual developer ID
        
        // Debug logging for three-dot menu
        android.util.Log.d("VideoViewHolder", "Three-dot menu visibility: " + threeDotMenu.getVisibility());
        android.util.Log.d("VideoViewHolder", "Three-dot menu clickable: " + threeDotMenu.isClickable());
        android.util.Log.d("VideoViewHolder", "Three-dot menu focusable: " + threeDotMenu.isFocusable());
        android.util.Log.d("VideoViewHolder", "Three-dot menu width: " + threeDotMenu.getWidth() + ", height: " + threeDotMenu.getHeight());
        
        // Debug logging
        android.util.Log.d("VideoViewHolder", "Bound video item: " + videoItem.id);
        android.util.Log.d("VideoViewHolder", "PlayerView visibility: " + playerView.getVisibility());
        android.util.Log.d("VideoViewHolder", "PlayerView width: " + playerView.getWidth() + ", height: " + playerView.getHeight());
        android.util.Log.d("VideoViewHolder", "Video URL: " + videoItem.videoUrl);
        
        // Add a layout listener to regenerate thumbnail when dimensions are available
        videoContainer.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove the listener to avoid multiple calls
                videoContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                
                // If we have a video item but no thumbnail yet, generate it now that dimensions are available
                if (currentVideoItem != null && !hasThumbnail && videoContainer.getWidth() > 0 && videoContainer.getHeight() > 0) {
                    android.util.Log.d("VideoViewHolder", "VideoContainer dimensions available, generating thumbnail");
                    showThumbnail(currentVideoItem);
                }
            }
        });
    }
    
    private void showThumbnail(VideoItem videoItem) {
        if (videoItem.videoUrl == null || videoItem.videoUrl.isEmpty()) {
            android.util.Log.d("VideoViewHolder", "Cannot generate thumbnail - no video URL");
            showFallbackThumbnail();
            return;
        }
        
        // Store the current video item for later thumbnail restoration
        this.currentVideoItem = videoItem;
        
        android.util.Log.d("VideoViewHolder", "Generating thumbnail for video: " + videoItem.id + " URL: " + videoItem.videoUrl);
        
        // Show loading state initially (dark background)
        videoContainer.setBackgroundColor(android.graphics.Color.parseColor("#1F1F1F"));
        
        // Test URL accessibility first
        testVideoUrlAccessibility(videoItem);
    }
    
    private void testVideoUrlAccessibility(VideoItem videoItem) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(videoItem.videoUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                android.util.Log.d("VideoViewHolder", "Video URL test - Response code: " + responseCode + " for video: " + videoItem.id);
                
                if (responseCode == 200) {
                    android.util.Log.d("VideoViewHolder", "Video URL is accessible, proceeding with thumbnail generation");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        tryMediaMetadataRetriever(videoItem);
                    });
                } else {
                    android.util.Log.w("VideoViewHolder", "Video URL not accessible (code: " + responseCode + "), showing fallback");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        showFallbackThumbnail();
                    });
                }
                
            } catch (Exception e) {
                android.util.Log.e("VideoViewHolder", "Error testing video URL: " + e.getMessage());
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    showFallbackThumbnail();
                });
            }
        }).start();
    }
    
    private void tryMediaMetadataRetriever(VideoItem videoItem) {
        new Thread(() -> {
            android.media.MediaMetadataRetriever retriever = null;
            try {
                android.util.Log.d("VideoViewHolder", "Trying MediaMetadataRetriever for video: " + videoItem.id);
                retriever = new android.media.MediaMetadataRetriever();
                
                // Set network timeout for better error handling
                retriever.setDataSource(videoItem.videoUrl);
                
                // Try to get frame at 1 second (1,000,000 microseconds)
                android.graphics.Bitmap thumbnail = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                
                if (thumbnail == null) {
                    android.util.Log.d("VideoViewHolder", "No frame at 1 second, trying at 0 seconds");
                    // If no frame at 1 second, try at 0 seconds
                    thumbnail = retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }
                
                if (thumbnail == null) {
                    android.util.Log.d("VideoViewHolder", "No frame at 0 seconds, trying any available frame");
                    // If still no frame, try any available frame
                    thumbnail = retriever.getFrameAtTime();
                }
                
                if (retriever != null) {
                    retriever.release();
                }
                
                if (thumbnail != null) {
                    android.util.Log.d("VideoViewHolder", "MediaMetadataRetriever succeeded for video: " + videoItem.id);
                    // Scale thumbnail to fit the video container dimensions
                    final android.graphics.Bitmap scaledThumbnail = scaleBitmap(thumbnail, videoContainer.getWidth(), videoContainer.getHeight());
                    safeRecycleBitmap(thumbnail); // Free the original bitmap safely
                    
                    // Create and store the thumbnail drawable on main thread
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        try {
                            // Check if this view holder is still bound to the same video item
                            if (currentVideoItem != null && currentVideoItem.id.equals(videoItem.id)) {
                                storedThumbnail = new android.graphics.drawable.BitmapDrawable(
                                    itemView.getContext().getResources(), 
                                    scaledThumbnail
                                );
                                
                                // Set the bitmap as background of the video container instead of player view
                                videoContainer.setBackground(storedThumbnail);
                                
                                hasThumbnail = true;
                                
                                android.util.Log.d("VideoViewHolder", "Successfully captured thumbnail for video: " + videoItem.id);
                            } else {
                                android.util.Log.d("VideoViewHolder", "View holder recycled, discarding thumbnail");
                                scaledThumbnail.recycle();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("VideoViewHolder", "Error setting thumbnail: " + e.getMessage());
                            showFallbackThumbnail();
                        }
                    });
                } else {
                    android.util.Log.d("VideoViewHolder", "MediaMetadataRetriever failed, trying ExoPlayer fallback");
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        tryExoPlayerThumbnail(videoItem);
                    });
                }
                
            } catch (Exception e) {
                android.util.Log.e("VideoViewHolder", "Error with MediaMetadataRetriever: " + e.getMessage());
                if (retriever != null) {
                    try {
                        retriever.release();
                    } catch (Exception releaseException) {
                        android.util.Log.e("VideoViewHolder", "Error releasing retriever: " + releaseException.getMessage());
                    }
                }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    tryExoPlayerThumbnail(videoItem);
                });
            }
        }).start();
    }
    
    private void tryExoPlayerThumbnail(VideoItem videoItem) {
        android.util.Log.d("VideoViewHolder", "Trying ExoPlayer thumbnail generation for video: " + videoItem.id);
        
        // Create a temporary ExoPlayer for thumbnail generation
        ExoPlayer thumbnailPlayer = new ExoPlayer.Builder(itemView.getContext()).build();
        
        try {
            String videoUri = videoItem.videoUrl;
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(itemView.getContext(), "instagame-agent");
            MediaItem mediaItem = MediaItem.fromUri(videoUri);
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
            
            thumbnailPlayer.setMediaSource(mediaSource);
            thumbnailPlayer.prepare();
            thumbnailPlayer.seekTo(1000); // Seek to 1 second for thumbnail
            
            thumbnailPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        android.util.Log.d("VideoViewHolder", "ExoPlayer ready, capturing frame");
                        // Temporarily set the player to capture frame
                        playerView.setPlayer(thumbnailPlayer);
                        
                        // Wait a bit for the frame to be rendered
                        new android.os.Handler().postDelayed(() -> {
                            try {
                                android.graphics.Bitmap frameBitmap = captureFrameFromPlayerView();
                                if (frameBitmap != null) {
                                    android.util.Log.d("VideoViewHolder", "ExoPlayer thumbnail capture succeeded");
                                    // Scale the bitmap to fit the video container
                                    android.graphics.Bitmap scaledBitmap = scaleBitmap(frameBitmap, videoContainer.getWidth(), videoContainer.getHeight());
                                    frameBitmap.recycle(); // Free the original bitmap
                                    
                                    // Create and store the thumbnail drawable
                                    storedThumbnail = new android.graphics.drawable.BitmapDrawable(
                                        itemView.getContext().getResources(), 
                                        scaledBitmap
                                    );
                                    
                                    // Set the bitmap as background of the video container instead of player view
                                    videoContainer.setBackground(storedThumbnail);
                                    
                                    hasThumbnail = true;
                                    
                                    android.util.Log.d("VideoViewHolder", "Successfully captured thumbnail with ExoPlayer for video: " + videoItem.id);
                                } else {
                                    android.util.Log.d("VideoViewHolder", "ExoPlayer frame capture failed, showing fallback");
                                    showFallbackThumbnail();
                                }
                            } catch (Exception e) {
                                android.util.Log.e("VideoViewHolder", "Error capturing ExoPlayer thumbnail: " + e.getMessage());
                                showFallbackThumbnail();
                            }
                            
                            // Detach player and release
                            playerView.setPlayer(null);
                            thumbnailPlayer.release();
                        }, 1000); // Wait 1 second to ensure frame is rendered
                    }
                }
                
                @Override
                public void onPlayerError(PlaybackException error) {
                    android.util.Log.e("VideoViewHolder", "ExoPlayer thumbnail error: " + error.getMessage());
                    thumbnailPlayer.release();
                    showFallbackThumbnail();
                }
            });
            
        } catch (Exception e) {
            android.util.Log.e("VideoViewHolder", "Error setting up ExoPlayer thumbnail: " + e.getMessage());
            thumbnailPlayer.release();
            showFallbackThumbnail();
        }
    }
    
    private android.graphics.Bitmap captureFrameFromPlayerView() {
        try {
            // Create a bitmap with the same size as the player view
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                playerView.getWidth(),
                playerView.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888
            );
            
            // Create a canvas to draw the player view content
            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
            
            // Draw the player view content to the canvas
            playerView.draw(canvas);
            
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("VideoViewHolder", "Error capturing frame: " + e.getMessage());
            return null;
        }
    }
    

    
    private void safeRecycleBitmap(android.graphics.Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                bitmap.recycle();
            } catch (Exception e) {
                android.util.Log.e("VideoViewHolder", "Error recycling bitmap: " + e.getMessage());
            }
        }
    }
    
    private android.graphics.Bitmap scaleBitmap(android.graphics.Bitmap bitmap, int targetWidth, int targetHeight) {
        if (bitmap == null || bitmap.isRecycled()) return null;
        
        // If target dimensions are not set yet, use the original bitmap
        if (targetWidth <= 0 || targetHeight <= 0) {
            return bitmap;
        }
        
        android.graphics.Bitmap scaledBitmap = null;
        android.graphics.Bitmap croppedBitmap = null;
        
        try {
            // Calculate scaling to maintain aspect ratio
            float scaleX = (float) targetWidth / bitmap.getWidth();
            float scaleY = (float) targetHeight / bitmap.getHeight();
            float scale = Math.max(scaleX, scaleY); // Use the larger scale to ensure coverage
            
            int newWidth = Math.round(bitmap.getWidth() * scale);
            int newHeight = Math.round(bitmap.getHeight() * scale);
            
            // Create scaled bitmap
            scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            
            // If the scaled bitmap is larger than target, crop it to center
            if (newWidth > targetWidth || newHeight > targetHeight) {
                int x = (newWidth - targetWidth) / 2;
                int y = (newHeight - targetHeight) / 2;
                
                // Ensure we don't go out of bounds
                x = Math.max(0, Math.min(x, newWidth - targetWidth));
                y = Math.max(0, Math.min(y, newHeight - targetHeight));
                
                croppedBitmap = android.graphics.Bitmap.createBitmap(scaledBitmap, x, y, targetWidth, targetHeight);
                safeRecycleBitmap(scaledBitmap);
                return croppedBitmap;
            }
            
            return scaledBitmap;
        } catch (Exception e) {
            android.util.Log.e("VideoViewHolder", "Error scaling bitmap: " + e.getMessage());
            safeRecycleBitmap(scaledBitmap);
            safeRecycleBitmap(croppedBitmap);
            return null;
        }
    }
    
    public void showThumbnailWhenStopped() {
        // Hide PlayerView to show thumbnail
        playerView.setVisibility(View.INVISIBLE);
        
        if (currentVideoItem != null && hasThumbnail && storedThumbnail != null) {
            android.util.Log.d("VideoViewHolder", "Restoring stored thumbnail for video: " + currentVideoItem.id);
            
            // Restore the stored thumbnail
            videoContainer.setBackground(storedThumbnail);
            
            // No play icon overlay - just pure thumbnail
        } else if (currentVideoItem != null) {
            android.util.Log.d("VideoViewHolder", "No stored thumbnail, generating new one for video: " + currentVideoItem.id);
            showThumbnail(currentVideoItem);
        } else {
            // No video item, show fallback
            showFallbackThumbnail();
        }
    }
    
    public void regenerateThumbnail() {
        if (currentVideoItem != null) {
            android.util.Log.d("VideoViewHolder", "Regenerating thumbnail for video: " + currentVideoItem.id);
            hasThumbnail = false;
            storedThumbnail = null;
            showThumbnail(currentVideoItem);
        }
    }
    
    public void debugThumbnailGeneration() {
        if (currentVideoItem != null) {
            android.util.Log.d("VideoViewHolder", "=== DEBUG THUMBNAIL GENERATION ===");
            android.util.Log.d("VideoViewHolder", "Video ID: " + currentVideoItem.id);
            android.util.Log.d("VideoViewHolder","Video URL: " + currentVideoItem.videoUrl);
            android.util.Log.d("VideoViewHolder","Has thumbnail: " + hasThumbnail);
            android.util.Log.d("VideoViewHolder","Stored thumbnail: " + (storedThumbnail != null ? "exists" : "null"));
            android.util.Log.d("VideoViewHolder","VideoContainer width: " + videoContainer.getWidth() + ", height: " + videoContainer.getHeight());
            android.util.Log.d("VideoViewHolder","PlayerView visibility: " + playerView.getVisibility());
            android.util.Log.d("VideoViewHolder","Current background: " + videoContainer.getBackground());
            android.util.Log.d("VideoViewHolder","=== END DEBUG ===");
        } else {
            android.util.Log.d("VideoViewHolder", "No current video item for debugging");
        }
    }
    
    public void forceShowFallbackThumbnail() {
        android.util.Log.d("VideoViewHolder", "Forcing fallback thumbnail display");
        showFallbackThumbnail();
    }
    
    public void testSeekBar() {
        android.util.Log.d("VideoViewHolder", "=== TESTING SEEK BAR ===");
        android.util.Log.d("VideoViewHolder", "Progress container: " + (progressContainer != null ? "exists" : "null"));
        android.util.Log.d("VideoViewHolder", "Progress line: " + (progressLine != null ? "exists" : "null"));
        android.util.Log.d("VideoViewHolder", "ExoPlayer: " + (exoPlayer != null ? "exists" : "null"));
        if (exoPlayer != null) {
            android.util.Log.d("VideoViewHolder", "Duration: " + exoPlayer.getDuration());
            android.util.Log.d("VideoViewHolder", "Current position: " + exoPlayer.getCurrentPosition());
        }
        android.util.Log.d("VideoViewHolder", "=== END TEST ===");
    }
    

    
    private void showFallbackThumbnail() {
        android.util.Log.d("VideoViewHolder", "Showing fallback thumbnail for video: " + (currentVideoItem != null ? currentVideoItem.id : "unknown"));
        
        // Create a gradient background as fallback thumbnail
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{
                android.graphics.Color.parseColor("#2C2C2C"),
                android.graphics.Color.parseColor("#1A1A1A")
            }
        );
        gradient.setCornerRadius(8f);
        
        videoContainer.setBackground(gradient);
        
        // Add a subtle play icon overlay
        android.graphics.drawable.Drawable playIcon = itemView.getContext().getDrawable(android.R.drawable.ic_media_play);
        if (playIcon != null) {
            playIcon.setTint(android.graphics.Color.WHITE);
            playIcon.setAlpha(128); // 50% opacity
            
            // Create a layer drawable with gradient background and play icon
            android.graphics.drawable.Drawable[] layers = {gradient, playIcon};
            android.graphics.drawable.LayerDrawable layerDrawable = new android.graphics.drawable.LayerDrawable(layers);
            
            // Center the play icon
            int iconSize = 80;
            int left = (videoContainer.getWidth() - iconSize) / 2;
            int top = (videoContainer.getHeight() - iconSize) / 2;
            layerDrawable.setLayerInset(1, left, top, left, top);
            
            videoContainer.setBackground(layerDrawable);
        }
        
        // Mark that we have a fallback thumbnail
        hasThumbnail = true;
        storedThumbnail = playerView.getBackground();
    }
    
    public void hideThumbnail() {
        // Hide the PlayerView when video starts playing to show thumbnail
        playerView.setVisibility(View.INVISIBLE);
        
        android.util.Log.d("VideoViewHolder", "Hidden PlayerView for video: " + (currentVideoItem != null ? currentVideoItem.id : "unknown"));
    }
    
    public void showPlayerView() {
        // Show the PlayerView when video is actually playing
        playerView.setVisibility(View.VISIBLE);
        
        android.util.Log.d("VideoViewHolder", "Showed PlayerView for video: " + (currentVideoItem != null ? currentVideoItem.id : "unknown"));
    }
    
    private void showVideo(VideoItem videoItem) {
        if (videoItem.videoUrl != null && !videoItem.videoUrl.isEmpty()) {
            // Set up the player view for shared player
            String videoUri = videoItem.videoUrl;
            if (videoUri == null || videoUri.isEmpty()) {
                playerView.setVisibility(View.INVISIBLE);
                android.util.Log.d("VideoViewHolder", "No video URL for video " + videoItem.id);
                return;
            }
            
            playerView.setVisibility(View.VISIBLE);
            android.util.Log.d("VideoViewHolder", "Setting up player view for video: " + videoUri);
            
            // The actual video playback will be handled by the HomeAdapter's shared player
            // This method just sets up the player view and shows thumbnail
            showThumbnail(videoItem);
        } else {
            playerView.setVisibility(View.INVISIBLE);
            android.util.Log.d("VideoViewHolder", "No video URL for video " + videoItem.id);
            showFallbackThumbnail();
        }
    }
    
    private void showVideoDetailsBottomSheet() {
        if (currentItem == null) return;
        
        VideoDetailsBottomSheet bottomSheet = VideoDetailsBottomSheet.newInstance(
            currentItem.id,
            currentItem.title,
            currentItem.description
        );
        
        // Get the activity context
        Context context = itemView.getContext();
        if (context instanceof androidx.fragment.app.FragmentActivity) {
            androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
            bottomSheet.show(activity.getSupportFragmentManager(), "VideoDetailsBottomSheet");
        }
    }
    
    // Reset view tracking when video is bound
    public void resetViewTracking() {
        if (currentItem != null) {
            ViewCountManager.resetVideoViewTracking(currentItem.id);
            hasIncrementedView = false;
        }
    }
    
    private void resetPlayerView() {
        // Release any existing player
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        
        // Reset PlayerView - hide it initially to show thumbnail
        playerView.setPlayer(null);
        playerView.setVisibility(View.INVISIBLE);
        
        // Reset progress
        if (progressLine != null) {
            progressLine.setScaleX(0f);
            progressLine.setVisibility(View.VISIBLE);
        }
        if (progressContainer != null) {
            progressContainer.setVisibility(View.VISIBLE);
        }
        
        // Reset thumbnail state safely
        clearStoredThumbnail();
        currentVideoItem = null;
        
        android.util.Log.d("VideoViewHolder", "Reset PlayerView");
    }
    
    private void clearStoredThumbnail() {
        if (storedThumbnail != null) {
            try {
                // Clear the background
                videoContainer.setBackground(null);
                storedThumbnail = null;
            } catch (Exception e) {
                android.util.Log.e("VideoViewHolder", "Error clearing stored thumbnail: " + e.getMessage());
            }
        }
        hasThumbnail = false;
    }
}
