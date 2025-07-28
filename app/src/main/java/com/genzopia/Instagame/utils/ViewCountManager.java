package com.genzopia.Instagame.utils;

import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

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
     * Increment view count in Firebase Realtime Database
     * @param videoId The unique video ID
     */
    private static void incrementViewCountInFirebase(String videoId) {
        final String finalVideoId = videoId;
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId)
                .child("view_count");
        
        videoRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String currentViewCount = task.getResult().getValue(String.class);
                long newViewCount = 1; // Default to 1 if no previous count
                
                if (currentViewCount != null && !currentViewCount.isEmpty()) {
                    try {
                        newViewCount = Long.parseLong(currentViewCount) + 1;
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing view count: " + currentViewCount, e);
                        newViewCount = 1;
                    }
                }
                
                final long finalNewViewCount = newViewCount;
                
                // Update the view count
                videoRef.setValue(String.valueOf(newViewCount))
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully incremented view count for video " + finalVideoId + 
                                  " to " + finalNewViewCount);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to increment view count for video " + finalVideoId, e);
                            // Reset tracking so user can try again
                            viewedVideos.remove(finalVideoId);
                        });
            } else {
                Log.e(TAG, "Failed to get current view count for video " + finalVideoId, task.getException());
                // Reset tracking so user can try again
                viewedVideos.remove(finalVideoId);
            }
        });
    }
    
    /**
     * Get current view count for a video
     * @param videoId The unique video ID
     * @param callback Callback to receive the view count
     */
    public static void getViewCount(String videoId, ViewCountCallback callback) {
        if (videoId == null) {
            callback.onError("Invalid video ID");
            return;
        }
        
        final String finalVideoId = videoId;
        final ViewCountCallback finalCallback = callback;
        
        DatabaseReference videoRef = FirebaseDatabase.getInstance()
                .getReference("videos")
                .child(videoId)
                .child("view_count");
        
        videoRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String viewCount = task.getResult().getValue(String.class);
                if (viewCount != null && !viewCount.isEmpty()) {
                    try {
                        long count = Long.parseLong(viewCount);
                        finalCallback.onSuccess(count);
                    } catch (NumberFormatException e) {
                        finalCallback.onError("Invalid view count format");
                    }
                } else {
                    finalCallback.onSuccess(0);
                }
            } else {
                finalCallback.onError("Failed to get view count");
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