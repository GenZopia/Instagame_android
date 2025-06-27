package com.genzopia.Instagame.vertical_recylerview_custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.channel_view.ChannelActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.hdodenhof.circleimageview.CircleImageView;

public class VideoViewHolder extends RecyclerView.ViewHolder {
    FrameLayout videoContainer;
    ImageView thumbnail;
    PlayerView playerView;
    CircleImageView channelIcon;
    TextView title;
    TextView channelName;
    TextView viewsAndTime;
    private final PlayerManager playerManager = PlayerManager.getInstance();
    private View.OnTouchListener globalTouchListener;
    private VideoItem currentItem;
    private long touchDownTime;
    private static final int TOUCH_SLOP = 8; // pixels
    private float initialX, initialY;

    @SuppressLint("ClickableViewAccessibility")
    public VideoViewHolder(@NonNull View itemView) {
        super(itemView);
        videoContainer = itemView.findViewById(R.id.videoContainer);
        thumbnail = itemView.findViewById(R.id.thumbnail);
        playerView = itemView.findViewById(R.id.playerView);
        channelIcon = itemView.findViewById(R.id.channelIcon);
        title = itemView.findViewById(R.id.title);
        channelName = itemView.findViewById(R.id.channelName);
        viewsAndTime = itemView.findViewById(R.id.viewsAndTime);

        playerView.setUseController(false); // Disable default controls

        // Ensure taps on thumbnail or video trigger click on videoContainer
        playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                videoContainer.performClick();
            }
            return true;
        });

        thumbnail.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                videoContainer.performClick();
            }
            return true;
        });

        // Touch interaction for visual and playback feedback
        videoContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchDownTime = System.currentTimeMillis();
                    initialX = event.getX();
                    initialY = event.getY();
                    v.setAlpha(0.7f);
                    playVideo(); // Start playback on touch
                    if (globalTouchListener != null) {
                        globalTouchListener.onTouch(v, event);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1.0f);
                    pauseVideo(); // Pause on release
                    if (globalTouchListener != null) {
                        globalTouchListener.onTouch(v, event);
                    }
                    return true;
            }
            return false;
        });
    }

    public void setGlobalTouchListener(View.OnTouchListener listener) {
        this.globalTouchListener = listener;
    }

    public void bind(VideoItem videoItem, Context context) {
        this.currentItem = videoItem;
        videoContainer.setTag(videoItem.id);

        // Reset state
        thumbnail.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.INVISIBLE);

        // Load thumbnail
        Glide.with(context)
                .load(videoItem.thumbnailUrl)
                .placeholder(R.drawable.btn_startcall_normal)
                .into(thumbnail);

        // Load channel icon
        Glide.with(context)
                .load(videoItem.channelIconUrl)
                .placeholder(R.drawable.btn_endcall_normal)
                .into(channelIcon);

        // Set text
        title.setText(videoItem.title);
        channelName.setText(videoItem.channelName);
        viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);

        // 👇 Handle click on title → Navigate to Dashboard
        title.setOnClickListener(v -> {
            TempStorage.videoId = videoItem.id;
            BottomNavigationView navView = ((MainActivity) context).findViewById(R.id.nav_view);
            navView.setSelectedItemId(R.id.navigation_dashboard);
        });

        // 👇 Handle click on channel icon → Open ChannelActivity
        channelIcon.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChannelActivity.class);
            intent.putExtra("channel_name", videoItem.channelName);
            context.startActivity(intent);
        });

        // Setup ExoPlayer
        ExoPlayer player = playerManager.getPlayer(context, videoItem.id, videoItem.videoUrl);
        playerView.setPlayer(player);
    }

    public void playVideo() {
        if (currentItem == null) return;
        playerManager.playVideo(currentItem.id);
        thumbnail.setVisibility(View.INVISIBLE);
        playerView.setVisibility(View.VISIBLE);
    }

    public void pauseVideo() {
        if (currentItem == null) return;
        playerManager.pauseVideo(currentItem.id);
        thumbnail.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.INVISIBLE);
    }

    public void releasePlayer() {
        if (currentItem == null) return;
        playerManager.releasePlayer(currentItem.id);
        playerView.setPlayer(null);
        currentItem = null;
    }
}
