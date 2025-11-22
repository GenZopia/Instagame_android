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
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
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
    private ExoPlayer sharedPlayer;
    private ReelViewHolder currentPlayingViewHolder = null;
    private int currentPlayingPosition = -1;
    private boolean isPausedByHold = false;
    
    // Video preload manager for Instagram-like smooth transitions
    private VideoPreloadManager videoPreloadManager;

    // CRITICAL: Playback position cache - preserves video progress for backward scroll
    // Maps videoId -> playback position in milliseconds
    private java.util.Map<String, Long> playbackPositionCache = new java.util.concurrent.ConcurrentHashMap<>();

    // Track which videos are currently playing or paused (not reloading)
    private java.util.Set<String> activeVideoIds = java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<>());

    // Follow state management
    private java.util.Map<String, Boolean> followStates = new java.util.concurrent.ConcurrentHashMap<>();
    private java.util.Map<String, java.util.List<ReelViewHolder>> developerViewHolders = new java.util.concurrent.ConcurrentHashMap<>();

    public ReelAdapter(Context context, List<ReelItem> reelItems, RecyclerView recyclerView) {
        this.context = context;
        this.reelItems = reelItems;
        this.recyclerView = recyclerView;
        initializePlayer();
        initializeVideoPreloadManager();
    }

    private void initializePlayer() {
        sharedPlayer = new ExoPlayer.Builder(context).build();
        sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        sharedPlayer.setPlayWhenReady(false);
    }

    private void initializeVideoPreloadManager() {
        videoPreloadManager = new VideoPreloadManager(context);
        videoPreloadManager.setReelItems(reelItems);
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

    /**
     * Play video at given position with intelligent preload detection.
     * Production-ready: Handles forward/backward scroll, race conditions, and fallback loading.
     */
    public void playVideoAtPosition(int position) {
        if (position < 0 || position >= reelItems.size()) {
            Log.w("ReelAdapter", "Invalid position: " + position);
            return;
        }

        // Skip if already playing this position
        if (currentPlayingPosition == position) {
            Log.d("ReelAdapter", "Already playing position: " + position);
            return;
        }

        // Pause any currently playing video
        pauseCurrentVideo();

        // Get the ViewHolder for this position
        ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            Log.w("ReelAdapter", "ViewHolder not found for position: " + position);
            return;
        }

        ReelItem item = reelItems.get(position);
        String videoUri = item.getVideoUrl() != null ? item.getVideoUrl() : item.getVideoId();

        // Handle unavailable videos
        if (videoUri == null || videoUri.isEmpty() || videoUri.equals(item.getVideoId())) {
            holder.playerView.setVisibility(View.INVISIBLE);
            holder.tvTitle.setText("Video unavailable");
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            isPausedByHold = false;
            Log.w("ReelAdapter", "Video unavailable at position: " + position);
            return;
        }

        holder.playerView.setVisibility(View.VISIBLE);
        Log.d("ReelAdapter", "Starting playback at position: " + position + ", videoId: " + item.getVideoId());

        // Step 1: Try to use preloaded player (with validation)
        ExoPlayer preloadedPlayer = videoPreloadManager.getPreloadedPlayer(item.getVideoId());

        if (preloadedPlayer != null && isPlayerReadyForPlayback(preloadedPlayer)) {
            Log.d("ReelAdapter", "✓ Using PRELOADED & READY player for: " + item.getVideoId());
            playWithPreloadedPlayer(holder, preloadedPlayer, position, item);
            return;
        }

        // Step 2: Fallback - Load on demand with proper error handling
        if (preloadedPlayer != null) {
            Log.w("ReelAdapter", "⚠ Preloaded player not ready yet, using fallback for: " + item.getVideoId());
        } else {
            Log.w("ReelAdapter", "⚠ No preloaded player found, loading on demand: " + item.getVideoId());
        }

        playWithOnDemandLoading(holder, videoUri, position, item);
    }

    /**
     * Check if player is truly ready for immediate playback
     */
    private boolean isPlayerReadyForPlayback(ExoPlayer player) {
        try {
            // Player must be in STATE_READY and have valid duration
            return player != null
                    && player.getPlaybackState() == Player.STATE_READY
                    && player.getDuration() > 0;
        } catch (Exception e) {
            Log.e("ReelAdapter", "Error checking player state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Play video using preloaded player (fast path)
     * Production-ready: Preserves playback position for backward scroll
     */
    private void playWithPreloadedPlayer(ReelViewHolder holder, ExoPlayer preloadedPlayer,
                                        int position, ReelItem item) {
        try {
            String videoId = item.getVideoId();

            // Save current playback position before switching
            if (sharedPlayer != null && currentPlayingPosition >= 0 && currentPlayingPosition < reelItems.size()) {
                try {
                    ReelItem currentItem = reelItems.get(currentPlayingPosition);
                    if (currentItem != null && currentItem.getVideoId() != null) {
                        long currentPosition = sharedPlayer.getCurrentPosition();
                        playbackPositionCache.put(currentItem.getVideoId(), currentPosition);
                        Log.d("ReelAdapter", "Saved playback position for " + currentItem.getVideoId() +
                              ": " + currentPosition + "ms");
                    }
                } catch (Exception e) {
                    Log.e("ReelAdapter", "Error saving playback position: " + e.getMessage());
                }
            }

            // Release old shared player if it's different
            if (sharedPlayer != null && sharedPlayer != preloadedPlayer) {
                try {
                    sharedPlayer.release();
                    Log.d("ReelAdapter", "Released old shared player");
                } catch (Exception e) {
                    Log.e("ReelAdapter", "Error releasing old player: " + e.getMessage());
                }
            }

            // Use preloaded player as new shared player
            sharedPlayer = preloadedPlayer;

            // Attach player to view
            holder.playerView.setPlayer(sharedPlayer);

            // CRITICAL FIX: Restore playback position if video was previously played
            Long savedPosition = playbackPositionCache.get(videoId);
            if (savedPosition != null && savedPosition > 0) {
                // Video was previously played - resume from saved position
                sharedPlayer.seekTo(savedPosition);
                activeVideoIds.add(videoId);
                Log.d("ReelAdapter", "✓ Resuming video " + videoId +
                      " from saved position: " + savedPosition + "ms (backward scroll)");
            } else {
                // First time playing this video - start from beginning
                sharedPlayer.seekTo(0);
                activeVideoIds.add(videoId);
                Log.d("ReelAdapter", "✓ Starting video " + videoId + " from beginning (new play)");
            }

            // Start playback
            sharedPlayer.setPlayWhenReady(true);

            // Update tracking
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            isPausedByHold = false;

            // Start UI progress updates
            holder.startProgressUpdates();

            // Add listener for tracking
            attachPlayerListener(item);

            Log.d("ReelAdapter", "✓ Playing with preloaded player at position: " + position);

        } catch (Exception e) {
            Log.e("ReelAdapter", "Error playing with preloaded player: " + e.getMessage(), e);
            // Fallback to on-demand if error occurs
            playWithOnDemandLoading(holder, item.getVideoUrl(), position, item);
        }
    }

    /**
     * Play video by loading on demand (fallback path)
     * Production-ready: Also preserves playback position
     */
    private void playWithOnDemandLoading(ReelViewHolder holder, String videoUri,
                                        int position, ReelItem item) {
        try {
            String videoId = item.getVideoId();

            // Save current playback position before switching
            if (sharedPlayer != null && currentPlayingPosition >= 0 && currentPlayingPosition < reelItems.size()) {
                try {
                    ReelItem currentItem = reelItems.get(currentPlayingPosition);
                    if (currentItem != null && currentItem.getVideoId() != null) {
                        long currentPosition = sharedPlayer.getCurrentPosition();
                        playbackPositionCache.put(currentItem.getVideoId(), currentPosition);
                        Log.d("ReelAdapter", "Saved playback position for " + currentItem.getVideoId() +
                              ": " + currentPosition + "ms");
                    }
                } catch (Exception e) {
                    Log.e("ReelAdapter", "Error saving playback position: " + e.getMessage());
                }
            }

            // Release old player
            if (sharedPlayer != null) {
                try {
                    sharedPlayer.release();
                    Log.d("ReelAdapter", "Released old player for on-demand loading");
                } catch (Exception e) {
                    Log.e("ReelAdapter", "Error releasing old player: " + e.getMessage());
                }
            }

            // Create fresh player
            sharedPlayer = new ExoPlayer.Builder(context).build();
            sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            sharedPlayer.setPlayWhenReady(false);

            // Prepare media source
            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
            MediaItem mediaItem = MediaItem.fromUri(videoUri);

            // Try HLS first, fall back to progressive if needed
            MediaSource mediaSource = createMediaSourceForUrl(videoUri, mediaItem, dataSourceFactory);

            sharedPlayer.setMediaSource(mediaSource);
            sharedPlayer.prepare();

            // Attach to view
            holder.playerView.setPlayer(sharedPlayer);

            // CRITICAL FIX: Restore playback position if video was previously played
            Long savedPosition = playbackPositionCache.get(videoId);
            if (savedPosition != null && savedPosition > 0) {
                // Video was previously played - resume from saved position
                sharedPlayer.seekTo(savedPosition);
                activeVideoIds.add(videoId);
                Log.d("ReelAdapter", "✓ Resuming video " + videoId +
                      " from saved position: " + savedPosition + "ms (on-demand, backward scroll)");
            } else {
                // First time playing this video - start from beginning
                sharedPlayer.seekTo(0);
                activeVideoIds.add(videoId);
                Log.d("ReelAdapter", "✓ Starting video " + videoId + " from beginning (on-demand, new play)");
            }

            // Update tracking
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            isPausedByHold = false;

            // Start UI progress updates
            holder.startProgressUpdates();

            // Add listener with error handling and retry logic
            attachPlayerListenerWithErrorHandling(item, videoUri);

            // Set to play (will start when STATE_READY)
            sharedPlayer.setPlayWhenReady(true);

            Log.d("ReelAdapter", "Started on-demand loading at position: " + position);

        } catch (Exception e) {
            Log.e("ReelAdapter", "Critical error in on-demand loading: " + e.getMessage(), e);
            // Mark video as unavailable
            holder.playerView.setVisibility(View.INVISIBLE);
            holder.tvTitle.setText("Video unavailable");
        }
    }

    /**
     * Create appropriate media source based on URL format
     */
    private MediaSource createMediaSourceForUrl(String videoUri, MediaItem mediaItem,
                                               DefaultDataSourceFactory dataSourceFactory) {
        try {
            String urlLower = videoUri.toLowerCase();

            // HLS format
            if (urlLower.contains(".m3u8") || urlLower.contains("hls")) {
                Log.d("ReelAdapter", "Creating HLS media source");
                return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
            }

            // Progressive format (MP4, etc)
            Log.d("ReelAdapter", "Creating progressive media source");
            return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);

        } catch (Exception e) {
            Log.e("ReelAdapter", "Error creating media source: " + e.getMessage());
            // Default to progressive
            return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        }
    }

    /**
     * Attach standard listener for playback tracking
     */
    private void attachPlayerListener(ReelItem item) {
        try {
            sharedPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                            try {
                                ViewCountManager.setVideoDuration(item.getVideoId(), sharedPlayer.getDuration());
                            } catch (Exception e) {
                                Log.e("ReelAdapter", "Error setting video duration: " + e.getMessage());
                            }
                        }
                    }
                }

                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                    if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                        try {
                            ViewCountManager.checkAndIncrementViewCount(
                                    item.getVideoId(),
                                    sharedPlayer.getCurrentPosition(),
                                    sharedPlayer.getDuration()
                            );
                        } catch (Exception e) {
                            Log.e("ReelAdapter", "Error checking view count: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e("ReelAdapter", "Error attaching player listener: " + e.getMessage());
        }
    }

    /**
     * Attach listener with error handling and fallback retry
     */
    private void attachPlayerListenerWithErrorHandling(ReelItem item, String videoUri) {
        try {
            sharedPlayer.addListener(new Player.Listener() {
                private boolean triedFallback = false;

                @Override
                public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                    Log.e("ReelAdapter", "Player error: " + error.getMessage());

                    if (!triedFallback) {
                        triedFallback = true;
                        try {
                            Log.w("ReelAdapter", "Attempting fallback to progressive format");
                            DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
                            MediaItem mediaItem = MediaItem.fromUri(videoUri);
                            MediaSource fallbackSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                                    .createMediaSource(mediaItem);
                            sharedPlayer.setMediaSource(fallbackSource);
                            sharedPlayer.prepare();
                            sharedPlayer.setPlayWhenReady(true);
                            Log.d("ReelAdapter", "Fallback initiated");
                        } catch (Exception e) {
                            Log.e("ReelAdapter", "Fallback also failed: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY) {
                        if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                            try {
                                ViewCountManager.setVideoDuration(item.getVideoId(), sharedPlayer.getDuration());
                            } catch (Exception e) {
                                Log.e("ReelAdapter", "Error setting video duration: " + e.getMessage());
                            }
                        }
                    }
                }

                @Override
                public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
                    if (item.getVideoId() != null && sharedPlayer.getDuration() > 0) {
                        try {
                            ViewCountManager.checkAndIncrementViewCount(
                                    item.getVideoId(),
                                    sharedPlayer.getCurrentPosition(),
                                    sharedPlayer.getDuration()
                            );
                        } catch (Exception e) {
                            Log.e("ReelAdapter", "Error checking view count: " + e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e("ReelAdapter", "Error attaching error-handling listener: " + e.getMessage());
        }
    }

    private void pauseCurrentVideo() {
        // Save position before pausing
        if (sharedPlayer != null && currentPlayingPosition >= 0 && currentPlayingPosition < reelItems.size()) {
            try {
                ReelItem item = reelItems.get(currentPlayingPosition);
                if (item != null && item.getVideoId() != null) {
                    long currentPosition = sharedPlayer.getCurrentPosition();
                    playbackPositionCache.put(item.getVideoId(), currentPosition);
                    Log.d("ReelAdapter", "Saved playback position on pause for " + item.getVideoId() +
                          ": " + currentPosition + "ms");
                }
            } catch (Exception e) {
                Log.e("ReelAdapter", "Error saving position on pause: " + e.getMessage());
            }
        }

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

    /**
     * Ensures only the first visible video plays, while pausing all others.
     * This is the primary entry point for video playback synchronization.
     *
     * @see #pauseOtherVisibleVideos(int)
     */
    public void ensureOnlyCurrentVideoPlays() {
        if (recyclerView.getLayoutManager() == null) {
            Log.w("ReelAdapter", "LayoutManager is null in ensureOnlyCurrentVideoPlays");
            return;
        }

        int firstVisible = ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager())
                .findFirstVisibleItemPosition();

        if (firstVisible < 0 || firstVisible >= getItemCount()) {
            Log.w("ReelAdapter", "Invalid firstVisible position: " + firstVisible);
            return;
        }

        // Only proceed if we're switching to a different video
        if (currentPlayingPosition == firstVisible) {
            Log.d("ReelAdapter", "Already playing video at position " + firstVisible);
            return;
        }

        Log.d("ReelAdapter", "ensureOnlyCurrentVideoPlays: Switching from position " +
              currentPlayingPosition + " to position " + firstVisible);

        // Step 1: Pause all other visible videos first (BEFORE playing the new one)
        pauseOtherVisibleVideos(firstVisible);

        // Step 2: Play the video at the first visible position
        playVideoAtPosition(firstVisible);
    }

    /**
     * Pauses and detaches players for all visible ViewHolders except the one at keepPosition.
     * This ensures clean separation between videos - no two videos should have attached players.
     *
     * @param keepPosition The adapter position of the video to keep playing
     */
    private void pauseOtherVisibleVideos(int keepPosition) {
        if (recyclerView.getLayoutManager() == null) {
            Log.w("ReelAdapter", "LayoutManager is null in pauseOtherVisibleVideos");
            return;
        }

        androidx.recyclerview.widget.LinearLayoutManager layoutManager =
                (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();

        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();

        if (firstVisible < 0 || lastVisible < 0) {
            Log.w("ReelAdapter", "Invalid visible range: first=" + firstVisible + ", last=" + lastVisible);
            return;
        }

        Log.d("ReelAdapter", "pauseOtherVisibleVideos: Pausing all except position " + keepPosition +
              " (visible range: " + firstVisible + " to " + lastVisible + ")");

        for (int pos = firstVisible; pos <= lastVisible; pos++) {
            if (pos < 0 || pos >= getItemCount()) {
                continue;
            }

            // Skip the position we want to keep playing
            if (pos == keepPosition) {
                Log.d("ReelAdapter", "Skipping position " + pos + " (target video)");
                continue;
            }

            RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(pos);
            if (viewHolder instanceof ReelViewHolder) {
                ReelViewHolder reelViewHolder = (ReelViewHolder) viewHolder;
                Log.d("ReelAdapter", "Pausing video at position " + pos);

                try {
                    // Stop progress updates to prevent unnecessary UI updates
                    reelViewHolder.stopProgressUpdates();

                    // If this ViewHolder has a player attached, pause and detach it
                    if (reelViewHolder.playerView != null) {
                        Player player = reelViewHolder.playerView.getPlayer();
                        if (player != null) {
                            try {
                                player.setPlayWhenReady(false);
                                Log.d("ReelAdapter", "Paused player at position " + pos);
                            } catch (Exception e) {
                                Log.e("ReelAdapter", "Error pausing player at position " + pos, e);
                            }
                        }

                        // Detach the player from the PlayerView to prevent multiple attachments
                        // This is crucial for clean playback transitions
                        try {
                            reelViewHolder.playerView.setPlayer(null);
                            Log.d("ReelAdapter", "Detached player from PlayerView at position " + pos);
                        } catch (Exception e) {
                            Log.e("ReelAdapter", "Error detaching player at position " + pos, e);
                        }
                    }
                } catch (Exception e) {
                    Log.e("ReelAdapter", "Error pausing video at position " + pos, e);
                }
            }
        }

        Log.d("ReelAdapter", "pauseOtherVisibleVideos: Complete");
    }

    public void releaseAllPlayers() {
        // Save current playback position before release
        saveCurrentPlaybackPosition();

        pauseCurrentVideo();
        if (sharedPlayer != null) {
            sharedPlayer.release();
            sharedPlayer = null;
        }
        
        // Release video preload manager
        if (videoPreloadManager != null) {
            videoPreloadManager.shutdown();
            videoPreloadManager = null;
        }

        // Clear follow state cache
        followStates.clear();
        developerViewHolders.clear();

        // DO NOT clear playback position cache - preserve it across sessions
        Log.d("ReelAdapter", "Playback position cache size: " + playbackPositionCache.size());
    }
    
    /**
     * Update the preload manager with the current scroll position
     */
    public void updatePreloadManagerPosition(int position) {
        if (videoPreloadManager != null && position >= 0 && position < reelItems.size()) {
            videoPreloadManager.updateCurrentPosition(position);
        }
    }

    /**
     * Save current playback position before switching videos
     * Production-ready: Ensures position is preserved
     */
    private void saveCurrentPlaybackPosition() {
        if (sharedPlayer != null && currentPlayingPosition >= 0 && currentPlayingPosition < reelItems.size()) {
            try {
                ReelItem item = reelItems.get(currentPlayingPosition);
                if (item != null && item.getVideoId() != null) {
                    long currentPosition = sharedPlayer.getCurrentPosition();
                    if (currentPosition > 0) {
                        playbackPositionCache.put(item.getVideoId(), currentPosition);
                        Log.d("ReelAdapter", "Saved final playback position for " + item.getVideoId() +
                              ": " + currentPosition + "ms");
                    }
                }
            } catch (Exception e) {
                Log.e("ReelAdapter", "Error saving playback position on release: " + e.getMessage());
            }
        }
    }

    /**
     * Clear playback position cache - useful when user wants fresh start
     */
    public void clearPlaybackPositionCache() {
        playbackPositionCache.clear();
        activeVideoIds.clear();
        Log.d("ReelAdapter", "Cleared playback position cache");
    }

    /**
     * Get cached playback position for a video
     */
    public Long getPlaybackPosition(String videoId) {
        return playbackPositionCache.get(videoId);
    }

    /**
     * Remove position cache for specific video
     */
    public void clearPlaybackPosition(String videoId) {
        playbackPositionCache.remove(videoId);
        activeVideoIds.remove(videoId);
        Log.d("ReelAdapter", "Cleared playback position for: " + videoId);
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

    /**
     * Handle scroll state changes - optimized for both forward and backward scrolling.
     * Production-ready: Ensures preload happens before playback, works reliably in both directions.
     */
    public void handleScrollStateChange(int newState) {
        if (recyclerView.getLayoutManager() == null) {
            Log.w("ReelAdapter", "LayoutManager is null in handleScrollStateChange");
            return;
        }

        androidx.recyclerview.widget.LinearLayoutManager layoutManager =
                (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();

        int firstVisible = layoutManager.findFirstVisibleItemPosition();

        if (firstVisible < 0 || firstVisible >= getItemCount()) {
            Log.w("ReelAdapter", "Invalid firstVisible position: " + firstVisible);
            return;
        }

        String scrollDirection = "UNKNOWN";

        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            // Scrolling has stopped - time to play the video
            determineScrollDirection(firstVisible);
            scrollDirection = currentPlayingPosition < firstVisible ? "FORWARD" : "BACKWARD";

            Log.d("ReelAdapter", "SCROLL_STATE_IDLE: Direction=" + scrollDirection +
                  ", currentPlaying=" + currentPlayingPosition + ", firstVisible=" + firstVisible);

            // Step 1: Update preload manager position (triggers background preload)
            updatePreloadManagerPosition(firstVisible);

            // Step 2: WAIT briefly for preload to buffer the video
            // This is critical for smooth playback, especially on backward scroll
            new android.os.Handler().postDelayed(() -> {
                if (currentPlayingPosition != firstVisible) {
                    Log.d("ReelAdapter", "After preload delay: Playing video at position " + firstVisible);
                    playVideoAtPosition(firstVisible);
                } else {
                    Log.d("ReelAdapter", "Already playing position: " + firstVisible);
                }
            }, 300); // 300ms delay for preload to start buffering

        } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
            // User is dragging - update preload in background but don't play yet
            Log.d("ReelAdapter", "SCROLL_STATE_DRAGGING: firstVisible=" + firstVisible);
            updatePreloadManagerPosition(firstVisible);

        } else if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
            // Snap-settling state - just log for debugging
            Log.d("ReelAdapter", "SCROLL_STATE_SETTLING: firstVisible=" + firstVisible);
        }
    }

    /**
     * Determine scroll direction to help debug scroll issues
     */
    private void determineScrollDirection(int newPosition) {
        if (currentPlayingPosition >= 0) {
            if (newPosition > currentPlayingPosition) {
                Log.d("ReelAdapter", "Scroll direction: FORWARD (from " + currentPlayingPosition + " to " + newPosition + ")");
            } else if (newPosition < currentPlayingPosition) {
                Log.d("ReelAdapter", "Scroll direction: BACKWARD (from " + currentPlayingPosition + " to " + newPosition + ")");
            }
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
        ImageView commentsButton;
        
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
            commentsButton = itemView.findViewById(R.id.comments_button);
            
            // Set up three-dot menu click listener
            threeDotMenu.setOnClickListener(v -> {
                android.util.Log.d("ReelViewHolder", "Three-dot menu clicked for video: " + currentVideoId);
                showVideoDetailsBottomSheet();
            });

            if (commentsButton != null) {
                commentsButton.setOnClickListener(v -> {
                    if (currentVideoId == null) return;
                    Context context = itemView.getContext();
                    if (context instanceof androidx.fragment.app.FragmentActivity) {
                        androidx.fragment.app.FragmentActivity activity = (androidx.fragment.app.FragmentActivity) context;
                        com.genzopia.Instagame.comments.ui.CommentsBottomSheetFragment
                                .newInstance(currentVideoId)
                                .show(activity.getSupportFragmentManager(), "Comments");
                    }
                });
            }
            
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

