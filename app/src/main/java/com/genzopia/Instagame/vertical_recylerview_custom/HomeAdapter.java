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
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ExoPlayer;
import com.genzopia.Instagame.vertical_recylerview_custom.PlayerManager;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

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
    private final PlayerManager playerManager = PlayerManager.getInstance();
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
        if (holder.getItemViewType() == TYPE_PROFILE) {
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
            // Release the video player when view is recycled
            VideoViewHolder videoHolder = (VideoViewHolder) holder;
            // Don't release the shared player here - it's managed by HomeAdapter
            videoHolder.playerView.setPlayer(null);
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
        
        // Ensure shared player is initialized
        initializeSharedPlayer();
        
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
    
    private void playVideoInViewHolder(VideoViewHolder holder, VideoItem videoItem, int position) {
        if (videoItem.videoUrl == null || videoItem.videoUrl.isEmpty()) {
            android.util.Log.d("HomeAdapter", "No video URL for video: " + videoItem.id);
            return;
        }
        
        android.util.Log.d("HomeAdapter", "Playing video: " + videoItem.id + " at position: " + position);
        
        // Check if sharedPlayer is null and reinitialize if needed
        if (sharedPlayer == null) {
            android.util.Log.d("HomeAdapter", "SharedPlayer is null, reinitializing...");
            sharedPlayer = new ExoPlayer.Builder(context).build();
            sharedPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            sharedPlayer.setPlayWhenReady(false);
        }
        
        // Set up the player with the video URL
        String videoUri = videoItem.videoUrl;
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        
        // Use progressive media source for MP4 files
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        
        try {
            sharedPlayer.setMediaSource(mediaSource);
            sharedPlayer.prepare();
            sharedPlayer.setPlayWhenReady(true);
            
            // Show the PlayerView and attach player when video starts playing
            holder.showPlayerView();
            holder.playerView.setPlayer(sharedPlayer);
            currentPlayingViewHolder = holder;
            currentPlayingPosition = position;
            currentlyPlayingVideoId = videoItem.id;
            
            android.util.Log.d("HomeAdapter", "Successfully started playing video: " + videoItem.id);
        } catch (Exception e) {
            android.util.Log.e("HomeAdapter", "Error playing video: " + e.getMessage());
        }
    }
    
    private void pauseCurrentVideo() {
        if (sharedPlayer != null) {
            sharedPlayer.setPlayWhenReady(false);
        }
        if (currentPlayingViewHolder != null) {
            // Hide PlayerView and show thumbnail when video stops playing
            currentPlayingViewHolder.playerView.setPlayer(null);
            currentPlayingViewHolder.hideThumbnail();
            currentPlayingViewHolder.showThumbnailWhenStopped();
            currentPlayingViewHolder = null;
        }
        currentPlayingPosition = -1;
        currentlyPlayingVideoId = null;
    }
    
    public void releaseAllPlayers() {
        pauseCurrentVideo();
        if (sharedPlayer != null) {
            sharedPlayer.release();
            sharedPlayer = null;
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
                if (child instanceof com.google.android.exoplayer2.ui.PlayerView || 
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
        if (items == null || items.size() <= 1) return;
        int start = Math.max(1, centerIndex - 5); // skip profile at 0
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