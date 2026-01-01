package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LruCache;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ThumbnailManager - Extracts and caches video thumbnails (first frame at 1 second)
 * Pre-generates thumbnails at startup to eliminate black screens
 * Uses both memory cache (LruCache) and Glide's disk cache for efficiency
 */
public class ThumbnailManager {
    private static final String TAG = "ThumbnailManager";
    
    // Memory cache for thumbnails (50MB max)
    private final LruCache<String, Bitmap> thumbnailCache;
    private final ExecutorService thumbnailExecutor;
    private final Handler mainHandler;
    private final Context context;
    
    public ThumbnailManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Initialize memory cache (50MB)
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8; // Use 1/8th of available memory (50MB for 400MB device)
        
        this.thumbnailCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // Return size in KB
                return bitmap.getByteCount() / 1024;
            }
        };
        
        // Use 2 threads for parallel thumbnail extraction
        this.thumbnailExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "ThumbnailExtractor");
            t.setPriority(Thread.NORM_PRIORITY - 1); // Lower priority than UI
            return t;
        });
        
        Log.d(TAG, "ThumbnailManager initialized with cache size: " + cacheSize + "KB");
    }
    
    /**
     * Pre-generate thumbnails for all reel items at startup
     * This eliminates black screens during scrolling
     */
    public void preloadThumbnails(List<ReelItem> reelItems) {
        if (reelItems == null || reelItems.isEmpty()) {
            return;
        }
        
        Log.d(TAG, "Preloading thumbnails for " + reelItems.size() + " videos");
        
        thumbnailExecutor.execute(() -> {
            for (ReelItem item : reelItems) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                
                String videoId = item.getVideoId();
                String videoUrl = item.getVideoUrl();
                
                // Skip if already cached or no URL
                if (videoId == null || videoUrl == null || videoUrl.isEmpty() || videoUrl.equals(videoId)) {
                    continue;
                }
                
                // Check if already in cache
                if (thumbnailCache.get(videoId) != null) {
                    continue;
                }
                
                // Extract thumbnail in background
                extractThumbnail(videoId, videoUrl);
            }
            
            Log.d(TAG, "Thumbnail preloading complete");
        });
    }
    
    /**
     * Extract thumbnail from video (first frame at 1 second)
     * Uses MediaMetadataRetriever for reliable extraction
     */
    private void extractThumbnail(String videoId, String videoUrl) {
        MediaMetadataRetriever retriever = null;
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(videoUrl);
            
            // Extract frame at 1 second (1,000,000 microseconds)
            Bitmap thumbnail = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            
            // Fallback to first frame if 1 second frame not available
            if (thumbnail == null) {
                thumbnail = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            
            // Fallback to any available frame
            if (thumbnail == null) {
                thumbnail = retriever.getFrameAtTime();
            }
            
            if (thumbnail != null) {
                // Scale down thumbnail to reduce memory usage (max 720p width)
                int maxWidth = 720;
                if (thumbnail.getWidth() > maxWidth) {
                    float scale = (float) maxWidth / thumbnail.getWidth();
                    int newHeight = Math.round(thumbnail.getHeight() * scale);
                    Bitmap scaledThumbnail = Bitmap.createScaledBitmap(thumbnail, maxWidth, newHeight, true);
                    thumbnail.recycle();
                    thumbnail = scaledThumbnail;
                }
                
                // Cache the thumbnail
                thumbnailCache.put(videoId, thumbnail);
                Log.d(TAG, "Extracted thumbnail for: " + videoId + " (size: " + thumbnail.getWidth() + "x" + thumbnail.getHeight() + ")");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error extracting thumbnail for " + videoId + ": " + e.getMessage());
        } finally {
            if (retriever != null) {
                try {
                    retriever.release();
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MediaMetadataRetriever: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Get thumbnail request builder for Glide
     * Uses Glide's built-in video thumbnail support with disk caching
     */
    public RequestBuilder<Bitmap> getThumbnailRequest(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return null;
        }
        
        return Glide.with(context)
                .asBitmap()
                .load(videoUrl)
                .frame(1000000) // Extract frame at 1 second (microseconds)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache to disk
                .override(720, 1280) // Limit size for memory efficiency
                .centerCrop()
                .error(android.R.color.black); // Black fallback if thumbnail fails
    }
    
    /**
     * Get cached thumbnail from memory cache
     */
    public Bitmap getCachedThumbnail(String videoId) {
        return thumbnailCache.get(videoId);
    }
    
    /**
     * Check if thumbnail is cached
     */
    public boolean hasCachedThumbnail(String videoId) {
        return thumbnailCache.get(videoId) != null;
    }
    
    /**
     * Clear all cached thumbnails
     */
    public void clearCache() {
        thumbnailCache.evictAll();
        Log.d(TAG, "Thumbnail cache cleared");
    }
    
    /**
     * Shutdown thumbnail manager
     */
    public void shutdown() {
        thumbnailExecutor.shutdown();
        clearCache();
        Log.d(TAG, "ThumbnailManager shutdown");
    }
}

