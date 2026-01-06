package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Advanced Video Preload Manager - Preloads videos silently in background
 * Videos are preloaded and paused, ready to play instantly when needed
 * No UI blocking, no loading delays, smooth Instagram-like experience
 *
 * CRITICAL: All ExoPlayer operations happen on Main thread
 */
public class VideoPreloadManager {
    private static final String TAG = "VideoPreloadManager";
    
    // EMULATOR OPTIMIZATION: Reduce preload range on emulators to prevent OOM
    private static final int PRELOAD_RANGE_DEVICE = 4; // Videos to preload ahead/behind on real devices
    private static final int PRELOAD_RANGE_EMULATOR = 0; // DISABLED on emulators - on-demand only
    private static final int MAX_CACHED_VIDEOS_DEVICE = 9; // 1 current + 4 before + 4 after
    private static final int MAX_CACHED_VIDEOS_EMULATOR = 1; // Only current video on emulators
    
    // ADAPTIVE SETTINGS: Can be updated based on device performance
    private int PRELOAD_RANGE;
    private int MAX_CACHED_VIDEOS;
    private AdaptivePerformanceManager adaptivePerformanceManager;

    private final Context context;
    private final ExecutorService preloadExecutor;
    private final Map<String, PreloadedPlayerData> playerCache;
    private final DefaultDataSourceFactory dataSourceFactory;
    private final Handler mainHandler; // CRITICAL: For Main thread operations
    private final java.util.Set<String> problematicVideos; // Track videos with codec issues
    private List<ReelItem> reelItems;
    private int currentPosition = -1;
    private volatile boolean isPreloading = false;

    public VideoPreloadManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper()); // CRITICAL: Main thread handler
        this.problematicVideos = new java.util.HashSet<>(); // Track codec-problematic videos

        // ADAPTIVE OPTIMIZATION: Use device performance detection for optimal settings
        boolean isEmulator = isRunningOnEmulator();
        DevicePerformanceDetector.PerformanceLevel devicePerformance = DevicePerformanceDetector.detectPerformanceLevel(context);
        
        if (isEmulator) {
            this.PRELOAD_RANGE = PRELOAD_RANGE_EMULATOR;
            this.MAX_CACHED_VIDEOS = MAX_CACHED_VIDEOS_EMULATOR;
            Log.i(TAG, "Emulator detected - using minimal preload settings (range: " + PRELOAD_RANGE + ", cache: " + MAX_CACHED_VIDEOS + ")");
        } else {
            // Adaptive settings based on device performance
            switch (devicePerformance) {
                case HIGH_END:
                    this.PRELOAD_RANGE = PRELOAD_RANGE_DEVICE;
                    this.MAX_CACHED_VIDEOS = MAX_CACHED_VIDEOS_DEVICE;
                    break;
                case MID_RANGE:
                    this.PRELOAD_RANGE = 2; // Reduced for mid-range
                    this.MAX_CACHED_VIDEOS = 5; // Reduced cache
                    break;
                case LOW_END:
                default:
                    this.PRELOAD_RANGE = 1; // Minimal for low-end
                    this.MAX_CACHED_VIDEOS = 2; // Very small cache
                    break;
            }
            Log.i(TAG, String.format("%s device detected - using adaptive preload settings (range: %d, cache: %d)", 
                  devicePerformance.name(), PRELOAD_RANGE, MAX_CACHED_VIDEOS));
        }

        // Use 2 threads for faster parallel preloading
        this.preloadExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "VideoPreloadThread");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        // LinkedHashMap to track access order for LRU cache
        this.playerCache = new LinkedHashMap<String, PreloadedPlayerData>(MAX_CACHED_VIDEOS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                if (size() > MAX_CACHED_VIDEOS) {
                    PreloadedPlayerData data = (PreloadedPlayerData) eldest.getValue();
                    if (data != null && data.player != null) {
                        try {
                            data.player.release();
                            Log.d(TAG, "Released cached video: " + eldest.getKey());
                        } catch (Exception e) {
                            Log.e(TAG, "Error releasing player", e);
                        }
                    }
                    return true;
                }
                return false;
            }
        };
        this.dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
    }

    public void setReelItems(List<ReelItem> reelItems) {
        this.reelItems = reelItems;
    }

    /**
     * Update preload position - called when user scrolls
     * CRITICAL: This must preload videos IMMEDIATELY for smooth playback
     * OPTIMIZED: Includes memory pressure detection for emulators
     */
    public void updateCurrentPosition(int position) {
        if (reelItems == null || reelItems.isEmpty()) {
            return;
        }

        // MEMORY OPTIMIZATION: Check memory pressure before preloading
        if (isMemoryPressureHigh()) {
            Log.w(TAG, "High memory pressure detected - performing cleanup before preload");
            performEmergencyCleanup();
        }

        Log.d(TAG, "Updated position to: " + position + ", starting PRIORITY preload...");
        preloadVideosAround(position);
    }
    
    /**
     * Detect high memory pressure
     */
    private boolean isMemoryPressureHigh() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        // Consider memory pressure high if using more than 85% of max heap
        double memoryUsagePercent = (double) usedMemory / maxMemory;
        boolean highPressure = memoryUsagePercent > 0.85;
        
        if (highPressure) {
            Log.w(TAG, String.format("High memory pressure: %.1f%% used (%dMB/%dMB)", 
                  memoryUsagePercent * 100, usedMemory / (1024 * 1024), maxMemory / (1024 * 1024)));
        }
        
        return highPressure;
    }
    
    /**
     * Emergency cleanup when memory pressure is high
     */
    private void performEmergencyCleanup() {
        int initialSize = playerCache.size();
        
        // Release half of the cached players, keeping only the most recent ones
        int targetSize = Math.max(1, playerCache.size() / 2);
        
        mainHandler.post(() -> {
            try {
                java.util.Iterator<Map.Entry<String, PreloadedPlayerData>> iterator = playerCache.entrySet().iterator();
                int removed = 0;
                
                while (iterator.hasNext() && playerCache.size() > targetSize) {
                    Map.Entry<String, PreloadedPlayerData> entry = iterator.next();
                    PreloadedPlayerData data = entry.getValue();
                    
                    if (data != null && data.player != null) {
                        data.player.release();
                        removed++;
                    }
                    iterator.remove();
                }
                
                // Force garbage collection
                System.gc();
                
                Log.i(TAG, String.format("Emergency cleanup completed: released %d players (%d → %d)", 
                      removed, initialSize, playerCache.size()));
                
            } catch (Exception e) {
                Log.e(TAG, "Error during emergency cleanup", e);
            }
        });
    }

    /**
     * Preload videos in range: current (FIRST PRIORITY), then alternating ahead/behind
     * CRITICAL: This method ensures videos are preloaded for BOTH forward and backward scrolling
     * OPTIMIZED: Removed Thread.sleep() - preloads execute asynchronously without blocking
     * OPTIMIZED FOR BACKWARD SCROLL: Backward preloading now has EQUAL priority to forward
     */
    private void preloadVideosAround(int position) {
        preloadExecutor.execute(() -> {
            try {
                Log.d(TAG, "Starting preload sequence for position: " + position);

                // HIGHEST PRIORITY: Preload current position FIRST and IMMEDIATELY
                // This is critical for both forward and backward scroll
                if (position >= 0 && position < reelItems.size()) {
                    preloadAt(position);
                }

                // OPTIMIZATION FOR BACKWARD SCROLL: Preload forward AND backward in parallel
                // This ensures backward videos are ready just as fast as forward videos
                for (int i = 1; i <= PRELOAD_RANGE; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "Preload interrupted");
                        return;
                    }
                    
                    // Preload forward (ahead) - PRIORITY 2A
                    if (position + i < reelItems.size()) {
                        final int targetPosForward = position + i;
                        preloadExecutor.execute(() -> {
                            Log.d(TAG, "Preloading ahead: position " + targetPosForward);
                            preloadAt(targetPosForward);
                        });
                    }
                    
                    // Preload backward (behind) - PRIORITY 2B (NOW EQUAL PRIORITY!)
                    // This is crucial for smooth backward scrolling
                    if (position - i >= 0) {
                        final int targetPosBackward = position - i;
                        preloadExecutor.execute(() -> {
                            Log.d(TAG, "Preloading behind: position " + targetPosBackward);
                            preloadAt(targetPosBackward);
                        });
                    }
                }

                Log.d(TAG, "✓ Preload sequence queued for position: " + position);

            } catch (Exception e) {
                Log.e(TAG, "Error in preload sequence: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Preload a single video at position
     * BLOCKING: This method waits for preparation to complete
     * CRITICAL: All ExoPlayer operations happen on Main thread
     * EMULATOR OPTIMIZATION: Uses reduced buffer sizes and quality settings
     */
    private void preloadAt(int position) {
        if (position < 0 || position >= reelItems.size()) {
            return;
        }

        ReelItem item = reelItems.get(position);
        String videoUrl = item.getVideoUrl();

        // Skip if no valid URL
        if (videoUrl == null || videoUrl.isEmpty() || videoUrl.equals(item.getVideoId())) {
            Log.w(TAG, "Skipping video at position " + position + ": No valid URL");
            return;
        }

        String videoId = item.getVideoId();

        // Skip if this video has known codec issues
        if (problematicVideos.contains(videoId)) {
            Log.w(TAG, "Skipping problematic video: " + videoId + " (known codec issues)");
            return;
        }

        // Already cached? Skip
        if (playerCache.containsKey(videoId)) {
            Log.d(TAG, "Video already cached: " + videoId);
            return;
        }

        try {
            Log.d(TAG, "Starting PRELOAD for video: " + videoId + " at position: " + position);

            // CRITICAL: Create player and prepare on MAIN thread only
            mainHandler.post(() -> {
                try {
                    // ADAPTIVE OPTIMIZATION: Use performance-based codec and buffer settings
                    ExoPlayer player;
                    boolean isEmulator = isRunningOnEmulator();
                    boolean isLowEndDevice = DevicePerformanceDetector.isLowEndDevice(context);
                    
                    if (isEmulator || isLowEndDevice) {
                        Log.d(TAG, "Low-performance device detected - creating player with software codec and reduced buffers for: " + videoId);
                        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                            .setMediaCodecSelector(new SoftwareCodecSelector());
                        
                        // Use adaptive performance manager if available
                        if (adaptivePerformanceManager != null) {
                            player = new ExoPlayer.Builder(context)
                                .setRenderersFactory(renderersFactory)
                                .setLoadControl(adaptivePerformanceManager.createOptimizedLoadControl())
                                .setTrackSelector(adaptivePerformanceManager.createAdaptiveTrackSelector())
                                .build();
                        } else {
                            player = new ExoPlayer.Builder(context)
                                .setRenderersFactory(renderersFactory)
                                .setLoadControl(createEmulatorOptimizedLoadControl())
                                .build();
                        }
                    } else {
                        Log.d(TAG, "High-performance device detected - creating player with hardware codec for: " + videoId);
                        
                        // Use adaptive performance manager if available
                        if (adaptivePerformanceManager != null) {
                            player = new ExoPlayer.Builder(context)
                                .setLoadControl(adaptivePerformanceManager.createOptimizedLoadControl())
                                .setTrackSelector(adaptivePerformanceManager.createAdaptiveTrackSelector())
                                .build();
                        } else {
                            player = new ExoPlayer.Builder(context).build();
                        }
                    }

                    // Load media
                    MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                    MediaSource source = createMediaSource(videoUrl, mediaItem);

                    player.setMediaSource(source);
                    player.setPlayWhenReady(false); // IMPORTANT: Keep paused
                    player.prepare();

                    // Wait for player to be ready before caching
                    waitForPlayerReady(player, videoId, position);

                } catch (Exception e) {
                    Log.e(TAG, "Error preloading video at position " + position + ": " + e.getMessage(), e);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error scheduling preload for video at position " + position + ": " + e.getMessage(), e);
        }
    }

    /**
     * Wait for player to reach STATE_READY in background, then cache it
     */
    private void waitForPlayerReady(ExoPlayer player, String videoId, int position) {
        // Add a listener to track when the player is ready
        // This callback happens on the Main thread automatically
        player.addListener(new Player.Listener() {
            private boolean cached = false;

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY && !cached) {
                    cached = true;
                    Log.d(TAG, "✓ PRELOADED AND READY: " + videoId + " at position: " + position);
                    playerCache.put(videoId, new PreloadedPlayerData(player, position));
                    // Remove this listener to stop tracking
                    player.removeListener(this);
                }
            }

            @Override
            public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
                if (!cached) {
                    cached = true;
                    Log.e(TAG, "Player error while preloading: " + error);
                    
                    // Enhanced error handling for codec issues
                    if (isCodecError(error)) {
                        Log.w(TAG, "Codec error detected, attempting fallback for: " + videoId);
                        attemptFallbackPreload(player, videoId, position);
                    } else {
                        // For non-codec errors, just release and continue
                        mainHandler.post(player::release);
                    }
                    
                    player.removeListener(this);
                }
            }
            
            private boolean isCodecError(com.google.android.exoplayer2.PlaybackException error) {
                String errorMessage = error.getMessage();
                return errorMessage != null && (
                    errorMessage.contains("Decoder init failed") ||
                    errorMessage.contains("MediaCodec") ||
                    errorMessage.contains("codec") ||
                    errorMessage.contains("goldfish") ||
                    error.errorCode == com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                );
            }
            
            private void attemptFallbackPreload(ExoPlayer failedPlayer, String videoId, int position) {
                try {
                    Log.i(TAG, "Attempting codec fallback for video: " + videoId);
                    
                    // Release the failed player
                    mainHandler.post(failedPlayer::release);
                    
                    // Try with a more compatible configuration
                    mainHandler.post(() -> {
                        try {
                            // Create new player with software-only codec configuration for emulator compatibility
                            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                                .setMediaCodecSelector(new SoftwareCodecSelector());
                            
                            ExoPlayer fallbackPlayer = new ExoPlayer.Builder(context)
                                .setVideoScalingMode(com.google.android.exoplayer2.C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                .setRenderersFactory(renderersFactory)
                                .build();
                            
                            // Use progressive source as fallback (more compatible)
                            String videoUrl = reelItems.get(position).getVideoUrl();
                            MediaItem mediaItem = MediaItem.fromUri(videoUrl);
                            MediaSource fallbackSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem);
                            
                            fallbackPlayer.setMediaSource(fallbackSource);
                            fallbackPlayer.setPlayWhenReady(false);
                            fallbackPlayer.prepare();
                            
                            // Add listener for fallback attempt
                            fallbackPlayer.addListener(new Player.Listener() {
                                private boolean fallbackCached = false;
                                
                                @Override
                                public void onPlaybackStateChanged(int playbackState) {
                                    if (playbackState == Player.STATE_READY && !fallbackCached) {
                                        fallbackCached = true;
                                        Log.i(TAG, "✓ FALLBACK PRELOAD SUCCESS: " + videoId);
                                        playerCache.put(videoId, new PreloadedPlayerData(fallbackPlayer, position));
                                        fallbackPlayer.removeListener(this);
                                    }
                                }
                                
                                @Override
                                public void onPlayerError(com.google.android.exoplayer2.PlaybackException fallbackError) {
                                    if (!fallbackCached) {
                                        fallbackCached = true;
                                        Log.w(TAG, "✗ FALLBACK ALSO FAILED for: " + videoId + ", error: " + fallbackError.getMessage());
                                        mainHandler.post(fallbackPlayer::release);
                                        fallbackPlayer.removeListener(this);
                                        
                                        // Mark this video as problematic to avoid repeated attempts
                                        markVideoAsProblematic(videoId);
                                    }
                                }
                            });
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating fallback player for: " + videoId, e);
                        }
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in fallback attempt for: " + videoId, e);
                }
            }
        });

        // OPTIMIZATION: Reduced timeout from 15s to 10s - faster cleanup of failed preloads
        mainHandler.postDelayed(() -> {
            if (!playerCache.containsKey(videoId)) {
                Log.w(TAG, "Timeout waiting for player STATE_READY: " + videoId);
                mainHandler.post(player::release);
            }
        }, 10000); // 10 second timeout (reduced from 15s)
    }

    /**
     * Create appropriate media source
     */
    private MediaSource createMediaSource(String videoUrl, MediaItem mediaItem) {
        try {
            String urlLower = videoUrl.toLowerCase();

            // HLS format
            if (urlLower.contains(".m3u8") || urlLower.contains("hls")) {
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem);
            }

            // Progressive (MP4, etc)
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);

        } catch (Exception e) {
            Log.e(TAG, "Error creating media source, falling back to progressive", e);
            return new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);
        }
    }

    /**
     * Create emulator-optimized load control with reduced buffer sizes
     * EMULATOR OPTIMIZATION: Reduces memory usage by limiting buffer sizes
     */
    private com.google.android.exoplayer2.LoadControl createEmulatorOptimizedLoadControl() {
        try {
            // EMULATOR OPTIMIZATION: Significantly reduced buffer sizes to prevent OOM
            return new com.google.android.exoplayer2.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    2000,   // minBufferMs: 2 seconds (reduced from default 50s)
                    5000,   // maxBufferMs: 5 seconds (reduced from default 50s)  
                    1000,   // bufferForPlaybackMs: 1 second (reduced from default 2.5s)
                    1000    // bufferForPlaybackAfterRebufferMs: 1 second (reduced from default 5s)
                )
                .setTargetBufferBytes(1024 * 1024) // maxBufferBytes: 1MB (reduced from default)
                .setPrioritizeTimeOverSizeThresholds(true) // Prioritize time-based limits
                .build();
        } catch (Exception e) {
            Log.e(TAG, "Error creating emulator load control, using default", e);
            return new com.google.android.exoplayer2.DefaultLoadControl();
        }
    }

    /**
     * Get preloaded and paused player - ready to attach to PlayerView
     * Production-ready: Includes validation and logging
     */
    public ExoPlayer getPreloadedPlayer(String videoId) {
        PreloadedPlayerData data = playerCache.get(videoId);

        if (data == null) {
            Log.w(TAG, "✗ NO preloaded player found for: " + videoId + " (will load on demand)");
            return null;
        }

        ExoPlayer player = data.player;
        if (player == null) {
            Log.w(TAG, "✗ Cached player is null for: " + videoId);
            return null;
        }

        // Validate player is in a usable state
        try {
            int playbackState = player.getPlaybackState();
            long duration = player.getDuration();

            if (playbackState == Player.STATE_READY && duration > 0) {
                Log.d(TAG, "✓ Returning READY preloaded player for: " + videoId +
                      " (duration: " + duration + "ms)");
                return player;
            } else {
                Log.w(TAG, "⚠ Preloaded player not ready for: " + videoId +
                      " (state: " + playbackState + ", duration: " + duration + ")");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error validating preloaded player for: " + videoId + ", error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Release all cached players
     */
    public void releaseAll() {
        mainHandler.post(() -> {
            for (Map.Entry<String, PreloadedPlayerData> entry : playerCache.entrySet()) {
                try {
                    if (entry.getValue() != null && entry.getValue().player != null) {
                        entry.getValue().player.release();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing player", e);
                }
            }
            playerCache.clear();
            Log.d(TAG, "Released all preloaded videos");
        });
    }

    /**
     * Shutdown manager
     */
    public void shutdown() {
        releaseAll();
        preloadExecutor.shutdown();
    }

    /**
     * Update adaptive settings based on performance manager
     * CRITICAL: Allows dynamic adjustment of preload settings to prevent lag
     */
    public void updateAdaptiveSettings(AdaptivePerformanceManager performanceManager) {
        if (performanceManager != null) {
            this.adaptivePerformanceManager = performanceManager;
            
            // Update preload range based on current performance
            int newPreloadRange = performanceManager.getCurrentPreloadRange();
            if (newPreloadRange != this.PRELOAD_RANGE) {
                this.PRELOAD_RANGE = newPreloadRange;
                Log.i(TAG, "Updated preload range to: " + PRELOAD_RANGE);
            }
            
            // Update cache size based on current performance
            int newCacheSize = performanceManager.getCurrentCacheSize();
            if (newCacheSize != this.MAX_CACHED_VIDEOS) {
                this.MAX_CACHED_VIDEOS = newCacheSize;
                Log.i(TAG, "Updated cache size to: " + MAX_CACHED_VIDEOS);
                
                // Clean up excess cached videos if needed
                if (playerCache.size() > MAX_CACHED_VIDEOS) {
                    performEmergencyCleanup();
                }
            }
        }
    }

    /**
     * Mark a video as having codec issues to avoid repeated failed attempts
     */
    private void markVideoAsProblematic(String videoId) {
        problematicVideos.add(videoId);
        Log.w(TAG, "Marked video as problematic: " + videoId + " (total problematic: " + problematicVideos.size() + ")");
    }

    /**
     * Detect if running on Android emulator
     * Uses multiple detection methods for reliability
     */
    private boolean isRunningOnEmulator() {
        return (android.os.Build.BRAND.startsWith("generic") && android.os.Build.DEVICE.startsWith("generic"))
                || android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk_google")
                || android.os.Build.PRODUCT.contains("google_sdk")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.PRODUCT.contains("sdk_x86")
                || android.os.Build.PRODUCT.contains("vbox86p")
                || android.os.Build.PRODUCT.contains("emulator")
                || android.os.Build.PRODUCT.contains("simulator");
    }

    /**
     * Data class for preloaded player
     */
    private static class PreloadedPlayerData {
        ExoPlayer player;
        int position;
        long timestamp;

        PreloadedPlayerData(ExoPlayer player, int position) {
            this.player = player;
            this.position = position;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Software-only codec selector for emulator compatibility
     * Forces software decoding when hardware codecs fail
     */
    private static class SoftwareCodecSelector implements MediaCodecSelector {
        @Override
        public List<MediaCodecInfo> getDecoderInfos(String mimeType, boolean requiresSecureDecoder, boolean requiresTunnelingDecoder) {
            try {
                List<MediaCodecInfo> allCodecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
                List<MediaCodecInfo> softwareCodecs = new ArrayList<>();
                
                // Prefer software codecs for emulator compatibility
                for (MediaCodecInfo codecInfo : allCodecs) {
                    if (codecInfo.name.contains("software") || 
                        codecInfo.name.contains("google") ||
                        codecInfo.name.contains("ffmpeg") ||
                        !codecInfo.hardwareAccelerated) {
                        softwareCodecs.add(codecInfo);
                    }
                }
                
                // If no software codecs found, return all codecs as fallback
                return softwareCodecs.isEmpty() ? allCodecs : softwareCodecs;
            } catch (MediaCodecUtil.DecoderQueryException e) {
                Log.e(TAG, "Error querying decoders, using default selector", e);
                return new ArrayList<>();
            }
        }
    }
}
