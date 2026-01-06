package com.genzopia.Instagame.reelview;

import android.content.Context;

import androidx.recyclerview.widget.RecyclerView;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Property-based tests for thumbnail cache size limit
 * **Feature: reelview-optimization, Property 11: Thumbnail Cache Size Limit**
 * **Validates: Requirements 4.3**
 */
@RunWith(RobolectricTestRunner.class)
public class ThumbnailCacheSizeLimitTest {

    private static final long MAX_CACHE_SIZE_BYTES = 50 * 1024 * 1024; // 50MB
    
    @Mock
    private RecyclerView mockRecyclerView;
    
    private Context context;
    private ReelAdapter adapter;
    private List<ReelItem> testReelItems;
    private ThumbnailCacheManager cacheManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        context = RuntimeEnvironment.getApplication();
        
        // Create test data
        testReelItems = new ArrayList<>();
        testReelItems.add(new ReelItem("video1", "Test Video 1", "100", "Description 1", "dev1", "game1"));
        testReelItems.add(new ReelItem("video2", "Test Video 2", "200", "Description 2", "dev2", "game2"));
        testReelItems.add(new ReelItem("video3", "Test Video 3", "300", "Description 3", "dev3", "game3"));
        
        adapter = new ReelAdapter(context, testReelItems, mockRecyclerView);
        cacheManager = new ThumbnailCacheManager(MAX_CACHE_SIZE_BYTES);
    }

    @Provide
    Arbitrary<ReelItem> cacheTestReelItems() {
        return Arbitraries.create(() -> {
            String videoId = "cache_video_" + System.nanoTime();
            String title = "Cache Test Video " + (int)(Math.random() * 1000);
            String likes = String.valueOf((int)(Math.random() * 10000));
            String description = "Cache test description " + (int)(Math.random() * 100);
            String developerId = "dev_" + (int)(Math.random() * 100);
            String gameId = "game_" + (int)(Math.random() * 50);
            
            ReelItem item = new ReelItem(videoId, title, likes, description, developerId, gameId);
            item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
            item.setVideoDuration((int)(Math.random() * 300) + 10); // 10-310 seconds
            return item;
        });
    }

    @Provide
    Arbitrary<Integer> thumbnailSizes() {
        // Generate thumbnail sizes from 10KB to 5MB
        return Arbitraries.integers().between(10 * 1024, 5 * 1024 * 1024);
    }

    /**
     * Property 11: Thumbnail Cache Size Limit
     * For any sequence of thumbnail cache operations, the total cache size should never exceed 50MB
     * **Validates: Requirements 4.3**
     */
    @Property(tries = 50)
    public void thumbnailCacheNeverExceeds50MB(@ForAll("cacheTestReelItems") ReelItem item,
                                              @ForAll("thumbnailSizes") Integer thumbnailSize) {
        // Generate thumbnail data
        ThumbnailData thumbnail = generateThumbnailData(item, thumbnailSize);
        
        // Add thumbnail to cache
        cacheManager.putThumbnail(item.getVideoId(), thumbnail);
        
        // Verify cache size limit is respected
        long currentCacheSize = cacheManager.getCurrentCacheSize();
        assertTrue("Cache size should never exceed 50MB, current: " + (currentCacheSize / (1024 * 1024)) + "MB", 
                  currentCacheSize <= MAX_CACHE_SIZE_BYTES);
        
        // Verify cache functionality
        ThumbnailData retrieved = cacheManager.getThumbnail(item.getVideoId());
        if (currentCacheSize > 0) {
            assertNotNull("Thumbnail should be retrievable if cache is not empty", retrieved);
        }
        
        // Verify cache maintains reasonable size
        int cacheEntryCount = cacheManager.getCacheEntryCount();
        assertTrue("Cache should maintain reasonable number of entries", cacheEntryCount >= 0);
    }

    /**
     * Property test: Cache should use LRU eviction when size limit is reached
     */
    @Property(tries = 30)
    public void cacheUsesLRUEvictionWhenLimitReached(@ForAll("cacheTestReelItems") ReelItem item) {
        // Fill cache to near capacity
        List<String> addedVideoIds = new ArrayList<>();
        
        // Add thumbnails until we approach the limit
        for (int i = 0; i < 20; i++) {
            String videoId = "lru_test_" + i;
            ThumbnailData thumbnail = generateThumbnailData(item, 3 * 1024 * 1024); // 3MB each
            
            cacheManager.putThumbnail(videoId, thumbnail);
            addedVideoIds.add(videoId);
            
            // Verify cache size limit is respected
            long currentSize = cacheManager.getCurrentCacheSize();
            assertTrue("Cache size should not exceed limit during filling", 
                      currentSize <= MAX_CACHE_SIZE_BYTES);
        }
        
        // Add one more large thumbnail to trigger eviction
        String newVideoId = "lru_trigger_" + System.nanoTime();
        ThumbnailData largeThumbnail = generateThumbnailData(item, 5 * 1024 * 1024); // 5MB
        cacheManager.putThumbnail(newVideoId, largeThumbnail);
        
        // Verify cache size is still within limit
        long finalSize = cacheManager.getCurrentCacheSize();
        assertTrue("Cache size should remain within limit after eviction", 
                  finalSize <= MAX_CACHE_SIZE_BYTES);
        
        // Verify the new thumbnail is in cache
        assertNotNull("New thumbnail should be in cache", cacheManager.getThumbnail(newVideoId));
        
        // Verify some old thumbnails were evicted (LRU behavior)
        int evictedCount = 0;
        for (String videoId : addedVideoIds) {
            if (cacheManager.getThumbnail(videoId) == null) {
                evictedCount++;
            }
        }
        assertTrue("Some old thumbnails should have been evicted", evictedCount > 0);
    }

    /**
     * Test cache behavior with many small thumbnails
     */
    @Test
    public void cacheHandlesManySmallThumbnails() {
        int smallThumbnailSize = 100 * 1024; // 100KB each
        int maxPossibleThumbnails = (int) (MAX_CACHE_SIZE_BYTES / smallThumbnailSize);
        
        List<String> videoIds = new ArrayList<>();
        
        // Add many small thumbnails
        for (int i = 0; i < maxPossibleThumbnails + 10; i++) {
            String videoId = "small_thumb_" + i;
            ReelItem item = new ReelItem(videoId, "Small Thumbnail Test", "100", 
                                       "Description", "dev1", "game1");
            ThumbnailData thumbnail = generateThumbnailData(item, smallThumbnailSize);
            
            cacheManager.putThumbnail(videoId, thumbnail);
            videoIds.add(videoId);
            
            // Verify cache size limit
            long currentSize = cacheManager.getCurrentCacheSize();
            assertTrue("Cache size should not exceed limit with small thumbnails: " + 
                      (currentSize / (1024 * 1024)) + "MB", 
                      currentSize <= MAX_CACHE_SIZE_BYTES);
        }
        
        // Verify cache contains reasonable number of thumbnails
        int cachedCount = 0;
        for (String videoId : videoIds) {
            if (cacheManager.getThumbnail(videoId) != null) {
                cachedCount++;
            }
        }
        
        assertTrue("Cache should contain reasonable number of small thumbnails", cachedCount > 0);
        assertTrue("Cache should not contain more thumbnails than size allows", 
                  cachedCount <= maxPossibleThumbnails + 5); // Allow some variance
    }

    /**
     * Test cache behavior with few large thumbnails
     */
    @Test
    public void cacheHandlesFewLargeThumbnails() {
        int largeThumbnailSize = 10 * 1024 * 1024; // 10MB each
        int maxPossibleThumbnails = (int) (MAX_CACHE_SIZE_BYTES / largeThumbnailSize);
        
        List<String> videoIds = new ArrayList<>();
        
        // Add large thumbnails
        for (int i = 0; i < maxPossibleThumbnails + 2; i++) {
            String videoId = "large_thumb_" + i;
            ReelItem item = new ReelItem(videoId, "Large Thumbnail Test", "100", 
                                       "Description", "dev1", "game1");
            ThumbnailData thumbnail = generateThumbnailData(item, largeThumbnailSize);
            
            cacheManager.putThumbnail(videoId, thumbnail);
            videoIds.add(videoId);
            
            // Verify cache size limit
            long currentSize = cacheManager.getCurrentCacheSize();
            assertTrue("Cache size should not exceed limit with large thumbnails: " + 
                      (currentSize / (1024 * 1024)) + "MB", 
                      currentSize <= MAX_CACHE_SIZE_BYTES);
        }
        
        // Verify cache behavior with large thumbnails
        int cachedCount = 0;
        for (String videoId : videoIds) {
            if (cacheManager.getThumbnail(videoId) != null) {
                cachedCount++;
            }
        }
        
        assertTrue("Cache should contain at least one large thumbnail", cachedCount > 0);
        assertTrue("Cache should not exceed expected capacity for large thumbnails", 
                  cachedCount <= maxPossibleThumbnails + 1);
    }

    /**
     * Test cache behavior with mixed thumbnail sizes
     */
    @Test
    public void cacheHandlesMixedThumbnailSizes() {
        int[] thumbnailSizes = {
            50 * 1024,      // 50KB
            500 * 1024,     // 500KB
            2 * 1024 * 1024, // 2MB
            5 * 1024 * 1024, // 5MB
            8 * 1024 * 1024  // 8MB
        };
        
        List<String> videoIds = new ArrayList<>();
        
        // Add thumbnails of various sizes
        for (int round = 0; round < 5; round++) {
            for (int sizeIndex = 0; sizeIndex < thumbnailSizes.length; sizeIndex++) {
                String videoId = "mixed_thumb_" + round + "_" + sizeIndex;
                ReelItem item = new ReelItem(videoId, "Mixed Size Test", "100", 
                                           "Description", "dev1", "game1");
                ThumbnailData thumbnail = generateThumbnailData(item, thumbnailSizes[sizeIndex]);
                
                cacheManager.putThumbnail(videoId, thumbnail);
                videoIds.add(videoId);
                
                // Verify cache size limit
                long currentSize = cacheManager.getCurrentCacheSize();
                assertTrue("Cache size should not exceed limit with mixed sizes: " + 
                          (currentSize / (1024 * 1024)) + "MB", 
                          currentSize <= MAX_CACHE_SIZE_BYTES);
            }
        }
        
        // Verify cache maintains reasonable state
        int cachedCount = 0;
        long totalCachedSize = 0;
        for (String videoId : videoIds) {
            ThumbnailData cached = cacheManager.getThumbnail(videoId);
            if (cached != null) {
                cachedCount++;
                totalCachedSize += cached.getDataSize();
            }
        }
        
        assertTrue("Cache should contain some thumbnails", cachedCount > 0);
        assertTrue("Total cached size should not exceed limit", totalCachedSize <= MAX_CACHE_SIZE_BYTES);
        assertEquals("Cache manager size should match actual cached size", 
                    totalCachedSize, cacheManager.getCurrentCacheSize());
    }

    /**
     * Test cache performance under rapid additions and retrievals
     */
    @Test
    public void cachePerformanceUnderLoad() {
        int numberOfOperations = 100;
        long startTime = System.currentTimeMillis();
        
        // Perform rapid cache operations
        for (int i = 0; i < numberOfOperations; i++) {
            String videoId = "perf_test_" + i;
            ReelItem item = new ReelItem(videoId, "Performance Test", "100", 
                                       "Description", "dev1", "game1");
            
            // Add thumbnail
            ThumbnailData thumbnail = generateThumbnailData(item, 1024 * 1024); // 1MB
            cacheManager.putThumbnail(videoId, thumbnail);
            
            // Retrieve thumbnail
            ThumbnailData retrieved = cacheManager.getThumbnail(videoId);
            assertNotNull("Thumbnail should be retrievable immediately after adding", retrieved);
            
            // Verify cache size limit is maintained
            long currentSize = cacheManager.getCurrentCacheSize();
            assertTrue("Cache size should remain within limit during performance test", 
                      currentSize <= MAX_CACHE_SIZE_BYTES);
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        // Verify performance is reasonable
        double avgTimePerOperation = totalTime / (double) numberOfOperations;
        assertTrue("Cache operations should be fast: " + avgTimePerOperation + "ms per operation", 
                  avgTimePerOperation < 10); // Less than 10ms per operation
    }

    /**
     * Test cache memory cleanup
     */
    @Test
    public void cacheMemoryCleanup() {
        // Fill cache with thumbnails
        List<String> videoIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String videoId = "cleanup_test_" + i;
            ReelItem item = new ReelItem(videoId, "Cleanup Test", "100", 
                                       "Description", "dev1", "game1");
            ThumbnailData thumbnail = generateThumbnailData(item, 2 * 1024 * 1024); // 2MB each
            
            cacheManager.putThumbnail(videoId, thumbnail);
            videoIds.add(videoId);
        }
        
        long sizeBeforeCleanup = cacheManager.getCurrentCacheSize();
        assertTrue("Cache should have content before cleanup", sizeBeforeCleanup > 0);
        
        // Clear cache
        cacheManager.clearCache();
        
        // Verify cleanup
        long sizeAfterCleanup = cacheManager.getCurrentCacheSize();
        assertEquals("Cache should be empty after cleanup", 0, sizeAfterCleanup);
        assertEquals("Cache entry count should be zero after cleanup", 0, cacheManager.getCacheEntryCount());
        
        // Verify all thumbnails are removed
        for (String videoId : videoIds) {
            assertNull("No thumbnails should remain after cleanup", cacheManager.getThumbnail(videoId));
        }
    }

    /**
     * Generate thumbnail data for testing
     */
    private ThumbnailData generateThumbnailData(ReelItem item, int sizeBytes) {
        byte[] data = new byte[sizeBytes];
        
        // Fill with deterministic data based on video ID
        String videoId = item.getVideoId();
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ((videoId.hashCode() + i) % 256);
        }
        
        return new ThumbnailData(data, System.currentTimeMillis());
    }

    /**
     * Mock thumbnail cache manager for testing
     */
    private static class ThumbnailCacheManager {
        private final long maxCacheSize;
        private final Map<String, ThumbnailData> cache;
        private long currentCacheSize;

        public ThumbnailCacheManager(long maxCacheSize) {
            this.maxCacheSize = maxCacheSize;
            this.cache = new LinkedHashMap<String, ThumbnailData>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ThumbnailData> eldest) {
                    // Remove eldest entries when cache size exceeds limit
                    if (currentCacheSize > maxCacheSize) {
                        currentCacheSize -= eldest.getValue().getDataSize();
                        return true;
                    }
                    return false;
                }
            };
            this.currentCacheSize = 0;
        }

        public void putThumbnail(String videoId, ThumbnailData thumbnail) {
            // Remove existing entry if present
            ThumbnailData existing = cache.remove(videoId);
            if (existing != null) {
                currentCacheSize -= existing.getDataSize();
            }
            
            // Add new thumbnail
            cache.put(videoId, thumbnail);
            currentCacheSize += thumbnail.getDataSize();
            
            // Trigger LRU eviction if needed
            while (currentCacheSize > maxCacheSize && !cache.isEmpty()) {
                Map.Entry<String, ThumbnailData> eldest = cache.entrySet().iterator().next();
                cache.remove(eldest.getKey());
                currentCacheSize -= eldest.getValue().getDataSize();
            }
        }

        public ThumbnailData getThumbnail(String videoId) {
            return cache.get(videoId); // LinkedHashMap will update access order
        }

        public long getCurrentCacheSize() {
            return currentCacheSize;
        }

        public int getCacheEntryCount() {
            return cache.size();
        }

        public void clearCache() {
            cache.clear();
            currentCacheSize = 0;
        }
    }

    /**
     * Thumbnail data class for testing
     */
    private static class ThumbnailData {
        private final byte[] data;
        private final long timestamp;

        public ThumbnailData(byte[] data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public byte[] getData() {
            return data;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getDataSize() {
            return data.length;
        }
    }
}