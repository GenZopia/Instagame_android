// HomeAdapter.java
package com.genzopia.Instagame.vertical_recylerview_custom;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.ImageAdapter;
import com.genzopia.Instagame.vertical_recylerview_custom.profile_recyclerview.ImageItem;
import com.genzopia.Instagame.webgl_gameloading.Game_mode;

import java.util.ArrayList;
import java.util.List;

import android.os.Handler;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PROFILE = 0;
    private static final int TYPE_VIDEO = 1;
    private static final String TAG = "HomeAdapter";
    public RecyclerView recyclerView;

    private Context context;
    private List<Object> items = new ArrayList<>();
    private final PlayerManager playerManager = PlayerManager.getInstance();
    private View.OnTouchListener globalTouchListener;
    private String currentlyPlayingVideoId = null;

    public HomeAdapter(Context context, List<ImageItem> profileItems, List<VideoItem> videoItems) {
        this.context = context;
        items.add(profileItems);
        items.addAll(videoItems);
    }

    public void setGlobalTouchListener(View.OnTouchListener listener) {
        this.globalTouchListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_PROFILE) {
            View view = inflater.inflate(R.layout.item_profile_container, parent, false);
            return new ProfileViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_video, parent, false);
            return new VideoViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
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
            ((VideoViewHolder) holder).releasePlayer();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_PROFILE;
        } else {
            return TYPE_VIDEO;
        }
    }

    @Override
    public int getItemCount() {
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

    public void playVideo(String videoId) {
        if (currentlyPlayingVideoId != null && currentlyPlayingVideoId.equals(videoId)) {
            return;
        }

        String oldVideoId = currentlyPlayingVideoId;
        currentlyPlayingVideoId = videoId;

        // Pause old video
        if (oldVideoId != null) {
            playerManager.pauseVideo(oldVideoId);
            int oldPosition = findVideoPositionById(oldVideoId);
            if (oldPosition != -1) {
                mainHandler.post(() -> notifyItemChanged(oldPosition));
            }
        }

        // Play new video
        playerManager.playVideo(videoId);
        int newPosition = findVideoPositionById(videoId);
        if (newPosition != -1) {
            mainHandler.post(() -> notifyItemChanged(newPosition));
        }
    }

    public void pauseVideo(String videoId) {
        if (currentlyPlayingVideoId != null && currentlyPlayingVideoId.equals(videoId)) {
            currentlyPlayingVideoId = null;
            playerManager.pauseVideo(videoId);

            int position = findVideoPositionById(videoId);
            if (position != -1) {
                mainHandler.post(() -> notifyItemChanged(position));
            }
        }
    }
    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        RecyclerView profileRecyclerView;

        ProfileViewHolder(View itemView) {
            super(itemView);
            profileRecyclerView = itemView.findViewById(R.id.profileRecyclerView);

        }

        void bind(List<ImageItem> profileItems) {
            profileRecyclerView.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            ImageAdapter adapter = new ImageAdapter(profileItems, itemView.getContext());
            profileRecyclerView.setAdapter(adapter);


        }
    }


}