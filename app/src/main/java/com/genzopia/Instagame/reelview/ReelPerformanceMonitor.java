package com.genzopia.Instagame.reelview;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ReelPerformanceMonitor - Comprehensive performance monitoring system for reelview
 * Tracks video transitions, memory usage, cache performance, and scroll frame rates
 * Provides debug logging and performance warnings for optimization insights
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4, 8.5
 */
public class ReelPerformanceMonitor {
    private static final String TAG = "ReelPerformance";
    
    // Performance thresholds
    private static final long VIDEO_TRANSITION_WARNING_MS = 200;
    private static final long MEMORY_WARNING_THRESHOLD_MB = 150;
    private static final int FRAME_DROP_WARNING_THRESHOLD = 3;
    
    // Video transition tracking
    private final Map<String, Long> videoTransitionStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> videoTransitionTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalTransitionTime = new AtomicLong(0);
    private final AtomicInteger transitionCount = new AtomicInteger(0);
    
    // Cache performance tracking
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicInteger thumbnailCacheHits = new AtomicInteger(0);
    private final AtomicInteger thumbnailCacheMisses = new AtomicInteger(0);
    
    // Memory usage monitoring
    private final AtomicLong lastMemoryCheck = new AtomicLong(0);
    private final AtomicLong peakMemoryUsage = new AtomicLong(0);
    private final Handler memoryHandler = new Handler(Looper.getMainLooper());
    private Runnable memoryMonitorRunnable;
    
    // Scroll performance tracking
    private final AtomicLong lastFrameTime = new AtomicLong(0);
    private final AtomicInteger droppedFrames = new AtomicInteger(0);
    private final AtomicInteger totalFrames = new AtomicInteger(0);
    private boolean isScrolling = false;
    
    // Performance statistics
    private final AtomicLong sessionStartTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger errorCount = new AtomicInteger(0);
    
    public ReelPerformanceMonitor() {
        startMemoryMonitoring();
        Log.i(TAG, "ReelPerformanceMonitor initialized");
    }
    
    // ===== VIDEO TRANSITION MONITORING =====
    
    /**
     * Start tracking video transition time
     * Requirements: 8.1 - Track video transition times and log when exceeding 200ms
     */
    public void startVideoTransition(String videoId) {
        if (videoId == null) return;
        
        long startTime = System.currentTimeMillis();
        videoTransitionStartTimes.put(videoId, startTime);
        
        Log.d(TAG, "Started video transition: " + videoId + " at " + startTime);
    }
    
    /**
     * End video transition tracking and log performance
     * Requirements: 8.1 - Track video transition times and log when exceeding 200ms
     */
    public void endVideoTransition(String videoId, boolean fromCache) {
        if (videoId == null) return;
        
        Long startTime = videoTransitionStartTimes.remove(videoId);
        if (startTime == null) {
            Log.w(TAG, "No start time found for video transition: " + videoId);
            return;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Store transition time
        videoTransitionTimes.put(videoId, duration);
        
        // Update statistics
        totalTransitionTime.addAndGet(duration);
        transitionCount.incrementAndGet();
        
        // Update cache statistics
        if (fromCache) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
        }
        
        // Log performance with detailed context
        String cacheStatus = fromCache ? "CACHED" : "ON_DEMAND";
        Log.d(TAG, String.format("Video transition complete: %s = %dms (%s)", 
                                videoId, duration, cacheStatus));
        
        // Warning for slow transitions (Requirements: 8.1)
        if (duration > VIDEO_TRANSITION_WARNING_MS) {
            Log.w(TAG, String.format("SLOW VIDEO TRANSITION: %s took %dms (threshold: %dms, cache: %s)", 
                                   videoId, duration, VIDEO_TRANSITION_WARNING_MS, cacheStatus));
        }
        
        // Log cache performance every 10 transitions
        if (transitionCount.get() % 10 == 0) {
            logCachePerformance();
        }
    }
    
    /**
     * Get average video transition time
     */
    public double getAverageTransitionTime() {
        int count = transitionCount.get();
        if (count == 0) return 0.0;
        return (double) totalTransitionTime.get() / count;
    }
    
    /**
     * Perform complete video transition (for testing compatibility)
     * This method combines startVideoTransition and endVideoTransition for test scenarios
     */
    public void performVideoTransition(String videoId, boolean fromCache, long simulatedDelayMs) {
        startVideoTransition(videoId);
        
        // Simulate transition delay if specified
        if (simulatedDelayMs > 0) {
            try {
                Thread.sleep(Math.min(simulatedDelayMs, 100)); // Cap at 100ms for test performance
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        endVideoTransition(videoId, fromCache);
    }
    
    /**
     * Get transition metrics (for testing compatibility)
     * Returns a simple metrics object with key performance data
     */
    public TransitionMetrics getTransitionMetrics() {
        return new TransitionMetrics(
            transitionCount.get(),
            getAverageTransitionTime(),
            cacheHits.get(),
            cacheMisses.get(),
            getCurrentMemoryUsageMB(),
            getPeakMemoryUsageMB()
        );
    }
    
    /**
     * Simple metrics class for test compatibility
     */
    public static class TransitionMetrics {
        public final int totalTransitions;
        public final double averageTransitionTime;
        public final int cacheHits;
        public final int cacheMisses;
        public final long currentMemoryMB;
        public final long peakMemoryMB;
        
        public TransitionMetrics(int totalTransitions, double averageTransitionTime, 
                               int cacheHits, int cacheMisses, 
                               long currentMemoryMB, long peakMemoryMB) {
            this.totalTransitions = totalTransitions;
            this.averageTransitionTime = averageTransitionTime;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.currentMemoryMB = currentMemoryMB;
            this.peakMemoryMB = peakMemoryMB;
        }
        
        public float getCacheHitRate() {
            int total = cacheHits + cacheMisses;
            return total > 0 ? (float) cacheHits / total * 100 : 0f;
        }
    }
    
    // ===== CACHE PERFORMANCE MONITORING =====
    
    /**
     * Record cache hit for player cache
     * Requirements: 8.3 - Count cache hit/miss ratios for optimization insights
     */
    public void recordCacheHit() {
        cacheHits.incrementAndGet();
        Log.d(TAG, "Player cache HIT (total hits: " + cacheHits.get() + ")");
    }
    
    /**
     * Record cache miss for player cache
     * Requirements: 8.3 - Count cache hit/miss ratios for optimization insights
     */
    public void recordCacheMiss() {
        cacheMisses.incrementAndGet();
        Log.d(TAG, "Player cache MISS (total misses: " + cacheMisses.get() + ")");
    }
    
    /**
     * Record thumbnail cache hit
     * Requirements: 8.3 - Count cache hit/miss ratios for optimization insights
     */
    public void recordThumbnailCacheHit() {
        thumbnailCacheHits.incrementAndGet();
        Log.d(TAG, "Thumbnail cache HIT (total hits: " + thumbnailCacheHits.get() + ")");
    }
    
    /**
     * Record thumbnail cache miss
     * Requirements: 8.3 - Count cache hit/miss ratios for optimization insights
     */
    public void recordThumbnailCacheMiss() {
        thumbnailCacheMisses.incrementAndGet();
        Log.d(TAG, "Thumbnail cache MISS (total misses: " + thumbnailCacheMisses.get() + ")");
    }
    
    /**
     * Log comprehensive cache performance statistics
     * Requirements: 8.3 - Count cache hit/miss ratios for optimization insights
     */
    public void logCachePerformance() {
        int playerHits = cacheHits.get();
        int playerMisses = cacheMisses.get();
        int thumbnailHits = thumbnailCacheHits.get();
        int thumbnailMisses = thumbnailCacheMisses.get();
        
        // Calculate hit rates
        float playerHitRate = calculateHitRate(playerHits, playerMisses);
        float thumbnailHitRate = calculateHitRate(thumbnailHits, thumbnailMisses);
        
        Log.i(TAG, String.format("=== CACHE PERFORMANCE REPORT ==="));
        Log.i(TAG, String.format("Player Cache - Hits: %d, Misses: %d, Hit Rate: %.1f%%", 
                                playerHits, playerMisses, playerHitRate));
        Log.i(TAG, String.format("Thumbnail Cache - Hits: %d, Misses: %d, Hit Rate: %.1f%%", 
                                thumbnailHits, thumbnailMisses, thumbnailHitRate));
        
        // Performance warnings
        if (playerHitRate < 60.0f && (playerHits + playerMisses) > 10) {
            Log.w(TAG, "LOW PLAYER CACHE HIT RATE: " + playerHitRate + "% (target: >60%)");
        }
        
        if (thumbnailHitRate < 80.0f && (thumbnailHits + thumbnailMisses) > 10) {
            Log.w(TAG, "LOW THUMBNAIL CACHE HIT RATE: " + thumbnailHitRate + "% (target: >80%)");
        }
    }
    
    private float calculateHitRate(int hits, int misses) {
        int total = hits + misses;
        return total > 0 ? (float) hits / total * 100 : 0f;
    }
    
    // ===== MEMORY USAGE MONITORING =====
    
    /**
     * Start continuous memory monitoring
     * Requirements: 8.2 - Monitor memory usage and warn when approaching limits
     */
    private void startMemoryMonitoring() {
        memoryMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                checkMemoryUsage();
                // Check memory every 5 seconds
                memoryHandler.postDelayed(this, 5000);
            }
        };
        memoryHandler.post(memoryMonitorRunnable);
    }
    
    /**
     * Check current memory usage and log warnings
     * Requirements: 8.2 - Monitor memory usage and warn when approaching limits
     */
    public void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // MB
        long maxMemory = runtime.maxMemory() / (1024 * 1024); // MB
        
        // Update peak memory usage
        peakMemoryUsage.updateAndGet(current -> Math.max(current, usedMemory));
        
        // Log detailed memory info every 30 seconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMemoryCheck.get() > 30000) {
            lastMemoryCheck.set(currentTime);
            
            Log.d(TAG, String.format("Memory Usage: %dMB / %dMB (%.1f%%), Peak: %dMB", 
                                    usedMemory, maxMemory, 
                                    (float) usedMemory / maxMemory * 100,
                                    peakMemoryUsage.get()));
        }
        
        // Warning for high memory usage (Requirements: 8.2)
        if (usedMemory > MEMORY_WARNING_THRESHOLD_MB) {
            Log.w(TAG, String.format("HIGH MEMORY USAGE WARNING: %dMB (threshold: %dMB, max: %dMB)", 
                                    usedMemory, MEMORY_WARNING_THRESHOLD_MB, maxMemory));
        }
        
        // Critical warning at 90% of max memory
        if (usedMemory > maxMemory * 0.9) {
            Log.e(TAG, String.format("CRITICAL MEMORY WARNING: %dMB / %dMB (%.1f%% - approaching limit!)", 
                                    usedMemory, maxMemory, (float) usedMemory / maxMemory * 100));
        }
    }
    
    /**
     * Get current memory usage in MB
     */
    public long getCurrentMemoryUsageMB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
    
    /**
     * Get peak memory usage in MB
     */
    public long getPeakMemoryUsageMB() {
        return peakMemoryUsage.get();
    }
    
    // ===== SCROLL PERFORMANCE MONITORING =====
    
    /**
     * Start scroll performance monitoring
     * Requirements: 8.4 - Measure scroll frame rates and detect dropped frames
     */
    public void startScrollMonitoring() {
        isScrolling = true;
        lastFrameTime.set(System.nanoTime());
        Log.d(TAG, "Started scroll performance monitoring");
    }
    
    /**
     * Record frame during scroll
     * Requirements: 8.4 - Measure scroll frame rates and detect dropped frames
     */
    public void recordScrollFrame() {
        if (!isScrolling) return;
        
        long currentTime = System.nanoTime();
        long lastTime = lastFrameTime.getAndSet(currentTime);
        
        if (lastTime > 0) {
            long frameDuration = (currentTime - lastTime) / 1_000_000; // Convert to milliseconds
            totalFrames.incrementAndGet();
            
            // Detect dropped frames (target: 16.67ms for 60fps)
            if (frameDuration > 33) { // More than 2 frames (33ms)
                droppedFrames.incrementAndGet();
                Log.d(TAG, String.format("Dropped frame detected: %dms (target: <17ms)", frameDuration));
            }
            
            // Log frame performance details
            Log.v(TAG, String.format("Frame time: %dms", frameDuration));
        }
    }
    
    /**
     * Stop scroll performance monitoring and log results
     * Requirements: 8.4 - Measure scroll frame rates and detect dropped frames
     */
    public void stopScrollMonitoring() {
        if (!isScrolling) return;
        
        isScrolling = false;
        int totalFrameCount = totalFrames.get();
        int droppedFrameCount = droppedFrames.get();
        
        if (totalFrameCount > 0) {
            float dropRate = (float) droppedFrameCount / totalFrameCount * 100;
            
            Log.i(TAG, String.format("Scroll Performance: %d frames, %d dropped (%.1f%% drop rate)", 
                                    totalFrameCount, droppedFrameCount, dropRate));
            
            // Warning for high frame drop rate (Requirements: 8.4)
            if (droppedFrameCount > FRAME_DROP_WARNING_THRESHOLD) {
                Log.w(TAG, String.format("HIGH FRAME DROP RATE: %d dropped frames (%.1f%% - target: <5%%)", 
                                        droppedFrameCount, dropRate));
            }
        }
        
        // Reset counters for next scroll session
        totalFrames.set(0);
        droppedFrames.set(0);
    }
    
    // ===== ERROR TRACKING =====
    
    /**
     * Record error occurrence
     * Requirements: 8.5 - Provide debug logs for all major operations with timing information
     */
    public void recordError(String operation, String errorMessage, Exception exception) {
        errorCount.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        
        Log.e(TAG, String.format("ERROR in %s at %d: %s", operation, timestamp, errorMessage));
        if (exception != null) {
            Log.e(TAG, "Exception details:", exception);
        }
    }
    
    // ===== COMPREHENSIVE PERFORMANCE REPORTING =====
    
    /**
     * Generate comprehensive performance report
     * Requirements: 8.5 - Provide debug logs for all major operations with timing information
     */
    public void generatePerformanceReport() {
        long sessionDuration = System.currentTimeMillis() - sessionStartTime.get();
        
        Log.i(TAG, "=== COMPREHENSIVE PERFORMANCE REPORT ===");
        Log.i(TAG, String.format("Session Duration: %d minutes", sessionDuration / 60000));
        
        // Video transition performance
        int transitionCountValue = transitionCount.get();
        if (transitionCountValue > 0) {
            double avgTransition = getAverageTransitionTime();
            Log.i(TAG, String.format("Video Transitions: %d total, %.1fms average", 
                                    transitionCountValue, avgTransition));
        }
        
        // Cache performance
        logCachePerformance();
        
        // Memory performance
        Log.i(TAG, String.format("Memory: Current %dMB, Peak %dMB", 
                                getCurrentMemoryUsageMB(), getPeakMemoryUsageMB()));
        
        // Error count
        Log.i(TAG, String.format("Errors: %d total", errorCount.get()));
        
        Log.i(TAG, "=== END PERFORMANCE REPORT ===");
    }
    
    /**
     * Log operation timing with context
     * Requirements: 8.5 - Provide debug logs for all major operations with timing information
     */
    public void logOperationTiming(String operation, long startTime, String context) {
        long duration = System.currentTimeMillis() - startTime;
        Log.d(TAG, String.format("Operation '%s' completed in %dms [%s]", operation, duration, context));
        
        // Warning for slow operations
        if (duration > 100) {
            Log.w(TAG, String.format("SLOW OPERATION: '%s' took %dms [%s]", operation, duration, context));
        }
    }
    
    /**
     * Shutdown performance monitoring
     */
    public void shutdown() {
        if (memoryMonitorRunnable != null) {
            memoryHandler.removeCallbacks(memoryMonitorRunnable);
        }
        
        // Generate final report
        generatePerformanceReport();
        
        Log.i(TAG, "ReelPerformanceMonitor shutdown");
    }
}