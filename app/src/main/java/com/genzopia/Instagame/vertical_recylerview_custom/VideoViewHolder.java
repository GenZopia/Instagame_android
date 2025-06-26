package com.genzopia.Instagame.vertical_recylerview_custom;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
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

        // Single touch listener combining both visual feedback and global listener
        videoContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.7f);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1.0f);
                    break;
            }

            if (globalTouchListener != null) {
                return globalTouchListener.onTouch(v, event);
            }
            return false;
        });
    }

    public void setGlobalTouchListener(View.OnTouchListener listener) {
        this.globalTouchListener = listener;
    }

    public void bind(VideoItem videoItem, Context context) {
        this.currentItem = videoItem;
        videoContainer.setTag(videoItem.id); // Store video ID in container tag

        // Reset to default state
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

        title.setText(videoItem.title);
        channelName.setText(videoItem.channelName);
        viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);

        // Setup player
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