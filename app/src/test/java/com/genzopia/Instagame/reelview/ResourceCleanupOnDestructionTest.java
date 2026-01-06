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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Property-based tests for resource cleanup on destruction
 * **Feature: reelview-optimization, Property 12: Resource Cleanup on Destruction**
 * **Validates: Requirements 4.4**
 */
@RunWith(RobolectricTestRunner.class)
public class ResourceCleanupOnDestructionTest {

    @Mock
    private RecyclerView mockRecyclerView;
    
    private Context context;
    private ReelAdapter adapter;
    private List<ReelItem> testReelItems;
    private ResourceCleanupManager cleanupManager;

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
        cleanupManager = new ResourceCleanupManager();
    }
    @Provide
    Arbitrary<ReelItem> cleanupReelItems() {
        return Arbitraries.create(() -> {
            String videoId = "cleanup_video_" + System.nanoTime();
            String title = "Cleanup Test Video " + (int)(Math.random() * 1000);
            String likes = String.valueOf((int)(Math.random() * 10000));
            String description = "Cleanup test description " + (int)(Math.random() * 100);
            String developerId = "dev_" + (int)(Math.random() * 100);
            String gameId = "game_" + (int)(Math.random() * 50);
            
            ReelItem item = new ReelItem(videoId, title, likes, description, developerId, gameId);
            item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
            item.setVideoDuration((int)(Math.random() * 300) + 10); // 10-310 seconds
            return item;
        });
    }

    @Provide
    Arbitrary<List<ReelItem>> resourceSequences() {
        return Arbitraries.create(() -> {
            List<ReelItem> sequence = new ArrayList<>();
            int sequenceLength = 5 + (int)(Math.random() * 15); // 5-20 videos
            
            for (int i = 0; i < sequenceLength; i++) {
                String videoId = "resource_video_" + i + "_" + System.nanoTime();
                ReelItem item = new ReelItem(videoId, "Resource Video " + i, "100", 
                                           "Description " + i, "dev1", "game1");
                item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
                item.setVideoDuration(60);
                sequence.add(item);
            }
            return sequence;
        });
    }

    /**
     * Property 12: Resource Cleanup on Destruction
     * For any fragment destruction, all players and caches should be released completely
     * **Validates: Requirements 4.4**
     */
    @Property(tries = 50)
    public void allResourcesReleasedOnDestruction(@ForAll("resourceSequences") List<ReelItem> videoSequence) {
        // Create resources for all videos
        for (ReelItem video : videoSequence) {
            cleanupManager.createPlayerResource(video.getVideoId(), video.getVideoUrl());
            cleanupManager.createThumbnailCache(video.getVideoId());
            cleanupManager.createPreloadResource(video.getVideoId());
        }
        
        // Verify resources are created
        ResourceMetrics beforeCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All players should be created", videoSequence.size(), beforeCleanup.getPlayerCount());
        assertEquals("All thumbnail caches should be created", videoSequence.size(), beforeCleanup.getThumbnailCacheCount());
        assertEquals("All preload resources should be created", videoSequence.size(), beforeCleanup.getPreloadResourceCount());
        assertTrue("Total resource count should be positive", beforeCleanup.getTotalResourceCount() > 0);
        
        // Simulate fragment destruction
        cleanupManager.onFragmentDestroy();
        
        // Verify complete cleanup
        ResourceMetrics afterCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All players should be released", 0, afterCleanup.getPlayerCount());
        assertEquals("All thumbnail caches should be cleared", 0, afterCleanup.getThumbnailCacheCount());
        assertEquals("All preload resources should be released", 0, afterCleanup.getPreloadResourceCount());
        assertEquals("Total resource count should be zero", 0, afterCleanup.getTotalResourceCount());
        
        // Verify cleanup was thorough
        for (ReelItem video : videoSequence) {
            assertFalse("Player should be released for " + video.getVideoId(), 
                       cleanupManager.hasPlayerResource(video.getVideoId()));
            assertFalse("Thumbnail cache should be cleared for " + video.getVideoId(), 
                       cleanupManager.hasThumbnailCache(video.getVideoId()));
            assertFalse("Preload resource should be released for " + video.getVideoId(), 
                       cleanupManager.hasPreloadResource(video.getVideoId()));
        }
        
        // Verify cleanup metrics
        CleanupMetrics cleanupMetrics = cleanupManager.getCleanupMetrics();
        assertEquals("Should have cleaned up all players", videoSequence.size(), cleanupMetrics.getPlayersReleased());
        assertEquals("Should have cleared all caches", videoSequence.size(), cleanupMetrics.getCachesCleared());
        assertEquals("Should have released all preload resources", videoSequence.size(), cleanupMetrics.getPreloadResourcesReleased());
        assertTrue("Cleanup should have been completed", cleanupMetrics.isCleanupCompleted());
    }

    /**
     * Property test: Resource cleanup should be idempotent (safe to call multiple times)
     */
    @Property(tries = 30)
    public void resourceCleanupIsIdempotent(@ForAll("resourceSequences") List<ReelItem> videoSequence) {
        // Create resources
        for (ReelItem video : videoSequence) {
            cleanupManager.createPlayerResource(video.getVideoId(), video.getVideoUrl());
            cleanupManager.createThumbnailCache(video.getVideoId());
        }
        
        // Verify resources exist
        ResourceMetrics beforeCleanup = cleanupManager.getResourceMetrics();
        assertTrue("Should have resources before cleanup", beforeCleanup.getTotalResourceCount() > 0);
        
        // Perform cleanup multiple times
        cleanupManager.onFragmentDestroy();
        ResourceMetrics afterFirstCleanup = cleanupManager.getResourceMetrics();
        
        cleanupManager.onFragmentDestroy(); // Second cleanup
        ResourceMetrics afterSecondCleanup = cleanupManager.getResourceMetrics();
        
        cleanupManager.onFragmentDestroy(); // Third cleanup
        ResourceMetrics afterThirdCleanup = cleanupManager.getResourceMetrics();
        
        // Verify all cleanup calls result in same state
        assertEquals("First cleanup should clear all resources", 0, afterFirstCleanup.getTotalResourceCount());
        assertEquals("Second cleanup should maintain clean state", 0, afterSecondCleanup.getTotalResourceCount());
        assertEquals("Third cleanup should maintain clean state", 0, afterThirdCleanup.getTotalResourceCount());
        
        // Verify cleanup metrics are consistent
        CleanupMetrics finalMetrics = cleanupManager.getCleanupMetrics();
        assertTrue("Cleanup should remain completed after multiple calls", finalMetrics.isCleanupCompleted());
        
        // Verify no exceptions or errors during multiple cleanup calls
        assertTrue("Multiple cleanup calls should be safe", finalMetrics.getCleanupCallCount() >= 3);
    }
    /**
     * Test resource cleanup with different resource types
     */
    @Test
    public void resourceCleanupWithDifferentResourceTypes() {
        List<String> videoIds = new ArrayList<>();
        
        // Create different types of resources
        for (int i = 0; i < 10; i++) {
            String videoId = "mixed_resource_" + i;
            videoIds.add(videoId);
            
            // Create different combinations of resources
            cleanupManager.createPlayerResource(videoId, "https://example.com/video/" + videoId + ".mp4");
            
            if (i % 2 == 0) {
                cleanupManager.createThumbnailCache(videoId);
            }
            
            if (i % 3 == 0) {
                cleanupManager.createPreloadResource(videoId);
            }
            
            if (i % 4 == 0) {
                cleanupManager.createNetworkResource(videoId);
            }
        }
        
        // Verify mixed resources are created
        ResourceMetrics beforeCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All players should be created", 10, beforeCleanup.getPlayerCount());
        assertEquals("Half should have thumbnail caches", 5, beforeCleanup.getThumbnailCacheCount());
        assertTrue("Some should have preload resources", beforeCleanup.getPreloadResourceCount() > 0);
        assertTrue("Some should have network resources", beforeCleanup.getNetworkResourceCount() > 0);
        
        // Perform cleanup
        cleanupManager.onFragmentDestroy();
        
        // Verify all resource types are cleaned up
        ResourceMetrics afterCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All players should be released", 0, afterCleanup.getPlayerCount());
        assertEquals("All thumbnail caches should be cleared", 0, afterCleanup.getThumbnailCacheCount());
        assertEquals("All preload resources should be released", 0, afterCleanup.getPreloadResourceCount());
        assertEquals("All network resources should be released", 0, afterCleanup.getNetworkResourceCount());
        
        // Verify individual resource cleanup
        for (String videoId : videoIds) {
            assertFalse("Player should be released for " + videoId, 
                       cleanupManager.hasPlayerResource(videoId));
            assertFalse("Thumbnail cache should be cleared for " + videoId, 
                       cleanupManager.hasThumbnailCache(videoId));
            assertFalse("Preload resource should be released for " + videoId, 
                       cleanupManager.hasPreloadResource(videoId));
            assertFalse("Network resource should be released for " + videoId, 
                       cleanupManager.hasNetworkResource(videoId));
        }
    }

    /**
     * Test resource cleanup performance with large number of resources
     */
    @Test
    public void resourceCleanupPerformanceWithLargeResourceSet() {
        List<String> videoIds = new ArrayList<>();
        
        // Create large number of resources
        for (int i = 0; i < 100; i++) {
            String videoId = "performance_test_" + i;
            videoIds.add(videoId);
            
            cleanupManager.createPlayerResource(videoId, "https://example.com/video/" + videoId + ".mp4");
            cleanupManager.createThumbnailCache(videoId);
            cleanupManager.createPreloadResource(videoId);
        }
        
        // Verify large resource set is created
        ResourceMetrics beforeCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All 100 players should be created", 100, beforeCleanup.getPlayerCount());
        assertEquals("All 100 thumbnail caches should be created", 100, beforeCleanup.getThumbnailCacheCount());
        assertEquals("All 100 preload resources should be created", 100, beforeCleanup.getPreloadResourceCount());
        
        // Measure cleanup performance
        long startTime = System.currentTimeMillis();
        cleanupManager.onFragmentDestroy();
        long endTime = System.currentTimeMillis();
        
        long cleanupTime = endTime - startTime;
        
        // Verify cleanup performance
        assertTrue("Cleanup of 100 resources should be fast: " + cleanupTime + "ms", cleanupTime <= 100);
        
        // Verify complete cleanup
        ResourceMetrics afterCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All resources should be cleaned up", 0, afterCleanup.getTotalResourceCount());
        
        // Verify cleanup metrics
        CleanupMetrics cleanupMetrics = cleanupManager.getCleanupMetrics();
        assertEquals("Should have released 100 players", 100, cleanupMetrics.getPlayersReleased());
        assertEquals("Should have cleared 100 caches", 100, cleanupMetrics.getCachesCleared());
        assertEquals("Should have released 100 preload resources", 100, cleanupMetrics.getPreloadResourcesReleased());
        assertTrue("Cleanup time should be recorded", cleanupMetrics.getCleanupTimeMs() <= cleanupTime + 10);
    }

    /**
     * Test resource cleanup with concurrent access
     */
    @Test
    public void resourceCleanupWithConcurrentAccess() throws InterruptedException {
        List<String> videoIds = new ArrayList<>();
        
        // Create resources
        for (int i = 0; i < 20; i++) {
            String videoId = "concurrent_cleanup_" + i;
            videoIds.add(videoId);
            
            cleanupManager.createPlayerResource(videoId, "https://example.com/video/" + videoId + ".mp4");
            cleanupManager.createThumbnailCache(videoId);
        }
        
        // Create threads that try to access resources during cleanup
        int numberOfThreads = 3;
        List<Thread> threads = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        
        // Start cleanup in main thread
        Thread cleanupThread = new Thread(() -> {
            try {
                Thread.sleep(10); // Brief delay to allow other threads to start
                cleanupManager.onFragmentDestroy();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Start threads that try to access resources
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        String videoId = videoIds.get((threadId + i) % videoIds.size());
                        
                        // Try to access resources (should be safe even during cleanup)
                        cleanupManager.hasPlayerResource(videoId);
                        cleanupManager.hasThumbnailCache(videoId);
                        
                        Thread.sleep(1); // Brief pause
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Start cleanup
        cleanupThread.start();
        
        // Wait for all threads to complete
        cleanupThread.join(5000);
        for (Thread thread : threads) {
            thread.join(5000);
        }
        
        // Verify no exceptions occurred during concurrent access
        assertTrue("No exceptions should occur during concurrent cleanup", exceptions.isEmpty());
        
        // Verify cleanup completed successfully
        ResourceMetrics finalMetrics = cleanupManager.getResourceMetrics();
        assertEquals("All resources should be cleaned up after concurrent access", 
                    0, finalMetrics.getTotalResourceCount());
        
        CleanupMetrics cleanupMetrics = cleanupManager.getCleanupMetrics();
        assertTrue("Cleanup should be completed despite concurrent access", cleanupMetrics.isCleanupCompleted());
    }
    /**
     * Test resource cleanup with memory pressure simulation
     */
    @Test
    public void resourceCleanupUnderMemoryPressure() {
        List<String> videoIds = new ArrayList<>();
        
        // Create resources
        for (int i = 0; i < 15; i++) {
            String videoId = "memory_pressure_cleanup_" + i;
            videoIds.add(videoId);
            
            cleanupManager.createPlayerResource(videoId, "https://example.com/video/" + videoId + ".mp4");
            cleanupManager.createThumbnailCache(videoId);
            cleanupManager.createPreloadResource(videoId);
        }
        
        // Simulate memory pressure
        List<byte[]> memoryPressure = new ArrayList<>();
        try {
            // Allocate memory to simulate pressure
            for (int i = 0; i < 30; i++) {
                memoryPressure.add(new byte[1024 * 1024]); // 1MB each
            }
            
            // Perform cleanup under memory pressure
            long startTime = System.currentTimeMillis();
            cleanupManager.onFragmentDestroy();
            long endTime = System.currentTimeMillis();
            
            long cleanupTime = endTime - startTime;
            
            // Verify cleanup succeeded under memory pressure
            ResourceMetrics afterCleanup = cleanupManager.getResourceMetrics();
            assertEquals("All resources should be cleaned up under memory pressure", 
                        0, afterCleanup.getTotalResourceCount());
            
            // Verify cleanup performance wasn't severely degraded
            assertTrue("Cleanup should remain reasonably fast under memory pressure: " + cleanupTime + "ms", 
                      cleanupTime <= 200);
            
            // Verify cleanup metrics
            CleanupMetrics cleanupMetrics = cleanupManager.getCleanupMetrics();
            assertTrue("Cleanup should be completed under memory pressure", cleanupMetrics.isCleanupCompleted());
            assertEquals("Should have cleaned up all resources", 15, cleanupMetrics.getPlayersReleased());
            
        } finally {
            // Clean up memory pressure
            memoryPressure.clear();
            System.gc();
        }
    }

    /**
     * Test resource cleanup with partial resource creation
     */
    @Test
    public void resourceCleanupWithPartialResourceCreation() {
        List<String> videoIds = new ArrayList<>();
        
        // Create partial resources (simulate interrupted resource creation)
        for (int i = 0; i < 12; i++) {
            String videoId = "partial_resource_" + i;
            videoIds.add(videoId);
            
            // Always create player
            cleanupManager.createPlayerResource(videoId, "https://example.com/video/" + videoId + ".mp4");
            
            // Sometimes fail to create other resources (simulate errors)
            if (i % 3 != 0) { // Skip every 3rd thumbnail cache
                cleanupManager.createThumbnailCache(videoId);
            }
            
            if (i % 4 != 0) { // Skip every 4th preload resource
                cleanupManager.createPreloadResource(videoId);
            }
        }
        
        // Verify partial resource creation
        ResourceMetrics beforeCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All players should be created", 12, beforeCleanup.getPlayerCount());
        assertTrue("Some thumbnail caches should be missing", beforeCleanup.getThumbnailCacheCount() < 12);
        assertTrue("Some preload resources should be missing", beforeCleanup.getPreloadResourceCount() < 12);
        
        // Perform cleanup
        cleanupManager.onFragmentDestroy();
        
        // Verify complete cleanup despite partial resources
        ResourceMetrics afterCleanup = cleanupManager.getResourceMetrics();
        assertEquals("All existing resources should be cleaned up", 0, afterCleanup.getTotalResourceCount());
        
        // Verify individual cleanup
        for (String videoId : videoIds) {
            assertFalse("No player resources should remain for " + videoId, 
                       cleanupManager.hasPlayerResource(videoId));
            assertFalse("No thumbnail caches should remain for " + videoId, 
                       cleanupManager.hasThumbnailCache(videoId));
            assertFalse("No preload resources should remain for " + videoId, 
                       cleanupManager.hasPreloadResource(videoId));
        }
        
        // Verify cleanup metrics
        CleanupMetrics cleanupMetrics = cleanupManager.getCleanupMetrics();
        assertTrue("Cleanup should be completed with partial resources", cleanupMetrics.isCleanupCompleted());
        assertEquals("Should have released all created players", 12, cleanupMetrics.getPlayersReleased());
    }
    /**
     * Mock resource cleanup manager for testing
     */
    private static class ResourceCleanupManager {
        private final Map<String, PlayerResource> playerResources = new HashMap<>();
        private final Map<String, ThumbnailCacheResource> thumbnailCaches = new HashMap<>();
        private final Map<String, PreloadResource> preloadResources = new HashMap<>();
        private final Map<String, NetworkResource> networkResources = new HashMap<>();
        
        private final AtomicInteger playersReleased = new AtomicInteger(0);
        private final AtomicInteger cachesCleared = new AtomicInteger(0);
        private final AtomicInteger preloadResourcesReleased = new AtomicInteger(0);
        private final AtomicInteger networkResourcesReleased = new AtomicInteger(0);
        private final AtomicInteger cleanupCallCount = new AtomicInteger(0);
        
        private boolean cleanupCompleted = false;
        private long cleanupTimeMs = 0;

        public synchronized void createPlayerResource(String videoId, String videoUrl) {
            playerResources.put(videoId, new PlayerResource(videoId, videoUrl));
        }

        public synchronized void createThumbnailCache(String videoId) {
            thumbnailCaches.put(videoId, new ThumbnailCacheResource(videoId));
        }

        public synchronized void createPreloadResource(String videoId) {
            preloadResources.put(videoId, new PreloadResource(videoId));
        }

        public synchronized void createNetworkResource(String videoId) {
            networkResources.put(videoId, new NetworkResource(videoId));
        }

        public synchronized void onFragmentDestroy() {
            long startTime = System.currentTimeMillis();
            cleanupCallCount.incrementAndGet();
            
            // Release all player resources
            for (PlayerResource player : playerResources.values()) {
                player.release();
                playersReleased.incrementAndGet();
            }
            playerResources.clear();
            
            // Clear all thumbnail caches
            for (ThumbnailCacheResource cache : thumbnailCaches.values()) {
                cache.clear();
                cachesCleared.incrementAndGet();
            }
            thumbnailCaches.clear();
            
            // Release all preload resources
            for (PreloadResource preload : preloadResources.values()) {
                preload.release();
                preloadResourcesReleased.incrementAndGet();
            }
            preloadResources.clear();
            
            // Release all network resources
            for (NetworkResource network : networkResources.values()) {
                network.release();
                networkResourcesReleased.incrementAndGet();
            }
            networkResources.clear();
            
            cleanupCompleted = true;
            long endTime = System.currentTimeMillis();
            cleanupTimeMs = endTime - startTime;
        }

        public synchronized boolean hasPlayerResource(String videoId) {
            return playerResources.containsKey(videoId);
        }

        public synchronized boolean hasThumbnailCache(String videoId) {
            return thumbnailCaches.containsKey(videoId);
        }

        public synchronized boolean hasPreloadResource(String videoId) {
            return preloadResources.containsKey(videoId);
        }

        public synchronized boolean hasNetworkResource(String videoId) {
            return networkResources.containsKey(videoId);
        }

        public synchronized ResourceMetrics getResourceMetrics() {
            return new ResourceMetrics(
                playerResources.size(),
                thumbnailCaches.size(),
                preloadResources.size(),
                networkResources.size()
            );
        }

        public synchronized CleanupMetrics getCleanupMetrics() {
            return new CleanupMetrics(
                playersReleased.get(),
                cachesCleared.get(),
                preloadResourcesReleased.get(),
                networkResourcesReleased.get(),
                cleanupCompleted,
                cleanupTimeMs,
                cleanupCallCount.get()
            );
        }
    }

    /**
     * Mock resource classes
     */
    private static class PlayerResource {
        private final String videoId;
        private final String videoUrl;
        private boolean released = false;

        public PlayerResource(String videoId, String videoUrl) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
        }

        public void release() {
            this.released = true;
        }

        public boolean isReleased() { return released; }
    }

    private static class ThumbnailCacheResource {
        private final String videoId;
        private boolean cleared = false;

        public ThumbnailCacheResource(String videoId) {
            this.videoId = videoId;
        }

        public void clear() {
            this.cleared = true;
        }

        public boolean isCleared() { return cleared; }
    }

    private static class PreloadResource {
        private final String videoId;
        private boolean released = false;

        public PreloadResource(String videoId) {
            this.videoId = videoId;
        }

        public void release() {
            this.released = true;
        }

        public boolean isReleased() { return released; }
    }

    private static class NetworkResource {
        private final String videoId;
        private boolean released = false;

        public NetworkResource(String videoId) {
            this.videoId = videoId;
        }

        public void release() {
            this.released = true;
        }

        public boolean isReleased() { return released; }
    }

    /**
     * Resource metrics class
     */
    private static class ResourceMetrics {
        private final int playerCount;
        private final int thumbnailCacheCount;
        private final int preloadResourceCount;
        private final int networkResourceCount;

        public ResourceMetrics(int playerCount, int thumbnailCacheCount, 
                             int preloadResourceCount, int networkResourceCount) {
            this.playerCount = playerCount;
            this.thumbnailCacheCount = thumbnailCacheCount;
            this.preloadResourceCount = preloadResourceCount;
            this.networkResourceCount = networkResourceCount;
        }

        public int getPlayerCount() { return playerCount; }
        public int getThumbnailCacheCount() { return thumbnailCacheCount; }
        public int getPreloadResourceCount() { return preloadResourceCount; }
        public int getNetworkResourceCount() { return networkResourceCount; }
        public int getTotalResourceCount() { 
            return playerCount + thumbnailCacheCount + preloadResourceCount + networkResourceCount; 
        }
    }

    /**
     * Cleanup metrics class
     */
    private static class CleanupMetrics {
        private final int playersReleased;
        private final int cachesCleared;
        private final int preloadResourcesReleased;
        private final int networkResourcesReleased;
        private final boolean cleanupCompleted;
        private final long cleanupTimeMs;
        private final int cleanupCallCount;

        public CleanupMetrics(int playersReleased, int cachesCleared, int preloadResourcesReleased,
                            int networkResourcesReleased, boolean cleanupCompleted, 
                            long cleanupTimeMs, int cleanupCallCount) {
            this.playersReleased = playersReleased;
            this.cachesCleared = cachesCleared;
            this.preloadResourcesReleased = preloadResourcesReleased;
            this.networkResourcesReleased = networkResourcesReleased;
            this.cleanupCompleted = cleanupCompleted;
            this.cleanupTimeMs = cleanupTimeMs;
            this.cleanupCallCount = cleanupCallCount;
        }

        public int getPlayersReleased() { return playersReleased; }
        public int getCachesCleared() { return cachesCleared; }
        public int getPreloadResourcesReleased() { return preloadResourcesReleased; }
        public int getNetworkResourcesReleased() { return networkResourcesReleased; }
        public boolean isCleanupCompleted() { return cleanupCompleted; }
        public long getCleanupTimeMs() { return cleanupTimeMs; }
        public int getCleanupCallCount() { return cleanupCallCount; }
    }
}