package com.genzopia.Instagame.reelview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.genzopia.Instagame.ui.components.VideoDetailsBottomSheet;
import com.genzopia.Instagame.utils.ViewCountManager;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.PlaybackException;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.ReelViewHolder> {

    private Context context;
    private List<ReelItem> reelItems;
    private RecyclerView recyclerView;
    private SimpleExoPlayer sharedPlayer;
    private ReelViewHolder currentPlayingViewHolder = null;
    private int currentPlayingPosition = -1;
    private boolean isPausedByHold = false;
    
    // Follow state management
    private java.util.Map<String, Boolean> followStates = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.Map<String, java.util.List<ReelViewHolder>> developerViewHolders = new java.util.concurrent.ConcurrentHashMap<>();

    public ReelAdapter(Context context, List<ReelItem> reelItems, RecyclerView recyclerView) {
        this.context = context;
        this.reelItems = reelItems;
        this.recyclerView = recyclerView;
        initializePlayer();
    }

    private void initializePlayer() {
        sharedPlayer = new SimpleExoPlayer.Builder(context).build();
        sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        sharedPlayer.setPlayWhenReady(false);
    }

    @NonNull
    @Override
    public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reel_item, parent, false);
        return new ReelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
        ReelItem item = reelItems.get(position);
        holder.bind(item, position);
    }

    @Override
    public int getItemCount() {
        return reelItems.size();
    }

    @Override
    public void onViewRecycled(@NonNull ReelViewHolder holder) {
        super.onViewRecycled(holder);
        if (currentPlayingViewHolder == holder) {
            pauseCurrentVideo();
        }
        
        // Unregister this ViewHolder from follow state management
        if (holder.position >= 0 && holder.position < reelItems.size()) {
            String developerId = reelItems.get(holder.position).getDeveloperId();
            unregisterViewHolderForDeveloper(developerId, holder);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ReelViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        // Only pause if this is the currently playing view and it's completely out of view
        if (currentPlayingViewHolder == holder) {
            // Check if the view is completely out of view
            if (recyclerView.getLayoutManager() != null) {
                int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                int lastVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findLastVisibleItemPosition();
                
                if (holder.getAdapterPosition() < firstVisible || holder.getAdapterPosition() > lastVisible) {
                    // View is completely out of view, pause the video
                    pauseCurrentVideo();
                }
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ReelViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        // Don't auto-play here, let the scroll listener handle it
    }

    private void playVideoAtPosition(int position) {
        if (position < 0 || position >= reelItems.size()) return;
        if (currentPlayingPosition == position) return;
        pauseCurrentVideo();
        ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) return;
        ReelItem item = reelItems.get(position);
        String videoUri = item.getVideoUrl() != null ? item.getVideoUrl() : item.getVideoId();
        if (videoUri == null || videoUri.isEmpty() || videoUri.equals(item.getVideoId())) {
            holder.playerView.setVisibility(View.INVISIBLE);
            holder.tvTitle.setText("Video unavailable");
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            isPausedByHold = false;
            return;
        }
        holder.playerView.setVisibility(View.VISIBLE);
        Log.e("test5557","videouri="+videoUri+"   laodingurl="+MediaItem.fromUri(videoUri));
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        MediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        sharedPlayer.setMediaSource(hlsSource);
        sharedPlayer.prepare();
        sharedPlayer.setPlayWhenReady(true);
        sharedPlayer.addListener(new Player.Listener() {
            boolean triedFallback = false;
            @Override
            public void onPlayerError(PlaybackException error) {
                if (!triedFallback) {
                    triedFallback = true;
                    MediaSource mp4Source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    sharedPlayer.setMediaSource(mp4Source);
                    sharedPlayer.prepare();
                    sharedPlayer.setPlayWhenReady(true);
                }
            }
            
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    // Store video duration for view tracking
                    if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                        ViewCountManager.setVideoDuration(item.getVideoId(), sharedPlayer.getDuration());
                    }
                }
            }
            
            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                // Track view count when video reaches 60%
                if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                    ViewCountManager.checkAndIncrementViewCount(
                        item.getVideoId(),
                        sharedPlayer.getCurrentPosition(),
                        sharedPlayer.getDuration()
                    );
                }
            }
        });
        holder.playerView.setPlayer(sharedPlayer);
        currentPlayingViewHolder = holder;
        currentPlayingPosition = position;
        isPausedByHold = false;
        holder.startProgressUpdates();
    }

    private void pauseCurrentVideo() {
        if (sharedPlayer != null) {
            sharedPlayer.setPlayWhenReady(false);
        }
        if (currentPlayingViewHolder != null) {
            currentPlayingViewHolder.stopProgressUpdates();
            // Detach player from the current view
            currentPlayingViewHolder.playerView.setPlayer(null);
            currentPlayingViewHolder = null;
        }
        currentPlayingPosition = -1;
        isPausedByHold = false;
    }

    public void pausePlayers() {
        pauseCurrentVideo();
    }

    public void resumePlayers() {
        // Find the first visible item and play it
        if (recyclerView.getLayoutManager() != null) {
            int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (firstVisible >= 0 && firstVisible < getItemCount()) {
                // Only play if it's different from current position
                if (currentPlayingPosition != firstVisible) {
                    playVideoAtPosition(firstVisible);
                }
            }
        }
    }

    public void ensureOnlyCurrentVideoPlays() {
        // Pause all videos except the current one
        if (recyclerView.getLayoutManager() != null) {
            int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            if (firstVisible >= 0 && firstVisible < getItemCount()) {
                if (currentPlayingPosition != firstVisible) {
                    playVideoAtPosition(firstVisible);
                }
            }
        }
    }

    public void releaseAllPlayers() {
        pauseCurrentVideo();
        if (sharedPlayer != null) {
            sharedPlayer.release();
            sharedPlayer = null;
        }
        
        // Clear follow state cache
        followStates.clear();
        developerViewHolders.clear();
    }
    
    // Follow state management methods
    private void registerViewHolderForDeveloper(String developerId, ReelViewHolder holder) {
        if (developerId == null) return;
        
        if (!developerViewHolders.containsKey(developerId)) {
            developerViewHolders.put(developerId, new java.util.ArrayList<>());
        }
        
        java.util.List<ReelViewHolder> holders = developerViewHolders.get(developerId);
        if (holders != null && !holders.contains(holder)) {
            holders.add(holder);
            Log.d("FollowDebug", "Registered ViewHolder for developer: " + developerId + ", total holders: " + holders.size());
        }
    }
    
    private void unregisterViewHolderForDeveloper(String developerId, ReelViewHolder holder) {
        if (developerId == null) return;
        
        java.util.List<ReelViewHolder> holders = developerViewHolders.get(developerId);
        if (holders != null) {
            holders.remove(holder);
            Log.d("FollowDebug", "Unregistered ViewHolder for developer: " + developerId + ", remaining holders: " + holders.size());
        }
    }
    
    private void updateAllViewHoldersForDeveloper(String developerId, boolean isFollowing) {
        followStates.put(developerId, isFollowing);
        
        java.util.List<ReelViewHolder> holders = developerViewHolders.get(developerId);
        if (holders != null) {
            Log.d("FollowDebug", "Updating " + holders.size() + " ViewHolders for developer: " + developerId + " to following: " + isFollowing);
            for (ReelViewHolder holder : holders) {
                if (holder != null && holder.followButton != null) {
                    holder.updateFollowUI(isFollowing);
                }
            }
        } else {
            Log.d("FollowDebug", "No ViewHolders found for developer: " + developerId);
        }
    }
    
    private void checkFollowStateFromFirebase(String developerId, ReelViewHolder holder) {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        
        // Prevent checking if trying to follow yourself
        if (currentUserId.equals(developerId)) {
            holder.followButton.setVisibility(View.GONE); // Hide follow button for own videos
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
                Log.d("FollowDebug", "Firebase follow status for " + developerId + ": " + following);
                
                // Cache the result and update all ViewHolders for this developer
                updateAllViewHoldersForDeveloper(developerId, following);
            }
            
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // On error, assume not following
                Log.d("FollowDebug", "Error checking follow status: " + error.getMessage());
                updateAllViewHoldersForDeveloper(developerId, false);
            }
        });
    }

    public void handleScrollStateChange(int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            // When scrolling stops, find the first visible item and play it
            if (recyclerView.getLayoutManager() != null) {
                int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
                if (firstVisible >= 0 && firstVisible < getItemCount()) {
                    // Only play if it's different from current position
                    if (currentPlayingPosition != firstVisible) {
                        playVideoAtPosition(firstVisible);
                    }
                }
            }
        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            // Don't pause video when scrolling starts, let it continue playing
            // Only pause when the view is completely out of view
        }
    }
    
    // Method to refresh follow states for all ViewHolders
    public void refreshFollowStates() {
        followStates.clear();
        // This will trigger re-checking of follow states when ViewHolders are bound
        notifyDataSetChanged();
    }
    
    // Method to pre-load follow states for all developers in the current list
    public void preloadFollowStates() {
        String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId == null) return;
        
        // Get unique developer IDs from the current reel list
        java.util.Set<String> developerIds = new java.util.HashSet<>();
        for (ReelItem item : reelItems) {
            if (item.getDeveloperId() != null && !item.getDeveloperId().equals(currentUserId)) {
                developerIds.add(item.getDeveloperId());
            }
        }
        
        Log.d("FollowDebug", "Preloading follow states for " + developerIds.size() + " developers");
        
        // Check follow state for each developer
        for (String developerId : developerIds) {
            if (!followStates.containsKey(developerId)) {
                DatabaseReference currentUserFollowingRef = FirebaseDatabase.getInstance()
                        .getReference("users")
                        .child(currentUserId)
                        .child("following_list")
                        .child(developerId);
                
                currentUserFollowingRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean following = snapshot.exists();
                        Log.d("FollowDebug", "Preloaded follow state for " + developerId + ": " + following);
                        followStates.put(developerId, following);
                    }
                    
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.d("FollowDebug", "Error preloading follow state for " + developerId + ": " + error.getMessage());
                        followStates.put(developerId, false);
                    }
                });
            }
        }
    }

    public class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView tvTitle, tvLikes;
        TextView tvGameName;
        CircleImageView profile_image;
        View progressLine;
        GestureDetector gestureDetector;
        String currentVideoId;
        int position;
        private android.os.Handler progressHandler;
        private Runnable progressRunnable;
        private boolean isHolding = false;
        
        // Three-dot menu
        ImageView threeDotMenu;
        
        // Like button components
        LinearLayout likeButton;
        ImageView likeIcon;
        boolean isLiked = false;
        
        // Share button components
        LinearLayout shareButton;
        
        // Follow button components
        LinearLayout followButton;
        TextView followText;
        boolean isFollowing = false;

        @SuppressLint("ClickableViewAccessibility")
        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvLikes = itemView.findViewById(R.id.tv_likes);

            tvGameName = itemView.findViewById(R.id.tv_game_name);
            profile_image = itemView.findViewById(R.id.profile_image);
            progressLine = itemView.findViewById(R.id.progress_line);
            View progressContainer = itemView.findViewById(R.id.progress_container);
            
            // Initialize like button components
            likeButton = itemView.findViewById(R.id.like_button);
            likeIcon = itemView.findViewById(R.id.like_icon);
            
            // Initialize share button components
            shareButton = itemView.findViewById(R.id.share_button);
            
            // Initialize follow button components
            followButton = itemView.findViewById(R.id.follow_button);
            followText = itemView.findViewById(R.id.tv_follow_text);
            
            // Initialize three-dot menu
            threeDotMenu = itemView.findViewById(R.id.threeDotMenu);
            
            // Set up three-dot menu click listener
            threeDotMenu.setOnClickListener(v -> {
                android.util.Log.d("ReelViewHolder", "Three-dot menu clicked for video: " + currentVideoId);
                showVideoDetailsBottomSheet();
            });
            
            // Ensure follow button is visible by default
            if (followButton != null) {
                followButton.setVisibility(View.VISIBLE);
            }

            playerView.setUseController(false);
            gestureDetector = new GestureDetector(context, new CustomGestureListener());
            
            // Set up like button click listener
            likeButton.setOnClickListener(v -> {
                handleLikeClick();
            });
            
            // Set up share button click listener
            shareButton.setOnClickListener(v -> {
                handleShareClick();
            });
            
            // Set up follow button click listener
            followButton.setOnClickListener(v -> {
                handleFollowClick();
            });
            
            // Set up profile image click listener
            profile_image.setOnClickListener(v -> {
                String developerId = reelItems.get(position).getDeveloperId();
                if (developerId != null && !developerId.isEmpty()) {
                    // Navigate to ChannelActivity with developer ID
                    Intent intent = new Intent(context, com.genzopia.Instagame.channel_view.ChannelActivity.class);
                    intent.putExtra("developer_id", developerId);
                    context.startActivity(intent);
                }
            });

            // Add touch listener for progress container (much larger touch area)
            progressContainer.setOnTouchListener((v, event) -> {
                if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.getDuration() > 0) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                        case MotionEvent.ACTION_MOVE:
                            float x = event.getX();
                            float width = progressContainer.getWidth();
                            float progress = x / width;
                            
                            // Clamp progress between 0 and 1
                            progress = Math.max(0f, Math.min(1f, progress));
                            
                            // Update progress line
                            progressLine.setScaleX(progress);
                            
                            // Seek video to the new position
                            long newPosition = (long) (progress * sharedPlayer.getDuration());
                            sharedPlayer.seekTo(newPosition);
                            
                            return true;
                        case MotionEvent.ACTION_UP:
                            return true;
                    }
                }
                return false;
            });

            itemView.setOnTouchListener((v, event) -> {
                // Let the progress container handle its own touches
                if (progressContainer.getVisibility() == View.VISIBLE) {
                    // Check if touch is on the progress container area
                    float touchY = event.getY();
                    float containerY = progressContainer.getY();
                    float containerHeight = progressContainer.getHeight();
                    
                    if (touchY >= containerY && touchY <= containerY + containerHeight) {
                        // Let the progress container handle this touch
                        return false;
                    }
                }
                
                boolean handled = gestureDetector.onTouchEvent(event);
                
                // Handle touch up for hold/pause functionality
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    onTouchUp();
                }
                
                return handled;
            });
        }

        void bind(ReelItem reelItem, int pos) {
            position = pos;
            tvTitle.setText(reelItem.getTitle());
            tvLikes.setText(reelItem.getLikeCount() + " likes");
            
            // Set default image
            profile_image.setImageResource(R.drawable.demo_user);
            
            // Load profile image from Firebase using userId
            loadProfileImage(reelItem.getDeveloperId());
            
            // Check if current user has liked this video
            checkIfLiked(reelItem.getVideoId());
            
            // Register this ViewHolder for the developer and check follow state
            String developerId = reelItem.getDeveloperId();
            registerViewHolderForDeveloper(developerId, this);
            
            // Check follow state - if cached, use immediately; if not, show default and check Firebase
            if (followStates.containsKey(developerId)) {
                boolean cachedState = followStates.get(developerId);
                Log.d("FollowDebug", "Using cached follow state for " + developerId + ": " + cachedState);
                updateFollowUI(cachedState);
            } else {
                // Show default state and check Firebase in background
                updateFollowUI(false);
                checkFollowStateFromFirebase(developerId, this);
            }

            currentVideoId = reelItem.getVideoId();
            
            // Reset view tracking for new video
            if (currentVideoId != null) {
                ViewCountManager.resetVideoViewTracking(currentVideoId);
            }
            
            // Debug logging for three-dot menu
            android.util.Log.d("ReelViewHolder", "Three-dot menu visibility: " + threeDotMenu.getVisibility());
            android.util.Log.d("ReelViewHolder", "Three-dot menu clickable: " + threeDotMenu.isClickable());
            android.util.Log.d("ReelViewHolder", "Three-dot menu focusable: " + threeDotMenu.isFocusable());
            android.util.Log.d("ReelViewHolder", "Current video ID: " + currentVideoId);
            
            // Clear any previous player attachment
            playerView.setPlayer(null);
            
            // Reset progress line and set pivot point
            progressLine.setScaleX(0f);
            progressLine.setPivotX(0f); // Set pivot to left side for left-to-right scaling

            itemView.setTag(R.id.gameid_tag, reelItem.getGameid());

            itemView.setTag(R.id.developerid_tag, reelItem.getDeveloperId());
            // --- Firebase fetch for game description and name ---
            String gameId = reelItem.getGameid();
            if (gameId != null && !gameId.isEmpty()) {
                DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
                gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            String gameName = snapshot.child("game_name").getValue(String.class);
                            if (gameName != null && !gameName.isEmpty()) {
                                tvGameName.setText("@"+gameName);
                            } else {
                                tvGameName.setText("");
                            }
                        } else {

                            tvGameName.setText("");
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {

                        tvGameName.setText("");
                    }
                });
            } else {
                tvGameName.setText("");
            }
        }
        
        private void loadProfileImage(String userId) {
            if (userId == null || userId.isEmpty()) {
                // Set default image if userId is null or empty
                profile_image.setImageResource(R.drawable.demo_user);
                return;
            }
            
            // Reference to the user's data in Firebase
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
            
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        String profilePhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                        
                        if (profilePhotoUrl != null && !profilePhotoUrl.isEmpty()) {
                            // Load the profile image using Glide
                            Glide.with(context)
                                    .load(profilePhotoUrl)
                                    .placeholder(R.drawable.demo_user)
                                    .error(R.drawable.demo_user)
                                    .into(profile_image);
                        } else {
                            // Set default image if profile_photo_url is null or empty
                            profile_image.setImageResource(R.drawable.demo_user);
                        }
                    } else {
                        // Set default image if user doesn't exist
                        profile_image.setImageResource(R.drawable.demo_user);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("ReelAdapter", "Error loading profile image: " + error.getMessage());
                    // Set default image on error
                    profile_image.setImageResource(R.drawable.demo_user);
                }
            });
        }
        
        private void handleLikeClick() {
            String videoId = reelItems.get(position).getVideoId();
            String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            
            // Prevent multiple rapid clicks
            if (likeButton.isEnabled()) {
                likeButton.setEnabled(false);
                
                // Optimistic update - update UI immediately
                boolean newLikeState = !isLiked;
                int currentCount = Integer.parseInt(reelItems.get(position).getLikeCount());
                int newCount = newLikeState ? currentCount + 1 : Math.max(0, currentCount - 1);
                
                updateLikeUI(newLikeState, newCount);
                
                // Perform Firebase operations
                if (newLikeState) {
                    likeVideoOptimistic(videoId, currentUserId, currentCount);
                } else {
                    unlikeVideoOptimistic(videoId, currentUserId, currentCount);
                }
            }
        }
        
        private void likeVideoOptimistic(String videoId, String userId, int currentCount) {
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
                        
                        // Update the ReelItem data
                        reelItems.get(position).setLikeCount(String.valueOf(currentCount + 1));
                    } else {
                        // Rollback UI on failure
                        updateLikeUI(false, currentCount);
                    }
                    likeButton.setEnabled(true);
                }
            });
        }
        
        private void unlikeVideoOptimistic(String videoId, String userId, int currentCount) {
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
                        
                        // Update the ReelItem data
                        reelItems.get(position).setLikeCount(String.valueOf(Math.max(0, currentCount - 1)));
                    } else {
                        // Rollback UI on failure
                        updateLikeUI(true, currentCount);
                    }
                    likeButton.setEnabled(true);
                }
            });
        }
        
        private void updateLikeUI(boolean liked, int likeCount) {
            isLiked = liked;
            
            // Update like icon color
            if (liked) {
                likeIcon.setImageResource(R.drawable.ic_heart_filled);
                likeIcon.setColorFilter(android.graphics.Color.RED);
            } else {
                likeIcon.setImageResource(R.drawable.ic_heart);
                likeIcon.setColorFilter(android.graphics.Color.WHITE);
            }
            
            // Update like count text
            tvLikes.setText(likeCount + " likes");
        }
        
        private void checkIfLiked(String videoId) {
            String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            
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
                    updateLikeUI(liked, Integer.parseInt(reelItems.get(position).getLikeCount()));
                }
                
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    // On error, assume not liked
                    updateLikeUI(false, Integer.parseInt(reelItems.get(position).getLikeCount()));
                }
            });
        }
        
        private void handleFollowClick() {
            String currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
            String developerId = reelItems.get(position).getDeveloperId();
            
            // Prevent following yourself
            if (currentUserId.equals(developerId)) {
                Toast.makeText(context, "You cannot follow yourself", Toast.LENGTH_SHORT).show();
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
                        
                        Log.d("FollowDebug", "Storing follow relationship: users/" + currentUserId + "/following_list/" + developerId + " = true");
                        
                        currentUserFollowingRef.setValue(true).addOnSuccessListener(aVoid -> {
                            Log.d("FollowDebug", "Successfully stored follow relationship");
                            // Update all ViewHolders for this developer
                            updateAllViewHoldersForDeveloper(developerId, true);
                        }).addOnFailureListener(e -> {
                            Log.d("FollowDebug", "Failed to store follow relationship: " + e.getMessage());
                            // Rollback UI on failure
                            updateAllViewHoldersForDeveloper(developerId, false);
                        });
                        
                        Toast.makeText(context, "Following", Toast.LENGTH_SHORT).show();
                    } else {
                        // Rollback UI on failure
                        Log.d("FollowDebug", "Transaction failed: " + (error != null ? error.getMessage() : "Unknown error"));
                        updateAllViewHoldersForDeveloper(developerId, false);
                        Toast.makeText(context, "Failed to follow", Toast.LENGTH_SHORT).show();
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
                        
                        Log.d("FollowDebug", "Removing follow relationship: users/" + currentUserId + "/following_list/" + developerId);
                        
                        currentUserFollowingRef.removeValue().addOnSuccessListener(aVoid -> {
                            Log.d("FollowDebug", "Successfully removed follow relationship");
                            // Update all ViewHolders for this developer
                            updateAllViewHoldersForDeveloper(developerId, false);
                        }).addOnFailureListener(e -> {
                            Log.d("FollowDebug", "Failed to remove follow relationship: " + e.getMessage());
                            // Rollback UI on failure
                            updateAllViewHoldersForDeveloper(developerId, true);
                        });
                        
                        Toast.makeText(context, "Unfollowed", Toast.LENGTH_SHORT).show();
                    } else {
                        // Rollback UI on failure
                        Log.d("FollowDebug", "Unfollow transaction failed: " + (error != null ? error.getMessage() : "Unknown error"));
                        updateAllViewHoldersForDeveloper(developerId, true);
                        Toast.makeText(context, "Failed to unfollow", Toast.LENGTH_SHORT).show();
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
            String videoId = reelItems.get(position).getVideoId();
            String videoTitle = reelItems.get(position).getTitle();
            
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
                context.startActivity(Intent.createChooser(shareIntent, "Share via"));
            } catch (Exception e) {
                // Handle case where no sharing app is available
                Toast.makeText(context, "No sharing app available", Toast.LENGTH_SHORT).show();
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

        void startProgressUpdates() {
            stopProgressUpdates();
            progressHandler = new android.os.Handler();
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (sharedPlayer != null && sharedPlayer.getDuration() > 0) {
                        float progress = (float) sharedPlayer.getCurrentPosition() / sharedPlayer.getDuration();
                        progressLine.setScaleX(progress);
                        
                        // Check for view count increment every 500ms
                        if (currentVideoId != null && sharedPlayer.getCurrentPosition() % 500 < 100) {
                            ViewCountManager.checkAndIncrementViewCount(
                                currentVideoId,
                                sharedPlayer.getCurrentPosition(),
                                sharedPlayer.getDuration()
                            );
                        }
                    }
                    if (progressHandler != null) {
                        progressHandler.postDelayed(this, 100); // Update every 100ms
                    }
                }
            };
            progressHandler.post(progressRunnable);
        }

        void stopProgressUpdates() {
            if (progressHandler != null) {
                progressHandler.removeCallbacksAndMessages(null);
                progressHandler = null;
            }
            progressRunnable = null;
        }

        class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // Toggle mute/unmute on single tap
                if (currentPlayingPosition == position) {
                    if (sharedPlayer != null) {
                        float currentVolume = sharedPlayer.getVolume();
                        if (currentVolume > 0f) {
                            sharedPlayer.setVolume(0f); // Mute
                        } else {
                            sharedPlayer.setVolume(1f); // Unmute
                        }
                    }
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                String gameid = (String) itemView.getTag(R.id.gameid_tag);
                
                // Pause current video before launching activity
                pauseCurrentVideo();
                
                // Launch Game_mode activity with only game_id
                if (gameid != null && !gameid.isEmpty()) {
                    Intent intent = new Intent(context, Game_mode.class);
                    intent.putExtra("game_id", gameid);
                    context.startActivity(intent);
                } else {
                    Log.e("ReelAdapter", "Game ID is null or empty");
                    Toast.makeText(context, "Game information not found", Toast.LENGTH_SHORT).show();
                }
                
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // Handle hold to pause
                if (!isHolding && e1 != null) {
                    long pressDuration = e2.getEventTime() - e1.getDownTime();
                    if (pressDuration > 200) { // 200ms hold threshold
                        isHolding = true;
                        if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.isPlaying()) {
                            sharedPlayer.setPlayWhenReady(false);
                            isPausedByHold = true;
                        }
                    }
                }
                return false;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                // Alternative long press detection
                isHolding = true;
                if (currentPlayingPosition == position && sharedPlayer != null && sharedPlayer.isPlaying()) {
                    sharedPlayer.setPlayWhenReady(false);
                    isPausedByHold = true;
                }
            }
        }

        // Method to handle touch up (release)
        public void onTouchUp() {
            if (isHolding) {
                isHolding = false;
                if (isPausedByHold && sharedPlayer != null) {
                    sharedPlayer.setPlayWhenReady(true);
                    isPausedByHold = false;
                }
            }
        }
        
        private void showVideoDetailsBottomSheet() {
            if (currentVideoId == null) {
                android.util.Log.w("ReelViewHolder", "Cannot show bottom sheet - no video ID");
                return;
            }
            
            // Get the actual description from the ReelItem instead of TextView
            String actualDescription = "";
            if (position >= 0 && position < reelItems.size()) {
                actualDescription = reelItems.get(position).getDescription();
                android.util.Log.d("VideoDetailsBottomSheet", "Passing description: " + actualDescription);
            }
            
            VideoDetailsBottomSheet bottomSheet = VideoDetailsBottomSheet.newInstance(
                currentVideoId,
                "", // Title will be fetched from Firebase
                actualDescription
            );
            
            // Get the activity context
            Context context = itemView.getContext();
            if (context instanceof androidx.fragment.app.FragmentActivity) {
                androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
                bottomSheet.show(activity.getSupportFragmentManager(), "VideoDetailsBottomSheet");
            } else {
                android.util.Log.w("ReelViewHolder", "Context is not a FragmentActivity");
            }
        }
    }
}

