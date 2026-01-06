package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

/**
 * Adaptive Performance Manager - Prevents video lag through intelligent optimization
 * Automatically adjusts video quality, buffer sizes, and preload settings based on device performance
 * 
 * PRODUCTION-READY: Comprehensive lag prevention for all device types
 */
public class AdaptivePerformanceManager {
    private static final String TAG = "AdaptivePerformanceManager";
    
    private final Context context;
    private final DevicePerformanceDetector.PerformanceLevel performanceLevel;
    private final Handler mainHandler;
    
    // Performance monitoring
    private long lastFrameDropCheck = 0;
    private int consecutiveFrameDrops = 0;
    private boolean isPerformanceDegraded = false;
    
    // Adaptive settings
    private int currentPreloadRange;
    private int currentCacheSize;
    private int currentBufferDuration;
    private String currentVideoQuality;
    
    public AdaptivePerformanceManager(Context context) {
        this.context = context;
        this.performanceLevel = DevicePerformanceDetector.detectPerformanceLevel(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        initializeAdaptiveSettings();
        Log.i(TAG, "Initialized for " + performanceLevel + " device with adaptive settings");
    }
    
    /**
     * Initialize settings based on device performance level
     */
    private void initializeAdaptiveSettings() {
        switch (performanceLevel) {
            case HIGH_END:
                currentPreloadRange = 4;      // Full preloading
                currentCacheSize = 15;        // Large cache
                currentBufferDuration = 30000; // 30s buffer
                currentVideoQuality = "1080p";
                break;
                
            case MID_RANGE:
                currentPreloadRange = 2;      // Moderate preloading
                currentCacheSize = 8;         // Medium cache
                currentBufferDuration = 15000; // 15s buffer
                currentVideoQuality = "720p";
                break;
                
            case LOW_END:
            default:
                currentPreloadRange = 1;      // Minimal preloading
                currentCacheSize = 3;         // Small cache
                currentBufferDuration = 5000; // 5s buffer
                currentVideoQuality = "480p";
                break;
        }
        
        Log.i(TAG, String.format("Initial settings: preload=%d, cache=%d, buffer=%dms, quality=%s",
              currentPreloadRange, currentCacheSize, currentBufferDuration, currentVideoQuality));
    }
    
    /**
     * Create optimized LoadControl based on device performance
     * CRITICAL: Prevents buffering lag and memory issues
     */
    public LoadControl createOptimizedLoadControl() {
        try {
            DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
            
            switch (performanceLevel) {
                case HIGH_END:
                    // High-end devices: Larger buffers for smooth playback
                    return builder
                        .setBufferDurationsMs(
                            2500,   // minBufferMs
                            30000,  // maxBufferMs
                            1500,   // bufferForPlaybackMs
                            2000    // bufferForPlaybackAfterRebufferMs
                        )
                        .setTargetBufferBytes(8 * 1024 * 1024) // 8MB buffer
                        .setPrioritizeTimeOverSizeThresholds(false)
                        .build();
                        
                case MID_RANGE:
                    // Mid-range devices: Balanced buffers
                    return builder
                        .setBufferDurationsMs(
                            2000,   // minBufferMs
                            15000,  // maxBufferMs
                            1000,   // bufferForPlaybackMs
                            1500    // bufferForPlaybackAfterRebufferMs
                        )
                        .setTargetBufferBytes(4 * 1024 * 1024) // 4MB buffer
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();
                        
                case LOW_END:
                default:
                    // Low-end devices: Minimal buffers to prevent lag
                    return builder
                        .setBufferDurationsMs(
                            1500,   // minBufferMs
                            5000,   // maxBufferMs
                            800,    // bufferForPlaybackMs
                            1000    // bufferForPlaybackAfterRebufferMs
                        )
                        .setTargetBufferBytes(1024 * 1024) // 1MB buffer
                        .setPrioritizeTimeOverSizeThresholds(true)
                        .build();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating optimized load control, using default", e);
            return new DefaultLoadControl();
        }
    }
    
    /**
     * Create adaptive track selector for quality optimization
     * CRITICAL: Automatically adjusts video quality to prevent lag
     */
    public DefaultTrackSelector createAdaptiveTrackSelector() {
        try {
            DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
            
            // Configure track selector based on device performance
            DefaultTrackSelector.Parameters.Builder parametersBuilder = trackSelector.getParameters().buildUpon();
            
            switch (performanceLevel) {
                case HIGH_END:
                    // High-end: Allow maximum quality
                    parametersBuilder
                        .setMaxVideoSizeSd() // Allow up to 1080p
                        .setMaxVideoBitrate(8000000) // 8Mbps max
                        .setForceHighestSupportedBitrate(false); // Adaptive
                    break;
                    
                case MID_RANGE:
                    // Mid-range: Limit to 720p for stability
                    parametersBuilder
                        .setMaxVideoSize(1280, 720) // Max 720p
                        .setMaxVideoBitrate(4000000) // 4Mbps max
                        .setForceLowestBitrate(false); // Adaptive within limits
                    break;
                    
                case LOW_END:
                default:
                    // Low-end: Force lower quality for smooth playback
                    parametersBuilder
                        .setMaxVideoSize(854, 480) // Max 480p
                        .setMaxVideoBitrate(2000000) // 2Mbps max
                        .setForceLowestBitrate(true); // Force lowest for performance
                    break;
            }
            
            trackSelector.setParameters(parametersBuilder);
            return trackSelector;
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating adaptive track selector, using default", e);
            return new DefaultTrackSelector(context);
        }
    }
    
    /**
     * Monitor performance and adapt settings dynamically
     * CRITICAL: Detects lag and automatically reduces quality/buffers
     */
    public void monitorAndAdaptPerformance(long currentTimeMs, boolean isFrameDropped) {
        // Check performance every 5 seconds
        if (currentTimeMs - lastFrameDropCheck < 5000) {
            if (isFrameDropped) {
                consecutiveFrameDrops++;
            }
            return;
        }
        
        lastFrameDropCheck = currentTimeMs;
        
        // Analyze performance degradation
        boolean wasPerformanceDegraded = isPerformanceDegraded;
        isPerformanceDegraded = consecutiveFrameDrops > 3; // More than 3 drops in 5 seconds
        
        if (isPerformanceDegraded && !wasPerformanceDegraded) {
            Log.w(TAG, "Performance degradation detected, reducing settings");
            reducePerformanceSettings();
        } else if (!isPerformanceDegraded && wasPerformanceDegraded) {
            Log.i(TAG, "Performance improved, considering settings increase");
            // Gradually increase settings after stable performance
            mainHandler.postDelayed(this::considerIncreasingSettings, 10000); // Wait 10s
        }
        
        consecutiveFrameDrops = 0; // Reset counter
    }
    
    /**
     * Reduce performance settings to prevent lag
     */
    private void reducePerformanceSettings() {
        // Reduce preload range
        if (currentPreloadRange > 0) {
            currentPreloadRange = Math.max(0, currentPreloadRange - 1);
            Log.i(TAG, "Reduced preload range to: " + currentPreloadRange);
        }
        
        // Reduce cache size
        if (currentCacheSize > 2) {
            currentCacheSize = Math.max(2, currentCacheSize - 2);
            Log.i(TAG, "Reduced cache size to: " + currentCacheSize);
        }
        
        // Reduce buffer duration
        if (currentBufferDuration > 3000) {
            currentBufferDuration = Math.max(3000, currentBufferDuration - 2000);
            Log.i(TAG, "Reduced buffer duration to: " + currentBufferDuration + "ms");
        }
        
        // Reduce video quality
        if ("1080p".equals(currentVideoQuality)) {
            currentVideoQuality = "720p";
            Log.i(TAG, "Reduced video quality to: " + currentVideoQuality);
        } else if ("720p".equals(currentVideoQuality)) {
            currentVideoQuality = "480p";
            Log.i(TAG, "Reduced video quality to: " + currentVideoQuality);
        }
    }
    
    /**
     * Consider increasing settings after stable performance
     */
    private void considerIncreasingSettings() {
        if (!isPerformanceDegraded) {
            // Only increase if we're below optimal for device level
            int optimalPreload = getOptimalPreloadRange();
            if (currentPreloadRange < optimalPreload) {
                currentPreloadRange = Math.min(optimalPreload, currentPreloadRange + 1);
                Log.i(TAG, "Increased preload range to: " + currentPreloadRange);
            }
        }
    }
    
    /**
     * Get optimal preload range for device performance level
     */
    private int getOptimalPreloadRange() {
        switch (performanceLevel) {
            case HIGH_END: return 4;
            case MID_RANGE: return 2;
            case LOW_END:
            default: return 1;
        }
    }
    
    // Getters for current adaptive settings
    public int getCurrentPreloadRange() { return currentPreloadRange; }
    public int getCurrentCacheSize() { return currentCacheSize; }
    public int getCurrentBufferDuration() { return currentBufferDuration; }
    public String getCurrentVideoQuality() { return currentVideoQuality; }
    public DevicePerformanceDetector.PerformanceLevel getPerformanceLevel() { return performanceLevel; }
    
    /**
     * Force emergency performance mode (for critical lag situations)
     */
    public void enableEmergencyMode() {
        Log.w(TAG, "Emergency performance mode activated");
        currentPreloadRange = 0;      // Disable preloading
        currentCacheSize = 1;         // Minimal cache
        currentBufferDuration = 2000; // 2s buffer only
        currentVideoQuality = "360p"; // Lowest quality
        isPerformanceDegraded = true;
    }
    
    /**
     * Check if device needs emergency optimizations
     */
    public boolean needsEmergencyOptimization() {
        return performanceLevel == DevicePerformanceDetector.PerformanceLevel.LOW_END && isPerformanceDegraded;
    }
}