package com.genzopia.Instagame.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.channel_view.VideoDetailActivity;

public class VideoNavigationManager {
    
    private static VideoNavigationManager instance;
    private String pendingVideoId = null;
    private boolean shouldPlayInReelView = false;
    
    private VideoNavigationManager() {}
    
    public static VideoNavigationManager getInstance() {
        if (instance == null) {
            instance = new VideoNavigationManager();
        }
        return instance;
    }
    
    /**
     * Navigate to video detail activity for editing (own video)
     */
    public void openVideoForEditing(Context context, String videoId) {
        try {
            Intent intent = new Intent(context, VideoDetailActivity.class);
            intent.putExtra("video_id", videoId);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Error opening video details: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Navigate to reel view to play video (other's video)
     */
    public void playVideoInReelView(Context context, String videoId) {
        try {
            // Set the video to play globally
            setPendingVideoId(videoId);
            setShouldPlayInReelView(true);
            
            // Start MainActivity and navigate to dashboard
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("navigate_to_dashboard", true);
            intent.putExtra("play_video_id", videoId);
            context.startActivity(intent);
            
            Toast.makeText(context, "Opening reel view...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Error navigating to reel view: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Get the pending video ID to play
     */
    public String getPendingVideoId() {
        String videoId = pendingVideoId;
        pendingVideoId = null; // Clear after reading
        return videoId;
    }
    
    /**
     * Check if we should play a specific video
     */
    public boolean shouldPlayInReelView() {
        boolean shouldPlay = shouldPlayInReelView;
        shouldPlayInReelView = false; // Clear after reading
        return shouldPlay;
    }
    
    /**
     * Set the pending video ID
     */
    public void setPendingVideoId(String videoId) {
        this.pendingVideoId = videoId;
    }
    
    /**
     * Set the flag to play in reel view
     */
    public void setShouldPlayInReelView(boolean shouldPlay) {
        this.shouldPlayInReelView = shouldPlay;
    }
    
    /**
     * Clear all pending data
     */
    public void clearPendingData() {
        pendingVideoId = null;
        shouldPlayInReelView = false;
    }
} 