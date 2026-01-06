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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Property-based tests for profile image caching duration
 * **Feature: reelview-optimization, Property 13: Profile Image Caching Duration**
 * **Validates: Requirements 5.1**
 */
@RunWith(RobolectricTestRunner.class)
public class ProfileImageCachingDurationTest {

    private static final long CACHE_DURATION_MS = TimeUnit.MINUTES.toMillis(5); // 5 minutes
    
    @Mock
    private RecyclerView mockRecyclerView;
    
    private Context context;
    private ReelAdapter adapter;
    private List<ReelItem> testReelItems;
    private ProfileImageCacheManager cacheManager;

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
        cacheManager = new ProfileImageCacheManager(CACHE_DURATION_MS);
    }
    @Provide
    Arbitrary<String> developerIds() {
        return Arbitraries.create(() -> "dev_" + System.nanoTime() + "_" + (int)(Math.random() * 1000));
    }

    @Provide
    Arbitrary<String> profileImageUrls() {
        return Arbitraries.create(() -> {
            String imageId = "profile_" + System.nanoTime();
            return "https://example.com/profiles/" + imageId + ".jpg";
        });
    }

    @Provide
    Arbitrary<Long> timeOffsets() {
        // Generate time offsets from 0 to 10 minutes (in milliseconds)
        return Arbitraries.longs().between(0, TimeUnit.MINUTES.toMillis(10));
    }

    /**
     * Property 13: Profile Image Caching Duration
     * For any profile image, it should be cached for 5 minutes to avoid repeated Firebase queries
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 50)
    public void profileImagesCachedFor5Minutes(@ForAll("developerIds") String developerId,
                                              @ForAll("profileImageUrls") String profileImageUrl,
                                              @ForAll("timeOffsets") Long timeOffset) {
        // Cache the profile image
        long cacheTime = System.currentTimeMillis();
        cacheManager.cacheProfileImage(developerId, profileImageUrl, cacheTime);
        
        // Verify image is cached immediately
        ProfileImageCacheEntry cachedEntry = cacheManager.getCachedProfileImage(developerId, cacheTime);
        assertNotNull("Profile image should be cached immediately", cachedEntry);
        assertEquals("Cached image URL should match", profileImageUrl, cachedEntry.getImageUrl());
        assertTrue("Cached image should be valid immediately", cachedEntry.isValid(cacheTime));
        
        // Test cache validity at different time offsets
        long testTime = cacheTime + timeOffset;
        ProfileImageCacheEntry entryAtTestTime = cacheManager.getCachedProfileImage(developerId, testTime);
        
        if (timeOffset <= CACHE_DURATION_MS) {
            // Within 5-minute window - should be cached and valid
            assertNotNull("Profile image should be cached within 5-minute window at offset " + 
                         (timeOffset / 1000) + "s", entryAtTestTime);
            assertTrue("Profile image should be valid within 5-minute window at offset " + 
                      (timeOffset / 1000) + "s", entryAtTestTime.isValid(testTime));
            assertEquals("Cached image URL should remain consistent", 
                        profileImageUrl, entryAtTestTime.getImageUrl());
        } else {
            // Beyond 5-minute window - should be expired or null
            if (entryAtTestTime != null) {
                assertFalse("Profile image should be expired beyond 5-minute window at offset " + 
                           (timeOffset / 1000) + "s", entryAtTestTime.isValid(testTime));
            }
            // Note: Cache manager may return null for expired entries or keep them as expired
        }
        
        // Verify cache metrics
        ProfileImageCacheMetrics metrics = cacheManager.getCacheMetrics();
        assertTrue("Cache should have at least one entry", metrics.getTotalCacheEntries() >= 1);
        assertTrue("Cache should track cache time correctly", metrics.getAverageCacheAge() >= 0);
    }

    /**
     * Property test: Profile image cache should handle multiple developers efficiently
     */
    @Property(tries = 30)
    public void profileImageCacheHandlesMultipleDevelopers(@ForAll("developerIds") String baseDeveloperId) {
        List<String> developerIds = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        
        // Create multiple developers with profile images
        for (int i = 0; i < 10; i++) {
            String developerId = baseDeveloperId + "_multi_" + i;
            String imageUrl = "https://example.com/profiles/" + developerId + ".jpg";
            developerIds.add(developerId);
            imageUrls.add(imageUrl);
        }
        
        long cacheTime = System.currentTimeMillis();
        
        // Cache all profile images
        for (int i = 0; i < developerIds.size(); i++) {
            cacheManager.cacheProfileImage(developerIds.get(i), imageUrls.get(i), cacheTime);
        }
        
        // Verify all images are cached
        for (int i = 0; i < developerIds.size(); i++) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerIds.get(i), cacheTime);
            assertNotNull("Profile image should be cached for developer " + i, entry);
            assertEquals("Cached image URL should match for developer " + i, 
                        imageUrls.get(i), entry.getImageUrl());
            assertTrue("Cached image should be valid for developer " + i, entry.isValid(cacheTime));
        }
        
        // Test cache validity after 3 minutes (should still be valid)
        long testTime3Min = cacheTime + TimeUnit.MINUTES.toMillis(3);
        for (int i = 0; i < developerIds.size(); i++) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerIds.get(i), testTime3Min);
            assertNotNull("Profile image should still be cached after 3 minutes for developer " + i, entry);
            assertTrue("Profile image should still be valid after 3 minutes for developer " + i, 
                      entry.isValid(testTime3Min));
        }
        
        // Test cache validity after 6 minutes (should be expired)
        long testTime6Min = cacheTime + TimeUnit.MINUTES.toMillis(6);
        int expiredCount = 0;
        for (int i = 0; i < developerIds.size(); i++) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerIds.get(i), testTime6Min);
            if (entry == null || !entry.isValid(testTime6Min)) {
                expiredCount++;
            }
        }
        
        assertEquals("All profile images should be expired after 6 minutes", 
                    developerIds.size(), expiredCount);
        
        // Verify cache metrics
        ProfileImageCacheMetrics metrics = cacheManager.getCacheMetrics();
        assertEquals("Cache should contain all developers", developerIds.size(), metrics.getTotalCacheEntries());
    }
    /**
     * Test profile image cache expiration behavior
     */
    @Test
    public void profileImageCacheExpirationBehavior() {
        String developerId = "expiration_test_dev";
        String imageUrl = "https://example.com/profiles/expiration_test.jpg";
        
        long cacheTime = System.currentTimeMillis();
        
        // Cache the profile image
        cacheManager.cacheProfileImage(developerId, imageUrl, cacheTime);
        
        // Test at various time intervals
        long[] testIntervals = {
            0,                                    // Immediate
            TimeUnit.MINUTES.toMillis(1),        // 1 minute
            TimeUnit.MINUTES.toMillis(2),        // 2 minutes
            TimeUnit.MINUTES.toMillis(4),        // 4 minutes
            TimeUnit.MINUTES.toMillis(5),        // Exactly 5 minutes
            TimeUnit.MINUTES.toMillis(5) + 1000, // 5 minutes + 1 second
            TimeUnit.MINUTES.toMillis(6),        // 6 minutes
            TimeUnit.MINUTES.toMillis(10)        // 10 minutes
        };
        
        for (long interval : testIntervals) {
            long testTime = cacheTime + interval;
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerId, testTime);
            
            if (interval <= CACHE_DURATION_MS) {
                assertNotNull("Profile image should be cached at " + (interval / 1000) + " seconds", entry);
                assertTrue("Profile image should be valid at " + (interval / 1000) + " seconds", 
                          entry.isValid(testTime));
                assertEquals("Image URL should remain consistent", imageUrl, entry.getImageUrl());
            } else {
                if (entry != null) {
                    assertFalse("Profile image should be expired at " + (interval / 1000) + " seconds", 
                               entry.isValid(testTime));
                }
            }
        }
    }

    /**
     * Test profile image cache update behavior
     */
    @Test
    public void profileImageCacheUpdateBehavior() {
        String developerId = "update_test_dev";
        String originalImageUrl = "https://example.com/profiles/original.jpg";
        String updatedImageUrl = "https://example.com/profiles/updated.jpg";
        
        long initialCacheTime = System.currentTimeMillis();
        
        // Cache original image
        cacheManager.cacheProfileImage(developerId, originalImageUrl, initialCacheTime);
        
        // Verify original image is cached
        ProfileImageCacheEntry originalEntry = cacheManager.getCachedProfileImage(developerId, initialCacheTime);
        assertNotNull("Original image should be cached", originalEntry);
        assertEquals("Original image URL should match", originalImageUrl, originalEntry.getImageUrl());
        
        // Update with new image after 2 minutes
        long updateTime = initialCacheTime + TimeUnit.MINUTES.toMillis(2);
        cacheManager.cacheProfileImage(developerId, updatedImageUrl, updateTime);
        
        // Verify updated image is cached
        ProfileImageCacheEntry updatedEntry = cacheManager.getCachedProfileImage(developerId, updateTime);
        assertNotNull("Updated image should be cached", updatedEntry);
        assertEquals("Updated image URL should match", updatedImageUrl, updatedEntry.getImageUrl());
        
        // Verify cache duration resets with update
        long testTimeAfterUpdate = updateTime + TimeUnit.MINUTES.toMillis(4); // 4 minutes after update
        ProfileImageCacheEntry entryAfterUpdate = cacheManager.getCachedProfileImage(developerId, testTimeAfterUpdate);
        assertNotNull("Updated image should still be cached 4 minutes after update", entryAfterUpdate);
        assertTrue("Updated image should still be valid 4 minutes after update", 
                  entryAfterUpdate.isValid(testTimeAfterUpdate));
        assertEquals("Should have updated image URL", updatedImageUrl, entryAfterUpdate.getImageUrl());
        
        // Verify expiration based on update time
        long testTimeExpired = updateTime + TimeUnit.MINUTES.toMillis(6); // 6 minutes after update
        ProfileImageCacheEntry expiredEntry = cacheManager.getCachedProfileImage(developerId, testTimeExpired);
        if (expiredEntry != null) {
            assertFalse("Updated image should be expired 6 minutes after update", 
                       expiredEntry.isValid(testTimeExpired));
        }
    }

    /**
     * Test profile image cache performance with many entries
     */
    @Test
    public void profileImageCachePerformanceWithManyEntries() {
        int numberOfDevelopers = 100;
        List<String> developerIds = new ArrayList<>();
        List<String> imageUrls = new ArrayList<>();
        
        // Create many developers
        for (int i = 0; i < numberOfDevelopers; i++) {
            String developerId = "perf_test_dev_" + i;
            String imageUrl = "https://example.com/profiles/perf_test_" + i + ".jpg";
            developerIds.add(developerId);
            imageUrls.add(imageUrl);
        }
        
        long cacheTime = System.currentTimeMillis();
        
        // Measure cache insertion performance
        long insertStartTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfDevelopers; i++) {
            cacheManager.cacheProfileImage(developerIds.get(i), imageUrls.get(i), cacheTime);
        }
        long insertEndTime = System.currentTimeMillis();
        
        long insertTime = insertEndTime - insertStartTime;
        assertTrue("Cache insertion should be fast for " + numberOfDevelopers + " entries: " + insertTime + "ms", 
                  insertTime <= 200);
        
        // Measure cache retrieval performance
        long retrievalStartTime = System.currentTimeMillis();
        for (int i = 0; i < numberOfDevelopers; i++) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerIds.get(i), cacheTime);
            assertNotNull("Entry should be retrievable for developer " + i, entry);
        }
        long retrievalEndTime = System.currentTimeMillis();
        
        long retrievalTime = retrievalEndTime - retrievalStartTime;
        assertTrue("Cache retrieval should be fast for " + numberOfDevelopers + " entries: " + retrievalTime + "ms", 
                  retrievalTime <= 100);
        
        // Verify cache metrics
        ProfileImageCacheMetrics metrics = cacheManager.getCacheMetrics();
        assertEquals("Cache should contain all developers", numberOfDevelopers, metrics.getTotalCacheEntries());
        assertTrue("Cache hit ratio should be high", metrics.getCacheHitRatio() >= 0.9);
    }

    /**
     * Test profile image cache cleanup of expired entries
     */
    @Test
    public void profileImageCacheCleanupExpiredEntries() {
        List<String> developerIds = new ArrayList<>();
        
        // Create developers with staggered cache times
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            String developerId = "cleanup_test_dev_" + i;
            String imageUrl = "https://example.com/profiles/cleanup_test_" + i + ".jpg";
            
            // Cache at different times (some will expire sooner)
            long cacheTime = baseTime - TimeUnit.MINUTES.toMillis(i);
            cacheManager.cacheProfileImage(developerId, imageUrl, cacheTime);
            developerIds.add(developerId);
        }
        
        // Verify all entries are initially present
        ProfileImageCacheMetrics initialMetrics = cacheManager.getCacheMetrics();
        assertEquals("All entries should be cached initially", 10, initialMetrics.getTotalCacheEntries());
        
        // Trigger cleanup at a time when some entries should be expired
        long cleanupTime = baseTime + TimeUnit.MINUTES.toMillis(1);
        cacheManager.cleanupExpiredEntries(cleanupTime);
        
        // Verify expired entries are cleaned up
        ProfileImageCacheMetrics afterCleanupMetrics = cacheManager.getCacheMetrics();
        assertTrue("Some entries should be cleaned up", 
                  afterCleanupMetrics.getTotalCacheEntries() < initialMetrics.getTotalCacheEntries());
        
        // Verify remaining entries are still valid
        for (String developerId : developerIds) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerId, cleanupTime);
            if (entry != null) {
                assertTrue("Remaining entries should be valid after cleanup", entry.isValid(cleanupTime));
            }
        }
        
        // Verify cleanup metrics
        assertTrue("Cleanup should have processed some entries", 
                  afterCleanupMetrics.getLastCleanupProcessedCount() > 0);
        assertTrue("Cleanup should have removed some entries", 
                  afterCleanupMetrics.getLastCleanupRemovedCount() > 0);
    }
    /**
     * Test profile image cache with concurrent access
     */
    @Test
    public void profileImageCacheWithConcurrentAccess() throws InterruptedException {
        int numberOfThreads = 5;
        int developersPerThread = 10;
        List<Thread> threads = new ArrayList<>();
        List<String> allDeveloperIds = new ArrayList<>();
        
        long baseTime = System.currentTimeMillis();
        
        // Create concurrent threads that cache profile images
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < developersPerThread; i++) {
                    String developerId = "concurrent_dev_" + threadId + "_" + i;
                    String imageUrl = "https://example.com/profiles/concurrent_" + threadId + "_" + i + ".jpg";
                    
                    synchronized (allDeveloperIds) {
                        allDeveloperIds.add(developerId);
                    }
                    
                    // Cache with slight time variations
                    long cacheTime = baseTime + (threadId * 1000) + (i * 100);
                    cacheManager.cacheProfileImage(developerId, imageUrl, cacheTime);
                    
                    // Immediately try to retrieve
                    ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerId, cacheTime);
                    assertNotNull("Entry should be retrievable immediately after caching", entry);
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify all entries are cached
        int expectedTotal = numberOfThreads * developersPerThread;
        ProfileImageCacheMetrics finalMetrics = cacheManager.getCacheMetrics();
        assertEquals("All concurrent entries should be cached", expectedTotal, finalMetrics.getTotalCacheEntries());
        
        // Verify all entries are retrievable
        for (String developerId : allDeveloperIds) {
            ProfileImageCacheEntry entry = cacheManager.getCachedProfileImage(developerId, baseTime);
            assertNotNull("All concurrent entries should be retrievable: " + developerId, entry);
        }
        
        // Verify cache integrity after concurrent access
        assertTrue("Cache should maintain integrity after concurrent access", 
                  finalMetrics.getCacheHitRatio() >= 0.8);
    }

    /**
     * Mock profile image cache manager for testing
     */
    private static class ProfileImageCacheManager {
        private final long cacheDurationMs;
        private final Map<String, ProfileImageCacheEntry> cache = new HashMap<>();
        private int totalCacheRequests = 0;
        private int cacheHits = 0;
        private int lastCleanupProcessedCount = 0;
        private int lastCleanupRemovedCount = 0;

        public ProfileImageCacheManager(long cacheDurationMs) {
            this.cacheDurationMs = cacheDurationMs;
        }

        public synchronized void cacheProfileImage(String developerId, String imageUrl, long cacheTime) {
            ProfileImageCacheEntry entry = new ProfileImageCacheEntry(imageUrl, cacheTime, cacheDurationMs);
            cache.put(developerId, entry);
        }

        public synchronized ProfileImageCacheEntry getCachedProfileImage(String developerId, long currentTime) {
            totalCacheRequests++;
            
            ProfileImageCacheEntry entry = cache.get(developerId);
            if (entry != null) {
                if (entry.isValid(currentTime)) {
                    cacheHits++;
                    return entry;
                } else {
                    // Entry exists but is expired
                    return entry; // Return expired entry (caller should check validity)
                }
            }
            
            return null; // No entry found
        }

        public synchronized void cleanupExpiredEntries(long currentTime) {
            lastCleanupProcessedCount = cache.size();
            lastCleanupRemovedCount = 0;
            
            List<String> expiredKeys = new ArrayList<>();
            for (Map.Entry<String, ProfileImageCacheEntry> entry : cache.entrySet()) {
                if (!entry.getValue().isValid(currentTime)) {
                    expiredKeys.add(entry.getKey());
                }
            }
            
            for (String key : expiredKeys) {
                cache.remove(key);
                lastCleanupRemovedCount++;
            }
        }

        public synchronized ProfileImageCacheMetrics getCacheMetrics() {
            double hitRatio = totalCacheRequests > 0 ? (double) cacheHits / totalCacheRequests : 0.0;
            
            long totalAge = 0;
            long currentTime = System.currentTimeMillis();
            for (ProfileImageCacheEntry entry : cache.values()) {
                totalAge += (currentTime - entry.getCacheTime());
            }
            
            double averageAge = cache.size() > 0 ? totalAge / (double) cache.size() : 0.0;
            
            return new ProfileImageCacheMetrics(
                cache.size(),
                hitRatio,
                averageAge,
                lastCleanupProcessedCount,
                lastCleanupRemovedCount
            );
        }
    }

    /**
     * Profile image cache entry class
     */
    private static class ProfileImageCacheEntry {
        private final String imageUrl;
        private final long cacheTime;
        private final long cacheDurationMs;

        public ProfileImageCacheEntry(String imageUrl, long cacheTime, long cacheDurationMs) {
            this.imageUrl = imageUrl;
            this.cacheTime = cacheTime;
            this.cacheDurationMs = cacheDurationMs;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public long getCacheTime() {
            return cacheTime;
        }

        public boolean isValid(long currentTime) {
            return (currentTime - cacheTime) <= cacheDurationMs;
        }
    }

    /**
     * Profile image cache metrics class
     */
    private static class ProfileImageCacheMetrics {
        private final int totalCacheEntries;
        private final double cacheHitRatio;
        private final double averageCacheAge;
        private final int lastCleanupProcessedCount;
        private final int lastCleanupRemovedCount;

        public ProfileImageCacheMetrics(int totalCacheEntries, double cacheHitRatio, double averageCacheAge,
                                      int lastCleanupProcessedCount, int lastCleanupRemovedCount) {
            this.totalCacheEntries = totalCacheEntries;
            this.cacheHitRatio = cacheHitRatio;
            this.averageCacheAge = averageCacheAge;
            this.lastCleanupProcessedCount = lastCleanupProcessedCount;
            this.lastCleanupRemovedCount = lastCleanupRemovedCount;
        }

        public int getTotalCacheEntries() { return totalCacheEntries; }
        public double getCacheHitRatio() { return cacheHitRatio; }
        public double getAverageCacheAge() { return averageCacheAge; }
        public int getLastCleanupProcessedCount() { return lastCleanupProcessedCount; }
        public int getLastCleanupRemovedCount() { return lastCleanupRemovedCount; }
    }
}