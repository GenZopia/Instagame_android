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
    private VideoItem currentItem;

    @SuppressLint("ClickableViewAccessibility")
    public VideoViewHolder(@NonNull View itemView) {
        super(itemView);

        videoContainer = itemView.findViewById(R.id.videoContainer);
        thumbnail      = itemView.findViewById(R.id.thumbnail);
        playerView     = itemView.findViewById(R.id.playerView);
        channelIcon    = itemView.findViewById(R.id.channelIcon);
        title          = itemView.findViewById(R.id.title);
        channelName    = itemView.findViewById(R.id.channelName);
        viewsAndTime   = itemView.findViewById(R.id.viewsAndTime);

        playerView.setUseController(false);

        // Play/pause on touch-down/up, but **return false** so clicks still fire
        videoContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.7f);
                    playVideo();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1.0f);
                    pauseVideo();
                    break;
            }
            return false; // allow click events to propagate
        });
    }

    public void bind(VideoItem videoItem, Context context) {
        this.currentItem = videoItem;

        // reset UI
        thumbnail.setVisibility(View.VISIBLE);
        playerView.setVisibility(View.INVISIBLE);

        // load assets
        Glide.with(context)
                .load(videoItem.thumbnailUrl)
                .placeholder(R.drawable.btn_startcall_normal)
                .into(thumbnail);

        Glide.with(context)
                .load(videoItem.channelIconUrl)
                .placeholder(R.drawable.btn_endcall_normal)
                .into(channelIcon);

        title.setText(videoItem.title);
        channelName.setText(videoItem.channelName);
        viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);

        // EXO PLAYER
        ExoPlayer player = playerManager.getPlayer(context, videoItem.id, videoItem.videoUrl);
        playerView.setPlayer(player);

        // ---- CLICK TO NAVIGATE TO DASHBOARD ----
        View.OnClickListener toDashboard = v -> {
            TempStorage.videoId = videoItem.id;
            BottomNavigationView navView = ((MainActivity) context).findViewById(R.id.nav_view);
            navView.setSelectedItemId(R.id.navigation_dashboard);
        };

        // Attach the same click to thumbnail, playerView, title
        thumbnail.setOnClickListener(toDashboard);
        playerView.setOnClickListener(toDashboard);
        title.setOnClickListener(toDashboard);

        // ---- OPEN CHANNEL ACTIVITY ----
        channelIcon.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChannelActivity.class);
            intent.putExtra("channel_name", videoItem.channelName);
            context.startActivity(intent);
        });
    }

    private void playVideo() {
        if (currentItem == null) return;
        playerManager.playVideo(currentItem.id);
        thumbnail.setVisibility(View.INVISIBLE);
        playerView.setVisibility(View.VISIBLE);
    }

    private void pauseVideo() {
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
