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
    private static final int PRELOAD_RANGE = 3; // Videos to preload ahead/behind
    private static final int MAX_CACHED_VIDEOS = 7; // 1 current + 3 before + 3 after

    private final Context context;
    private final ExecutorService preloadExecutor;
    private final Map<String, PreloadedPlayerData> playerCache;
    private final DefaultDataSourceFactory dataSourceFactory;
    private final Handler mainHandler; // CRITICAL: For Main thread operations
    private List<ReelItem> reelItems;
    private int currentPosition = -1;
    private volatile boolean isPreloading = false;

    public VideoPreloadManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper()); // CRITICAL: Main thread handler

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
     */
    public void updateCurrentPosition(int position) {
        if (reelItems == null || reelItems.isEmpty()) {
            return;
        }

        Log.d(TAG, "Updated position to: " + position + ", starting PRIORITY preload...");
        preloadVideosAround(position);
    }

    /**
     * Preload videos in range: current (FIRST PRIORITY), then alternating ahead/behind
     * CRITICAL: This method ensures videos are preloaded for BOTH forward and backward scrolling
     * OPTIMIZED: Removed Thread.sleep() - preloads execute asynchronously without blocking
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

                // PRIORITY 2: Preload videos AHEAD (forward direction) - submit all at once
                for (int i = 1; i <= PRELOAD_RANGE; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "Preload interrupted");
                        return;
                    }
                    if (position + i < reelItems.size()) {
                        final int targetPos = position + i;
                        preloadExecutor.execute(() -> {
                            Log.d(TAG, "Preloading ahead: position " + targetPos);
                            preloadAt(targetPos);
                        });
                    }
                }

                // PRIORITY 3: Preload videos BEHIND (backward direction) - submit all at once
                // This is crucial for smooth backward scrolling
                for (int i = 1; i <= PRELOAD_RANGE; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        Log.d(TAG, "Preload interrupted");
                        return;
                    }
                    if (position - i >= 0) {
                        final int targetPos = position - i;
                        preloadExecutor.execute(() -> {
                            Log.d(TAG, "Preloading behind: position " + targetPos);
                            preloadAt(targetPos);
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
                    // Create new ExoPlayer for this video
                    ExoPlayer player = new ExoPlayer.Builder(context).build();

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
                    mainHandler.post(player::release);
                    player.removeListener(this);
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
}
