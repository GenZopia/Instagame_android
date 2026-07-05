package com.genzopia.Instagame.utils;

import android.util.Log;
import com.genzopia.Instagame.gateway.GatewayClient;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewCountManager {
    private static final String TAG = "ViewCountManager";
    private static final double VIEW_THRESHOLD = 0.6; // 60%
    
    private static final Map<String, Boolean> viewedVideos = new HashMap<>();
    private static final Map<String, Long> videoDurations = new HashMap<>();
    
    /**
     * Check if video has been viewed for 60% and increment view count if needed
     * @param videoId The unique video ID
     * @param currentPosition Current playback position in milliseconds
     * @param duration Total video duration in milliseconds
     */
    public static void checkAndIncrementViewCount(String videoId, long currentPosition, long duration) {
        if (videoId == null || duration <= 0) {
            Log.w(TAG, "Invalid video ID or duration");
            return;
        }
        
        // Check if already viewed
        if (viewedVideos.containsKey(videoId) && viewedVideos.get(videoId)) {
            return; // Already counted this view
        }
        
        // Calculate view percentage
        double viewPercentage = (double) currentPosition / duration;
        
        // Check if 60% threshold is reached
        if (viewPercentage >= VIEW_THRESHOLD) {
            Log.d(TAG, "Video " + videoId + " reached 60% view threshold. Current: " + 
                  String.format("%.1f%%", viewPercentage * 100));
            
            // Mark as viewed to prevent multiple increments
            viewedVideos.put(videoId, true);
            
            // Increment view count in Firebase
            incrementViewCountInFirebase(videoId);
        }
    }
    
    /**
     * Reset view tracking for a video (useful when video is replayed)
     * @param videoId The unique video ID
     */
    public static void resetVideoViewTracking(String videoId) {
        if (videoId != null) {
            viewedVideos.remove(videoId);
            Log.d(TAG, "Reset view tracking for video: " + videoId);
        }
    }
    
    /**
     * Store video duration for tracking
     * @param videoId The unique video ID
     * @param duration Video duration in milliseconds
     */
    public static void setVideoDuration(String videoId, long duration) {
        if (videoId != null && duration > 0) {
            videoDurations.put(videoId, duration);
            Log.d(TAG, "Stored duration for video " + videoId + ": " + duration + "ms");
        }
    }
    
    /**
     * Get stored video duration
     * @param videoId The unique video ID
     * @return Video duration in milliseconds, or -1 if not found
     */
    public static long getVideoDuration(String videoId) {
        return videoDurations.getOrDefault(videoId, -1L);
    }
    
    /**
     * Clear all stored data (useful for memory management)
     */
    public static void clearAllData() {
        viewedVideos.clear();
        videoDurations.clear();
        Log.d(TAG, "Cleared all view tracking data");
    }
    
    /**
     * Increment view count via the backend Gateway (POST /reels/{videoId}/view).
     * Requirements: 4.1
     */
    private static void incrementViewCountInFirebase(String videoId) {
        GatewayClient.INSTANCE.getCallApi().recordView(videoId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "View count recorded via gateway for " + videoId);
                } else {
                    Log.e(TAG, "Gateway recordView HTTP " + response.code() + " for " + videoId);
                    viewedVideos.remove(videoId);
                }
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "Gateway recordView failed for " + videoId + ": " + t.getMessage());
                viewedVideos.remove(videoId);
            }
        });
    }
    
    /**
     * Callback interface for view count operations
     */
    public interface ViewCountCallback {
        void onSuccess(long viewCount);
        void onError(String error);
    }
} 