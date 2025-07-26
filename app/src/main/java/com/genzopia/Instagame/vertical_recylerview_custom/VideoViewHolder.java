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
import android.view.GestureDetector;
import android.view.ViewGroup;
import android.os.Handler;
import android.os.Looper;

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
    PlayerView playerView;
    CircleImageView channelIcon;
    TextView title;
    TextView channelName;
    TextView viewsAndTime;
    private VideoItem currentItem;
    
    // Seek bar and touch controls
    private View progressLine;
    private View progressContainer;
    private GestureDetector gestureDetector;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isHolding = false;
    private boolean isPausedByHold = false;
    private ExoPlayer exoPlayer;

    @SuppressLint("ClickableViewAccessibility")
    public VideoViewHolder(@NonNull View itemView) {
        super(itemView);

        videoContainer = itemView.findViewById(R.id.videoContainer);
        playerView     = itemView.findViewById(R.id.playerView);
        channelIcon    = itemView.findViewById(R.id.channelIcon);
        title          = itemView.findViewById(R.id.title);
        channelName    = itemView.findViewById(R.id.channelName);
        viewsAndTime   = itemView.findViewById(R.id.viewsAndTime);
        
        // Initialize progress line and container
        progressLine = itemView.findViewById(R.id.progress_line);
        progressContainer = itemView.findViewById(R.id.progress_container);
        
        // Initialize gesture detector
        gestureDetector = new GestureDetector(itemView.getContext(), new CustomGestureListener());

        playerView.setUseController(false);
        
        // Set up touch listener for video container
        videoContainer.setOnTouchListener((v, event) -> {
            // Check if touch is on progress line first
            if (progressLine.getVisibility() == View.VISIBLE) {
                float touchY = event.getY();
                float progressY = progressLine.getY();
                float progressHeight = progressLine.getHeight();
                
                if (touchY >= progressY && touchY <= progressY + progressHeight) {
                    // Handle progress line touch for seeking
                    return handleProgressLineTouch(event);
                }
            }
            
            // Handle general video container touch
            boolean handled = gestureDetector.onTouchEvent(event);
            
            // Handle touch up for hold/pause functionality
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                onTouchUp();
            }
            
            return handled;
        });
    }

    private boolean handleProgressLineTouch(MotionEvent event) {
        if (exoPlayer != null && exoPlayer.getDuration() > 0) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // Get touch coordinates - if from progress container, use directly; if from PlayerView, convert
                    float x = event.getX();
                    float width = progressContainer.getWidth(); // Use progress container width
                    
                    // If the event is from PlayerView, we need to adjust the x coordinate
                    if (event.getSource() == MotionEvent.TOOL_TYPE_FINGER) {
                        // This is likely from PlayerView, so we need to convert coordinates
                        float playerViewWidth = playerView.getWidth();
                        float progressContainerWidth = progressContainer.getWidth();
                        // Convert x coordinate to progress container coordinate system
                        x = (x / playerViewWidth) * progressContainerWidth;
                    }
                    
                    // Calculate progress (0.0 to 1.0)
                    float progress = x / width;
                    
                    // Clamp progress between 0 and 1
                    progress = Math.max(0f, Math.min(1f, progress));
                    
                    // Update progress line
                    progressLine.setScaleX(progress);
                    
                    // Seek video to the new position
                    long newPosition = (long) (progress * exoPlayer.getDuration());
                    exoPlayer.seekTo(newPosition);
                    
                    android.util.Log.d("VideoViewHolder", "Seeking to: " + newPosition + " (" + progress + ") at x=" + x + ", width=" + width);
                    return true;
                case MotionEvent.ACTION_UP:
                    android.util.Log.d("VideoViewHolder", "Seek bar touch ended");
                    return true;
            }
        }
        return false;
    }

    public void setupSeekBarAndTouchControls(PlayerView playerView, ExoPlayer exoPlayer) {
        this.exoPlayer = exoPlayer;
        // Force progress line to always be visible
        progressLine.setVisibility(View.VISIBLE);
        progressContainer.setVisibility(View.VISIBLE);
        progressLine.setBackgroundColor(0xFFFFFFFF); // White color
        progressLine.setScaleX(0f); // Start at 0 progress
        
        // Set pivot point to left edge so progress grows from left to right
        progressLine.setPivotX(0f);
        progressLine.setPivotY(progressLine.getHeight() / 2f);
        
        // Ensure progress bar stays visible
        progressLine.bringToFront();
        progressContainer.bringToFront();
        
        // Debug logging for visibility
        android.util.Log.d("VideoViewHolder", "Progress line visibility: " + progressLine.getVisibility());
        android.util.Log.d("VideoViewHolder", "Progress container visibility: " + progressContainer.getVisibility());
        android.util.Log.d("VideoViewHolder", "Progress line background: " + progressLine.getBackground());
        android.util.Log.d("VideoViewHolder", "Progress line width: " + progressLine.getWidth() + ", height: " + progressLine.getHeight());
        android.util.Log.d("VideoViewHolder", "Progress container width: " + progressContainer.getWidth() + ", height: " + progressContainer.getHeight());
        
        // Set up touch listener for the PlayerView itself
        playerView.setOnTouchListener((v, event) -> {
            android.util.Log.d("VideoViewHolder", "PlayerView onTouch event: action=" + event.getAction() + ", x=" + event.getX() + ", y=" + event.getY());
            
            // Check if touch is in the bottom area (progress container area)
            float touchY = event.getY();
            float playerHeight = playerView.getHeight();
            float progressContainerHeight = 60f; // Height of progress container
            
            if (touchY >= playerHeight - progressContainerHeight) {
                // Touch is in progress container area - handle seeking
                android.util.Log.d("VideoViewHolder", "Touch in progress container area, y=" + touchY + ", playerHeight=" + playerHeight);
                return handleProgressLineTouch(event);
            }
            
            // Handle general video touch
            boolean handled = gestureDetector.onTouchEvent(event);
            
            // Handle touch up for hold/pause functionality
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                onTouchUp();
            }
            
            return handled;
        });
        
        // Set up touch listener for the progress container
        progressContainer.setOnTouchListener((v, event) -> {
            android.util.Log.d("VideoViewHolder", "Progress container touch: action=" + event.getAction() + ", x=" + event.getX() + ", y=" + event.getY());
            return handleProgressLineTouch(event);
        });
        
        // Progress line is already in layout, just start updates
        startProgressUpdates();
        android.util.Log.d("VideoViewHolder", "Setup seek bar and touch controls");
    }

    void startProgressUpdates() {
        stopProgressUpdates();
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null && exoPlayer.getDuration() > 0) {
                    float progress = (float) exoPlayer.getCurrentPosition() / exoPlayer.getDuration();
                    progressLine.setScaleX(progress);
                    
                    // Ensure progress bar stays visible
                    if (progressLine.getVisibility() != View.VISIBLE) {
                        progressLine.setVisibility(View.VISIBLE);
                        android.util.Log.d("VideoViewHolder", "Forced progress line to visible");
                    }
                    if (progressContainer.getVisibility() != View.VISIBLE) {
                        progressContainer.setVisibility(View.VISIBLE);
                        android.util.Log.d("VideoViewHolder", "Forced progress container to visible");
                    }
                    
                    // Debug log every 2 seconds
                    if (exoPlayer.getCurrentPosition() % 2000 < 100) {
                        android.util.Log.d("VideoViewHolder", "Progress: " + progress + " (" + 
                            exoPlayer.getCurrentPosition() + "/" + exoPlayer.getDuration() + ")");
                        android.util.Log.d("VideoViewHolder", "Progress line scaleX: " + progressLine.getScaleX());
                        android.util.Log.d("VideoViewHolder", "Progress line visibility: " + progressLine.getVisibility());
                        android.util.Log.d("VideoViewHolder", "Video is playing: " + exoPlayer.isPlaying());
                    }
                }
                if (progressHandler != null) {
                    progressHandler.postDelayed(this, 100); // Update every 100ms
                }
            }
        };
        progressHandler.post(progressRunnable);
        android.util.Log.d("VideoViewHolder", "Started progress updates");
    }

    void stopProgressUpdates() {
        if (progressHandler != null) {
            progressHandler.removeCallbacksAndMessages(null);
            progressHandler = null;
        }
        progressRunnable = null;
    }

    // Method to handle touch up (release)
    public void onTouchUp() {
        if (isHolding && isPausedByHold) {
            if (exoPlayer != null) {
                exoPlayer.setPlayWhenReady(true);
                isPausedByHold = false;
            }
        }
        isHolding = false;
    }

    class CustomGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            // Check if tap is in upper or lower half of the PlayerView
            float y = e.getY();
            float height = playerView.getHeight();
            
            // Debug log
            android.util.Log.d("VideoViewHolder", "Single tap at y=" + y + ", height=" + height);
            
            if (y < height / 2) {
                // Upper half - toggle mute/unmute
                android.util.Log.d("VideoViewHolder", "Upper half tap - toggling mute");
                if (exoPlayer != null) {
                    float currentVolume = exoPlayer.getVolume();
                    if (currentVolume > 0f) {
                        exoPlayer.setVolume(0f); // Mute
                        android.util.Log.d("VideoViewHolder", "Muted video");
                    } else {
                        exoPlayer.setVolume(1f); // Unmute
                        android.util.Log.d("VideoViewHolder", "Unmuted video");
                    }
                }
            } else {
                // Lower half - toggle pause/resume
                android.util.Log.d("VideoViewHolder", "Lower half tap - toggling pause/resume");
                if (exoPlayer != null) {
                    if (exoPlayer.isPlaying()) {
                        exoPlayer.setPlayWhenReady(false);
                        android.util.Log.d("VideoViewHolder", "Paused video");
                    } else {
                        exoPlayer.setPlayWhenReady(true);
                        android.util.Log.d("VideoViewHolder", "Resumed video");
                    }
                }
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
                    android.util.Log.d("VideoViewHolder", "Hold detected - pausing");
                    if (exoPlayer != null && exoPlayer.isPlaying()) {
                        exoPlayer.setPlayWhenReady(false);
                        isPausedByHold = true;
                    }
                }
            }
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            // Alternative long press detection
            android.util.Log.d("VideoViewHolder", "Long press detected - pausing");
            isHolding = true;
            if (exoPlayer != null && exoPlayer.isPlaying()) {
                exoPlayer.setPlayWhenReady(false);
                isPausedByHold = true;
            }
        }
    }

    public void bind(VideoItem videoItem, Context context) {
        this.currentItem = videoItem;
        // PlayerView is always visible, just paused/playing
        playerView.setVisibility(View.VISIBLE);

        // load channel icon
        Glide.with(context)
                .load(videoItem.channelIconUrl)
                .placeholder(R.drawable.btn_endcall_normal)
                .into(channelIcon);

        title.setText(videoItem.title);
        channelName.setText(videoItem.channelName);
        viewsAndTime.setText(videoItem.views + " • " + videoItem.timeAgo);

        // ---- CLICK TO NAVIGATE TO DASHBOARD ----
        View.OnClickListener toDashboard = v -> {
            TempStorage.videoId = videoItem.id;
            BottomNavigationView navView = ((MainActivity) context).findViewById(R.id.nav_view);
            navView.setSelectedItemId(R.id.navigation_dashboard);
        };

        // Attach the same click to title only (not playerView)
        title.setOnClickListener(toDashboard);

        // ---- OPEN CHANNEL ACTIVITY ----
        channelIcon.setOnClickListener(v -> {
            Intent intent = new Intent(context, ChannelActivity.class);
            intent.putExtra("channel_name", videoItem.channelName);
            context.startActivity(intent);
        });
    }
    // Removed playVideo, pauseVideo, and releasePlayer methods
}
