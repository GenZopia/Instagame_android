// HomeAdapter.java
package com.genzopia.Instagame.vertical_recylerview_custom;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.profile_recyclerview.ImageAdapter;
import com.genzopia.Instagame.profile_recyclerview.ImageItem;
import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogRecord;

import android.os.Handler;

import de.hdodenhof.circleimageview.CircleImageView;

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
        Button button;

        ProfileViewHolder(View itemView) {
            super(itemView);
            profileRecyclerView = itemView.findViewById(R.id.profileRecyclerView);
            button = itemView.findViewById(R.id.button);
        }

        void bind(List<ImageItem> profileItems) {
            profileRecyclerView.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false)
            );
            ImageAdapter adapter = new ImageAdapter(profileItems, itemView.getContext());
            profileRecyclerView.setAdapter(adapter);

            button.setOnClickListener(v -> {
                itemView.getContext().startActivity(new Intent(itemView.getContext(), Game_mode.class));
            });
        }
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        FrameLayout videoContainer;
        ImageView thumbnail;
        PlayerView playerView;
        CircleImageView channelIcon;
        TextView title;
        TextView channelName;
        TextView viewsAndTime;
        private ExoPlayer player;
        private VideoItem currentItem;

        VideoViewHolder(View itemView) {
            super(itemView);
            videoContainer = itemView.findViewById(R.id.videoContainer);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            playerView = itemView.findViewById(R.id.playerView);
            channelIcon = itemView.findViewById(R.id.channelIcon);
            title = itemView.findViewById(R.id.title);
            channelName = itemView.findViewById(R.id.channelName);
            viewsAndTime = itemView.findViewById(R.id.viewsAndTime);

            playerView.setUseController(false);
            playerView.setBackgroundColor(0x00000000);
        }

        void bind(VideoItem videoItem, Context context) {
            this.currentItem = videoItem;
            videoContainer.setTag(videoItem.id);

            // Reset to default state
            thumbnail.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.INVISIBLE);

            // Load thumbnail
            Glide.with(context)
                    .load(videoItem.thumbnailUrl)
                    .placeholder(R.drawable.ic_accept)
                    .into(thumbnail);

            // Load channel icon
            Glide.with(context)
                    .load(videoItem.channelIconUrl)
                    .placeholder(R.drawable.ic_reject)
                    .into(channelIcon);

            title.setText(videoItem.title);
            channelName.setText(videoItem.channelName);
            viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);

            // Setup player
            player = playerManager.getPlayer(context, videoItem.id, videoItem.videoUrl);
            playerView.setPlayer(player);

            // Setup touch listener
            if (globalTouchListener != null) {
                videoContainer.setOnTouchListener(globalTouchListener);
            }

            // Update playback state
            if (videoItem.id.equals(currentlyPlayingVideoId)) {
                playVideo();
            } else {
                pauseVideo();
            }
        }

        void playVideo() {
            if (currentItem == null) return;

            thumbnail.setVisibility(View.INVISIBLE);
            playerView.setVisibility(View.VISIBLE);

            if (player != null) {
                player.setPlayWhenReady(true);
            }
        }

        void pauseVideo() {
            if (currentItem == null) return;

            thumbnail.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.INVISIBLE);

            if (player != null) {
                player.setPlayWhenReady(false);
            }
        }

        void releasePlayer() {
            pauseVideo();

            if (playerView != null) {
                playerView.setPlayer(null);
            }

            currentItem = null;
        }
    }
}