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

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ExoPlayer;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PROFILE = 0;
    private static final int TYPE_VIDEO = 1;
    private static final int TYPE_SKELETON_HEADER = -2;
    private static final int TYPE_SKELETON_FEED = -1;
    private static final String TAG = "HomeAdapter";
    public RecyclerView recyclerView;

    private Context context;
    private List<Object> items = new ArrayList<>();
    private final PlayerManager playerManager = PlayerManager.getInstance();
    private View.OnTouchListener globalTouchListener;
    private String currentlyPlayingVideoId = null;
    private boolean isLoading = false;
    private int skeletonCount = 5;
    private int skeletonFeedCount = 5;
    private ExoPlayer exoPlayer;

    public HomeAdapter(Context context, List<ImageItem> profileItems, List<VideoItem> videoItems) {
        this.context = context;
        items.add(profileItems);
        items.addAll(videoItems);
    }

    public void setGlobalTouchListener(View.OnTouchListener listener) {
        this.globalTouchListener = listener;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
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
            ((ProfileViewHolder) holder).bind((List<ImageItem>) items.get(position));
        } else {
            VideoItem videoItem = (VideoItem) items.get(position);
            ((VideoViewHolder) holder).bind(videoItem, context);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof VideoViewHolder) {
            // No playerView to detach from view
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

    public void releaseAllPlayers() {
        playerManager.releaseAll();
        currentlyPlayingVideoId = null;
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
            
            // Remove only the PlayerView and thumbnail, keep progress container
            for (int i = videoHolder.videoContainer.getChildCount() - 1; i >= 0; i--) {
                View child = videoHolder.videoContainer.getChildAt(i);
                if (child instanceof com.google.android.exoplayer2.ui.PlayerView || 
                    child.getId() == R.id.playerView || 
                    child.getId() == R.id.thumbnail) {
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
            Object item = items.get(i);
            if (item instanceof VideoItem) {
                // Optionally, implement preloading logic here if needed
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