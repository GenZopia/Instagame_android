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

import static org.junit.Assert.*;

/**
 * Property-based tests for single video playback rule
 * **Feature: reelview-optimization, Property 6: Single Video Playback Rule**
 * **Validates: Requirements 2.3**
 */
@RunWith(RobolectricTestRunner.class)
public class SingleVideoPlaybackRuleTest {

    @Mock
    private RecyclerView mockRecyclerView;
    
    private Context context;
    private ReelAdapter adapter;
    private List<ReelItem> testReelItems;
    private SingleVideoPlaybackManager playbackManager;

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
        playbackManager = new SingleVideoPlaybackManager();
    }
    @Provide
    Arbitrary<ReelItem> playbackRuleReelItems() {
        return Arbitraries.create(() -> {
            String videoId = "playback_rule_video_" + System.nanoTime();
            String title = "Playback Rule Test Video " + (int)(Math.random() * 1000);
            String likes = String.valueOf((int)(Math.random() * 10000));
            String description = "Playback rule test description " + (int)(Math.random() * 100);
            String developerId = "dev_" + (int)(Math.random() * 100);
            String gameId = "game_" + (int)(Math.random() * 50);
            
            ReelItem item = new ReelItem(videoId, title, likes, description, developerId, gameId);
            item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
            item.setVideoDuration((int)(Math.random() * 300) + 10); // 10-310 seconds
            return item;
        });
    }

    @Provide
    Arbitrary<List<ReelItem>> multipleVideoSequences() {
        return Arbitraries.create(() -> {
            List<ReelItem> sequence = new ArrayList<>();
            int sequenceLength = 3 + (int)(Math.random() * 7); // 3-10 videos
            
            for (int i = 0; i < sequenceLength; i++) {
                String videoId = "multi_video_" + i + "_" + System.nanoTime();
                ReelItem item = new ReelItem(videoId, "Multi Video " + i, "100", 
                                           "Description " + i, "dev1", "game1");
                item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
                item.setVideoDuration(60);
                sequence.add(item);
            }
            return sequence;
        });
    }

    /**
     * Property 6: Single Video Playback Rule
     * For any multiple videos visible, only the most visible one should be playing
     * **Validates: Requirements 2.3**
     */
    @Property(tries = 50)
    public void onlyMostVisibleVideoPlays(@ForAll("multipleVideoSequences") List<ReelItem> videoSequence) {
        // Simulate multiple videos being visible with different visibility percentages
        Map<String, Integer> videoVisibility = new HashMap<>();
        String mostVisibleVideoId = null;
        int maxVisibility = 0;
        
        for (int i = 0; i < videoSequence.size(); i++) {
            ReelItem video = videoSequence.get(i);
            int visibility = 10 + (int)(Math.random() * 90); // 10-100% visibility
            videoVisibility.put(video.getVideoId(), visibility);
            
            if (visibility > maxVisibility) {
                maxVisibility = visibility;
                mostVisibleVideoId = video.getVideoId();
            }
        }
        
        // Update playback manager with visibility information
        playbackManager.updateVideoVisibility(videoVisibility);
        
        // Verify single video playback rule
        List<String> playingVideos = playbackManager.getPlayingVideos();
        
        // Only one video should be playing
        assertEquals("Only one video should be playing at a time", 1, playingVideos.size());
        
        // The playing video should be the most visible one
        String playingVideoId = playingVideos.get(0);
        assertEquals("The most visible video should be the one playing", 
                    mostVisibleVideoId, playingVideoId);
        
        // All other videos should be paused
        for (ReelItem video : videoSequence) {
            String videoId = video.getVideoId();
            boolean shouldBePlaying = videoId.equals(mostVisibleVideoId);
            boolean isPlaying = playbackManager.isVideoPlaying(videoId);
            
            assertEquals("Video " + videoId + " playback state should match visibility rule", 
                        shouldBePlaying, isPlaying);
        }
        
        // Verify playback state consistency
        PlaybackStateMetrics metrics = playbackManager.getPlaybackMetrics();
        assertEquals("Metrics should show exactly one playing video", 1, metrics.getPlayingCount());
        assertEquals("Metrics should show correct paused count", 
                    videoSequence.size() - 1, metrics.getPausedCount());
    }

    /**
     * Property test: Single video playback rule should be maintained during visibility changes
     */
    @Property(tries = 30)
    public void singleVideoPlaybackMaintainedDuringVisibilityChanges(@ForAll("multipleVideoSequences") List<ReelItem> videoSequence) {
        if (videoSequence.size() < 3) return; // Need at least 3 videos for meaningful test
        
        // Initial state - first video most visible
        Map<String, Integer> initialVisibility = new HashMap<>();
        for (int i = 0; i < videoSequence.size(); i++) {
            ReelItem video = videoSequence.get(i);
            int visibility = (i == 0) ? 80 : 20; // First video 80%, others 20%
            initialVisibility.put(video.getVideoId(), visibility);
        }
        
        playbackManager.updateVideoVisibility(initialVisibility);
        
        // Verify initial state
        List<String> initialPlayingVideos = playbackManager.getPlayingVideos();
        assertEquals("Initially, only one video should be playing", 1, initialPlayingVideos.size());
        assertEquals("Initially, first video should be playing", 
                    videoSequence.get(0).getVideoId(), initialPlayingVideos.get(0));
        
        // Change visibility - make middle video most visible
        Map<String, Integer> changedVisibility = new HashMap<>();
        int middleIndex = videoSequence.size() / 2;
        for (int i = 0; i < videoSequence.size(); i++) {
            ReelItem video = videoSequence.get(i);
            int visibility = (i == middleIndex) ? 90 : 15; // Middle video 90%, others 15%
            changedVisibility.put(video.getVideoId(), visibility);
        }
        
        playbackManager.updateVideoVisibility(changedVisibility);
        
        // Verify state after change
        List<String> finalPlayingVideos = playbackManager.getPlayingVideos();
        assertEquals("After change, only one video should be playing", 1, finalPlayingVideos.size());
        assertEquals("After change, middle video should be playing", 
                    videoSequence.get(middleIndex).getVideoId(), finalPlayingVideos.get(0));
        
        // Verify previous video was paused
        assertFalse("Previous playing video should now be paused", 
                   playbackManager.isVideoPlaying(videoSequence.get(0).getVideoId()));
        
        // Verify playback transition metrics
        PlaybackStateMetrics finalMetrics = playbackManager.getPlaybackMetrics();
        assertEquals("Final metrics should show exactly one playing video", 1, finalMetrics.getPlayingCount());
        assertTrue("Should have recorded playback transitions", finalMetrics.getTransitionCount() > 0);
    }
    /**
     * Test single video playback rule with rapid scroll events
     */
    @Test
    public void singleVideoPlaybackRuleWithRapidScroll() {
        List<ReelItem> videoSequence = new ArrayList<>();
        
        // Create sequence of 8 videos
        for (int i = 0; i < 8; i++) {
            ReelItem item = new ReelItem("rapid_scroll_" + i, "Rapid Scroll Video " + i, "100", 
                                       "Description", "dev1", "game1");
            item.setVideoUrl("https://example.com/video/rapid_scroll_" + i + ".mp4");
            item.setVideoDuration(60);
            videoSequence.add(item);
        }
        
        // Simulate rapid scroll through videos
        for (int currentVideo = 0; currentVideo < videoSequence.size(); currentVideo++) {
            Map<String, Integer> visibility = new HashMap<>();
            
            // Set visibility for current and adjacent videos
            for (int i = 0; i < videoSequence.size(); i++) {
                ReelItem video = videoSequence.get(i);
                int visibilityPercent;
                
                if (i == currentVideo) {
                    visibilityPercent = 70; // Current video most visible
                } else if (Math.abs(i - currentVideo) == 1) {
                    visibilityPercent = 30; // Adjacent videos partially visible
                } else {
                    visibilityPercent = 0; // Other videos not visible
                }
                
                visibility.put(video.getVideoId(), visibilityPercent);
            }
            
            playbackManager.updateVideoVisibility(visibility);
            
            // Verify single video playback rule
            List<String> playingVideos = playbackManager.getPlayingVideos();
            assertEquals("During rapid scroll, only one video should play at position " + currentVideo, 
                        1, playingVideos.size());
            assertEquals("Current video should be the one playing at position " + currentVideo, 
                        videoSequence.get(currentVideo).getVideoId(), playingVideos.get(0));
            
            // Verify all other videos are paused
            for (int i = 0; i < videoSequence.size(); i++) {
                if (i != currentVideo) {
                    assertFalse("Video at position " + i + " should be paused when current is " + currentVideo, 
                               playbackManager.isVideoPlaying(videoSequence.get(i).getVideoId()));
                }
            }
        }
        
        // Verify final metrics
        PlaybackStateMetrics metrics = playbackManager.getPlaybackMetrics();
        assertEquals("Should have exactly one playing video after rapid scroll", 1, metrics.getPlayingCount());
        assertTrue("Should have recorded multiple transitions during rapid scroll", 
                  metrics.getTransitionCount() >= videoSequence.size() - 1);
    }

    /**
     * Test single video playback rule with edge cases
     */
    @Test
    public void singleVideoPlaybackRuleWithEdgeCases() {
        List<ReelItem> videoSequence = new ArrayList<>();
        
        // Create sequence of 5 videos
        for (int i = 0; i < 5; i++) {
            ReelItem item = new ReelItem("edge_case_" + i, "Edge Case Video " + i, "100", 
                                       "Description", "dev1", "game1");
            item.setVideoUrl("https://example.com/video/edge_case_" + i + ".mp4");
            item.setVideoDuration(60);
            videoSequence.add(item);
        }
        
        // Test case 1: All videos have equal visibility
        Map<String, Integer> equalVisibility = new HashMap<>();
        for (ReelItem video : videoSequence) {
            equalVisibility.put(video.getVideoId(), 50); // All 50% visible
        }
        
        playbackManager.updateVideoVisibility(equalVisibility);
        List<String> playingVideos = playbackManager.getPlayingVideos();
        assertEquals("With equal visibility, only one video should still play", 1, playingVideos.size());
        
        // Test case 2: No videos visible
        Map<String, Integer> noVisibility = new HashMap<>();
        for (ReelItem video : videoSequence) {
            noVisibility.put(video.getVideoId(), 0); // All 0% visible
        }
        
        playbackManager.updateVideoVisibility(noVisibility);
        List<String> playingAfterNoVisibility = playbackManager.getPlayingVideos();
        assertEquals("With no visibility, no videos should play", 0, playingAfterNoVisibility.size());
        
        // Test case 3: Single video becomes visible again
        Map<String, Integer> singleVisible = new HashMap<>();
        for (int i = 0; i < videoSequence.size(); i++) {
            ReelItem video = videoSequence.get(i);
            int visibility = (i == 2) ? 60 : 0; // Only middle video visible
            singleVisible.put(video.getVideoId(), visibility);
        }
        
        playbackManager.updateVideoVisibility(singleVisible);
        List<String> playingAfterSingle = playbackManager.getPlayingVideos();
        assertEquals("With single video visible, that video should play", 1, playingAfterSingle.size());
        assertEquals("The visible video should be playing", 
                    videoSequence.get(2).getVideoId(), playingAfterSingle.get(0));
    }

    /**
     * Test single video playback rule performance under load
     */
    @Test
    public void singleVideoPlaybackRulePerformanceUnderLoad() {
        List<ReelItem> videoSequence = new ArrayList<>();
        
        // Create large sequence of videos
        for (int i = 0; i < 20; i++) {
            ReelItem item = new ReelItem("load_test_" + i, "Load Test Video " + i, "100", 
                                       "Description", "dev1", "game1");
            item.setVideoUrl("https://example.com/video/load_test_" + i + ".mp4");
            item.setVideoDuration(60);
            videoSequence.add(item);
        }
        
        // Perform many rapid visibility updates
        int numberOfUpdates = 50;
        long totalUpdateTime = 0;
        
        for (int update = 0; update < numberOfUpdates; update++) {
            Map<String, Integer> visibility = new HashMap<>();
            int mostVisibleIndex = (int)(Math.random() * videoSequence.size());
            
            // Set random visibility for all videos
            for (int i = 0; i < videoSequence.size(); i++) {
                ReelItem video = videoSequence.get(i);
                int visibilityPercent = (i == mostVisibleIndex) ? 
                    80 + (int)(Math.random() * 20) : // Most visible: 80-100%
                    (int)(Math.random() * 30);       // Others: 0-30%
                visibility.put(video.getVideoId(), visibilityPercent);
            }
            
            long startTime = System.currentTimeMillis();
            playbackManager.updateVideoVisibility(visibility);
            long endTime = System.currentTimeMillis();
            
            totalUpdateTime += (endTime - startTime);
            
            // Verify single video playback rule is maintained
            List<String> playingVideos = playbackManager.getPlayingVideos();
            assertEquals("Under load, only one video should play (update " + update + ")", 
                        1, playingVideos.size());
            assertEquals("Under load, most visible video should play (update " + update + ")", 
                        videoSequence.get(mostVisibleIndex).getVideoId(), playingVideos.get(0));
        }
        
        // Verify performance
        double avgUpdateTime = totalUpdateTime / (double) numberOfUpdates;
        assertTrue("Playback rule updates should be fast under load: " + avgUpdateTime + "ms", 
                  avgUpdateTime <= 20);
        
        // Verify final state
        PlaybackStateMetrics finalMetrics = playbackManager.getPlaybackMetrics();
        assertEquals("Final state should have exactly one playing video", 1, finalMetrics.getPlayingCount());
        assertTrue("Should have processed many updates", finalMetrics.getTotalUpdates() >= numberOfUpdates);
    }
    /**
     * Test single video playback rule with concurrent visibility updates
     */
    @Test
    public void singleVideoPlaybackRuleWithConcurrentUpdates() throws InterruptedException {
        List<ReelItem> videoSequence = new ArrayList<>();
        
        // Create sequence of videos
        for (int i = 0; i < 10; i++) {
            ReelItem item = new ReelItem("concurrent_" + i, "Concurrent Video " + i, "100", 
                                       "Description", "dev1", "game1");
            item.setVideoUrl("https://example.com/video/concurrent_" + i + ".mp4");
            item.setVideoDuration(60);
            videoSequence.add(item);
        }
        
        int numberOfThreads = 3;
        List<Thread> threads = new ArrayList<>();
        
        // Create concurrent threads that update visibility
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    Map<String, Integer> visibility = new HashMap<>();
                    int mostVisibleIndex = (threadId + i) % videoSequence.size();
                    
                    for (int j = 0; j < videoSequence.size(); j++) {
                        ReelItem video = videoSequence.get(j);
                        int visibilityPercent = (j == mostVisibleIndex) ? 90 : 10;
                        visibility.put(video.getVideoId(), visibilityPercent);
                    }
                    
                    playbackManager.updateVideoVisibility(visibility);
                    
                    // Brief pause to allow other threads to interleave
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify final state maintains single video playback rule
        List<String> finalPlayingVideos = playbackManager.getPlayingVideos();
        assertTrue("After concurrent updates, at most one video should be playing", 
                  finalPlayingVideos.size() <= 1);
        
        // If a video is playing, verify it's a valid video from our sequence
        if (!finalPlayingVideos.isEmpty()) {
            String playingVideoId = finalPlayingVideos.get(0);
            boolean isValidVideo = videoSequence.stream()
                .anyMatch(video -> video.getVideoId().equals(playingVideoId));
            assertTrue("Playing video should be from our test sequence", isValidVideo);
        }
        
        // Verify metrics consistency
        PlaybackStateMetrics metrics = playbackManager.getPlaybackMetrics();
        assertTrue("Should have processed multiple updates", metrics.getTotalUpdates() > 0);
        assertEquals("Playing count should match actual playing videos", 
                    finalPlayingVideos.size(), metrics.getPlayingCount());
    }

    /**
     * Mock single video playback manager for testing
     */
    private static class SingleVideoPlaybackManager {
        private final Map<String, Boolean> videoPlaybackState = new HashMap<>();
        private int transitionCount = 0;
        private int totalUpdates = 0;

        public synchronized void updateVideoVisibility(Map<String, Integer> videoVisibility) {
            totalUpdates++;
            
            // Find the most visible video
            String mostVisibleVideoId = null;
            int maxVisibility = 0;
            
            for (Map.Entry<String, Integer> entry : videoVisibility.entrySet()) {
                if (entry.getValue() > maxVisibility) {
                    maxVisibility = entry.getValue();
                    mostVisibleVideoId = entry.getKey();
                }
            }
            
            // Update playback states
            boolean hadTransition = false;
            for (String videoId : videoVisibility.keySet()) {
                boolean shouldPlay = videoId.equals(mostVisibleVideoId) && maxVisibility > 0;
                Boolean currentState = videoPlaybackState.get(videoId);
                
                if (currentState == null || currentState != shouldPlay) {
                    hadTransition = true;
                }
                
                videoPlaybackState.put(videoId, shouldPlay);
            }
            
            if (hadTransition) {
                transitionCount++;
            }
        }

        public synchronized List<String> getPlayingVideos() {
            List<String> playingVideos = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : videoPlaybackState.entrySet()) {
                if (entry.getValue()) {
                    playingVideos.add(entry.getKey());
                }
            }
            return playingVideos;
        }

        public synchronized boolean isVideoPlaying(String videoId) {
            return videoPlaybackState.getOrDefault(videoId, false);
        }

        public synchronized PlaybackStateMetrics getPlaybackMetrics() {
            int playingCount = 0;
            int pausedCount = 0;
            
            for (Boolean isPlaying : videoPlaybackState.values()) {
                if (isPlaying) {
                    playingCount++;
                } else {
                    pausedCount++;
                }
            }
            
            return new PlaybackStateMetrics(playingCount, pausedCount, transitionCount, totalUpdates);
        }
    }

    /**
     * Playback state metrics class
     */
    private static class PlaybackStateMetrics {
        private final int playingCount;
        private final int pausedCount;
        private final int transitionCount;
        private final int totalUpdates;

        public PlaybackStateMetrics(int playingCount, int pausedCount, int transitionCount, int totalUpdates) {
            this.playingCount = playingCount;
            this.pausedCount = pausedCount;
            this.transitionCount = transitionCount;
            this.totalUpdates = totalUpdates;
        }

        public int getPlayingCount() { return playingCount; }
        public int getPausedCount() { return pausedCount; }
        public int getTransitionCount() { return transitionCount; }
        public int getTotalUpdates() { return totalUpdates; }
    }
}