// HomeAdapter.java
package com.genzopia.Instagame.vertical_recylerview_custom;

import android.content.Context;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.GestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.ImageItem;

import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.StoryProfileAdapter;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.StoryGridLayoutManager;
import com.genzopia.Instagame.vertical_recylerview_custom.VideoViewHolder;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;
import com.genzopia.Instagame.vertical_recylerview_custom.PlayerManager;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.source.MediaSource;

import androidx.media3.common.Player;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 2;
    private static final int TYPE_PROFILE = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_SKELETON_HEADER = -2;
    private static final int TYPE_SKELETON_FEED = -1;
    private static final String TAG = "HomeAdapter";
    public RecyclerView recyclerView;

    private List<Object> items = new ArrayList<>();
    private List<ImageItem> profileItems = new ArrayList<>();
    private List<VideoItem> videoItems = new ArrayList<>();
    private Context context;
    private View.OnTouchListener globalTouchListener;
    private String currentlyPlayingVideoId = null;
    public boolean isLoading = false;
    private int skeletonCount = 5;
    private int skeletonFeedCount = 5;
    private ExoPlayer exoPlayer;
    
    // Shared player for video playback
    private ExoPlayer sharedPlayer;
    private VideoViewHolder currentPlayingViewHolder = null;
    public int currentPlayingPosition = -1;

    public HomeAdapter(Context context, List<ImageItem> profileItems, List<VideoItem> videoItems) {
        this.context = context;
        this.profileItems = profileItems;
        this.videoItems = videoItems;
        // Add header as first item (null placeholder)
        items.add(null); // Header placeholder
        items.add(profileItems);
        items.addAll(videoItems);
        
        // Initialize shared player
        initializeSharedPlayer();
    }
    
    private void initializeSharedPlayer() {
        if (sharedPlayer == null) {
            android.util.Log.d("HomeAdapter", "Initializing shared player");
            sharedPlayer = new ExoPlayer.Builder(context).build();
            sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            sharedPlayer.setPlayWhenReady(false);
            
            // Add error listener
            sharedPlayer.addListener(new Player.Listener() {

                
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    // If player is in IDLE state for too long, force reset
                    if (playbackState == Player.STATE_IDLE) {
                        mainHandler.postDelayed(() -> {
                            if (sharedPlayer != null && sharedPlayer.getPlaybackState() == Player.STATE_IDLE) {
                                android.util.Log.d("HomeAdapter", "Player stuck in IDLE state, forcing reset");
                                forceCompleteReset();
                            }
                        }, 1000); // Reduced to 1 second delay
                    }
                }
            });
        }
    }
    
    public void checkForBlackScreenIssue() {
        // Check if current playing view holder is visible and has a valid player
        if (currentPlayingViewHolder != null && sharedPlayer != null) {
            try {
                // Check if the view is still attached and visible
                if (!currentPlayingViewHolder.itemView.isAttachedToWindow() || 
                    currentPlayingViewHolder.itemView.getVisibility() != View.VISIBLE) {
                    android.util.Log.d("HomeAdapter", "Current playing view not visible, forcing reset");
                    forceCompleteReset();
                    return;
                }
                
                // Check if player is in a valid state
                int state = sharedPlayer.getPlaybackState();
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    android.util.Log.d("HomeAdapter", "Player in invalid state: " + state + ", forcing reset");
                    forceCompleteReset();
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error checking for black screen issue: " + e.getMessage());
                forceCompleteReset();
            }
        }
    }
    
    private void resetSharedPlayer() {
        android.util.Log.d("HomeAdapter", "Resetting shared player");
        if (sharedPlayer != null) {
            try {
                sharedPlayer.stop();
                sharedPlayer.clearMediaItems();
                sharedPlayer.setPlayWhenReady(false);
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error resetting shared player: " + e.getMessage());
                // Release and recreate if there's an error
                sharedPlayer.release();
                sharedPlayer = null;
                initializeSharedPlayer();
            }
        }
    }

    public void setGlobalTouchListener(View.OnTouchListener listener) {
        this.globalTouchListener = listener;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
        notifyDataSetChanged();
    }

    public void updateData(List<ImageItem> profileItems, List<VideoItem> videoItems) {
        this.profileItems = profileItems;
        this.videoItems = videoItems;
        items.clear();
        items.add(null); // Header placeholder
        items.add(profileItems);
        items.addAll(videoItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_SKELETON_HEADER) {
            View view = inflater.inflate(R.layout.item_home_skeleton_header, parent, false);
            return new SkeletonHeaderViewHolder(view);
        } else if (viewType == TYPE_SKELETON_FEED) {
            View view = inflater.inflate(R.layout.item_home_skeleton_feed, parent, false);
            return new SkeletonFeedViewHolder(view);
        } else if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_PROFILE) {
            View view = inflater.inflate(R.layout.item_profile_container, parent, false);
            return new ProfileViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_video, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (isLoading) {
            // No binding needed for skeletons
            return;
        }
        if (holder.getItemViewType() == TYPE_HEADER) {
            // No binding needed for header
            return;
        } else if (holder.getItemViewType() == TYPE_PROFILE) {
            // Pass the entire profileItems list to the ProfileViewHolder
            ((ProfileViewHolder) holder).bind(profileItems);
        } else {
            VideoItem videoItem = (VideoItem) items.get(position);
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            videoHolder.bind(videoItem, context);
            
            // Don't play video immediately during binding - let the scroll listener handle it
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof VideoViewHolder) {
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            
            android.util.Log.d("HomeAdapter", "Recycling view holder at position: " + holder.getAdapterPosition());
            
            // If this is the currently playing view holder, stop the video
            if (videoHolder == currentPlayingViewHolder) {
                android.util.Log.d("HomeAdapter", "Recycling currently playing view holder, pausing video");
                pauseCurrentVideo();
            }
            
            // Always detach player from recycled view
            try {
                videoHolder.playerView.setPlayer(null);
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error detaching player from recycled view: " + e.getMessage());
            }
            
            // Reset the view holder state
            videoHolder.resetPlayerView();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoading) {
            if (position == 0) {
                return TYPE_SKELETON_HEADER;
            } else {
                return TYPE_SKELETON_FEED;
            }
        }
        if (position == 0) {
            return TYPE_HEADER;
        } else if (position == 1) {
            return TYPE_PROFILE;
        } else {
            return TYPE_VIDEO;
        }
    }

    @Override
    public int getItemCount() {
        if (isLoading) {
            return 1 + skeletonFeedCount; // 1 header + N feed skeletons
        }
        return items.size();
    }

    public void playVideoAtPosition(int position) {
        if (position < 0 || position >= items.size()) {
            android.util.Log.d("HomeAdapter", "Invalid position: " + position);
            return;
        }

        // Guard: ignore tap if this position is already playing
        if (position == currentPlayingPosition) {
            android.util.Log.d("HomeAdapter", "Already playing at position: " + position);
            return;
        }
        
        // Check if we need to force a complete reset
        if (sharedPlayer != null) {
            try {
                int state = sharedPlayer.getPlaybackState();
                if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                    android.util.Log.d("HomeAdapter", "Player in bad state: " + state + ", forcing complete reset");
                    forceCompleteReset();
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error checking player state: " + e.getMessage());
                forceCompleteReset();
                return;
            }
        }
        
        // Pause current video
        pauseCurrentVideo();
        
        // Get the video item at this position
        Object item = items.get(position);
        if (!(item instanceof VideoItem)) {
            android.util.Log.d("HomeAdapter", "Item at position " + position + " is not a VideoItem");
            return;
        }
        
        VideoItem videoItem = (VideoItem) item;
        
        // Check if recyclerView is available
        if (recyclerView == null) {
            android.util.Log.d("HomeAdapter", "RecyclerView is null, cannot play video");
            return;
        }
        
        // Find the VideoViewHolder for this position
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof VideoViewHolder)) {
            android.util.Log.d("HomeAdapter", "VideoViewHolder not found for position: " + position);
            return;
        }
        
        VideoViewHolder videoHolder = (VideoViewHolder) holder;
        playVideoInViewHolder(videoHolder, videoItem, position);
    }

    @OptIn(markerClass = UnstableApi.class)
    private void playVideoInViewHolder(VideoViewHolder holder, VideoItem videoItem, int position) {
        if (videoItem.videoUrl == null || videoItem.videoUrl.isEmpty()) {
            android.util.Log.d("HomeAdapter", "No video URL for video: " + videoItem.id);
            return;
        }
        
        android.util.Log.d("HomeAdapter", "Playing video: " + videoItem.id + " at position: " + position);
        
        // Always pause current video first
        pauseCurrentVideo();
        
        // Create a fresh player for this video to avoid state corruption
        ExoPlayer freshPlayer = new ExoPlayer.Builder(context).build();
        freshPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        freshPlayer.setPlayWhenReady(false);
        
        // Set up the player with the video URL
        String videoUri = videoItem.videoUrl;
        androidx.media3.datasource.DefaultDataSource.Factory dataSourceFactory = new androidx.media3.datasource.DefaultDataSource.Factory(context);
        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        
        // Use progressive media source for MP4 files
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        
        try {
            // Prepare the fresh player
            freshPlayer.setMediaSource(mediaSource);
            freshPlayer.prepare();
            
            // Show the PlayerView and attach fresh player
            holder.showPlayerView();
            holder.playerView.setPlayer(freshPlayer);
            
            // Set up seek bar and touch controls
            holder.setupSeekBarAndTouchControls(holder.playerView, freshPlayer);
            
            // Start playback
            freshPlayer.setPlayWhenReady(true);
            
            // Update tracking variables
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            currentlyPlayingVideoId = videoItem.id;
            
            // Store the fresh player
            sharedPlayer = freshPlayer;
            
            android.util.Log.d("HomeAdapter", "Successfully started playing video with fresh player: " + videoItem.id);
        } catch (Exception e) {
            android.util.Log.e("HomeAdapter", "Error playing video: " + e.getMessage());
            // Show thumbnail as fallback
            holder.showThumbnailWhenStopped();
            // Release fresh player on error
            if (freshPlayer != null) {
                freshPlayer.release();
            }
        }
    }
    
    private void pauseCurrentVideo() {
        android.util.Log.d("HomeAdapter", "Pausing current video");
        
        // Pause and release the current player
        if (sharedPlayer != null) {
            try {
                sharedPlayer.setPlayWhenReady(false);
                sharedPlayer.release();
                android.util.Log.d("HomeAdapter", "Current player paused and released");
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error pausing/releasing player: " + e.getMessage());
            } finally {
                sharedPlayer = null;
            }
        }
        
        // Clean up the current playing view holder
        if (currentPlayingViewHolder != null) {
            try {
                // Detach player from view
                currentPlayingViewHolder.playerView.setPlayer(null);
                
                // Show thumbnail
                currentPlayingViewHolder.hideThumbnail();
                currentPlayingViewHolder.showThumbnailWhenStopped();
                
                android.util.Log.d("HomeAdapter", "Cleaned up current playing view holder");
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error cleaning up view holder: " + e.getMessage());
            } finally {
                currentPlayingViewHolder = null;
            }
        }
        
        currentPlayingPosition = -1;
        currentlyPlayingVideoId = null;
        
        android.util.Log.d("HomeAdapter", "Video pause completed");
    }
    
    public void releaseAllPlayers() {
        pauseCurrentVideo();
        if (sharedPlayer != null) {
            try {
                sharedPlayer.release();
            } catch (Exception e) {
                android.util.Log.e("HomeAdapter", "Error releasing shared player: " + e.getMessage());
            }
            sharedPlayer = null;
        }
    }
    
    public void recoverFromPlayerError() {
        android.util.Log.d("HomeAdapter", "Recovering from player error");
        pauseCurrentVideo();
        resetSharedPlayer();
        
        // Force reset all visible view holders
        resetAllVisibleViewHolders();
    }
    
    public void forceCompleteReset() {
        android.util.Log.d("HomeAdapter", "Force complete reset - all videos black");
        
        // Release all players
        releaseAllPlayers();
        
        // Reset all visible view holders
        resetAllVisibleViewHolders();
        
        // Targeted notify — avoid full rebind of every item
        notifyItemRangeChanged(0, getItemCount());
        
        android.util.Log.d("HomeAdapter", "Complete reset completed");
    }
    
    private void resetAllVisibleViewHolders() {
        if (recyclerView == null) return;
        
        android.util.Log.d("HomeAdapter", "Resetting all visible view holders");
        
        // Get all visible view holders
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            
            if (holder instanceof VideoViewHolder) {
                VideoViewHolder videoHolder = (VideoViewHolder) holder;
                try {
                    // Reset the view holder
                    videoHolder.playerView.setPlayer(null);
                    videoHolder.resetPlayerView();
                    android.util.Log.d("HomeAdapter", "Reset view holder at position: " + holder.getAdapterPosition());
                } catch (Exception e) {
                    android.util.Log.e("HomeAdapter", "Error resetting view holder: " + e.getMessage());
                }
            }
        }
    }

    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Helper method to find video position by ID
    private int findVideoPositionById(String videoId) {
        for (int i = 1; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof VideoItem) {
                VideoItem videoItem = (VideoItem) item;
                if (videoItem.id.equals(videoId)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public Object getItem(int position) {
        if (position < 0 || position >= items.size()) {
            android.util.Log.w(TAG, "getItem: Invalid position " + position + ", items size: " + items.size());
            return null;
        }
        return items.get(position);
    }

    public void setExoPlayer(ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
    }

    public void attachPlayerViewTo(int position, PlayerView playerView) {
        if (recyclerView == null) return;
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof VideoViewHolder) {
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            // Remove PlayerView from any old parent
            ViewGroup parent = (ViewGroup) playerView.getParent();
            if (parent != null) parent.removeView(playerView);
            
            // Remove only the PlayerView, keep progress container
            for (int i = videoHolder.videoContainer.getChildCount() - 1; i >= 0; i--) {
                View child = videoHolder.videoContainer.getChildAt(i);
                if (child instanceof PlayerView ||
                    child.getId() == R.id.playerView) {
                    videoHolder.videoContainer.removeViewAt(i);
                }
            }
            
            // Add PlayerView to the container
            videoHolder.videoContainer.addView(playerView);
            
            // Pass the global ExoPlayer to the ViewHolder
            videoHolder.setupSeekBarAndTouchControls(playerView, exoPlayer);
        }
    }

    public void preloadAround(int centerIndex) {
        if (items == null || items.size() <= 2) return; // Need at least header + profile + 1 video
        int start = Math.max(2, centerIndex - 5); // skip header at 0 and profile at 1
        int end = Math.min(items.size() - 1, centerIndex + 5);
        // Preload and pause all in window (no playerView logic)
        for (int i = start; i <= end; i++) {
            Object item = getItem(i);
            if (item instanceof VideoItem) {
                // Optionally, implement preloading logic here if needed
                android.util.Log.d(TAG, "Preloading video at position: " + i);
            }
        }
    }

    public List<VideoItem> getVideoItems() {
        List<VideoItem> videoItems = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof VideoItem) {
                videoItems.add((VideoItem) item);
            }
        }
        return videoItems;
    }

    public void refreshCurrentViewHolderThumbnail(int position) {
        if (recyclerView == null) return;
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof VideoViewHolder) {
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            // Force rebind to show updated thumbnail
            Object item = getItem(position);
            if (item instanceof VideoItem) {
                videoHolder.bind((VideoItem) item, recyclerView.getContext());
                android.util.Log.d("HomeAdapter", "Refreshed thumbnail for position: " + position);
            } else {
                android.util.Log.w("HomeAdapter", "Item at position " + position + " is not a VideoItem or is null");
            }
        }
    }

    public void refreshAllVisibleThumbnails() {
        if (recyclerView == null) return;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        
        for (int i = firstVisible; i <= lastVisible; i++) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
            if (holder instanceof VideoViewHolder) {
                VideoViewHolder videoHolder = (VideoViewHolder) holder;
                Object item = getItem(i);
                if (item instanceof VideoItem) {
                    videoHolder.bind((VideoItem) item, recyclerView.getContext());
                    android.util.Log.d("HomeAdapter", "Refreshed thumbnail for position: " + i);
                } else {
                    android.util.Log.w("HomeAdapter", "Item at position " + i + " is not a VideoItem or is null");
                }
            }
        }
    }
    
    public void debugVisibleThumbnails() {
        if (recyclerView == null) return;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        
        android.util.Log.d("HomeAdapter", "Debugging thumbnails for positions " + firstVisible + " to " + lastVisible);
        
        for (int i = firstVisible; i <= lastVisible; i++) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
            if (holder instanceof VideoViewHolder) {
                VideoViewHolder videoHolder = (VideoViewHolder) holder;
                videoHolder.debugThumbnailGeneration();
            }
        }
    }
    
    public void forceShowFallbackThumbnails() {
        if (recyclerView == null) return;
        
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager == null) return;
        
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        
        android.util.Log.d("HomeAdapter", "Forcing fallback thumbnails for positions " + firstVisible + " to " + lastVisible);
        
        for (int i = firstVisible; i <= lastVisible; i++) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
            if (holder instanceof VideoViewHolder) {
                VideoViewHolder videoHolder = (VideoViewHolder) holder;
                videoHolder.forceShowFallbackThumbnail();
            }
        }
    }
    
    public void testSeekBarForCurrentVideo() {
        if (currentPlayingViewHolder != null) {
            android.util.Log.d("HomeAdapter", "Testing seek bar for current playing video");
            currentPlayingViewHolder.testSeekBar();
        } else {
            android.util.Log.d("HomeAdapter", "No video currently playing");
        }
    }



    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(View itemView) {
            super(itemView);
        }
    }

    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        RecyclerView profileRecyclerView;

        ProfileViewHolder(View itemView) {
            super(itemView);
            profileRecyclerView = itemView.findViewById(R.id.profileRecyclerView);
            profileRecyclerView.setLayoutManager(new StoryGridLayoutManager(itemView.getContext()));
        }

        void bind(List<ImageItem> profileItems) {
            StoryProfileAdapter adapter = new StoryProfileAdapter(profileItems);
            profileRecyclerView.setAdapter(adapter);
        }
    }

    static class SkeletonHeaderViewHolder extends RecyclerView.ViewHolder {
        SkeletonHeaderViewHolder(View itemView) {
            super(itemView);
        }
    }
    static class SkeletonFeedViewHolder extends RecyclerView.ViewHolder {
        SkeletonFeedViewHolder(View itemView) {
            super(itemView);
        }
    }
}