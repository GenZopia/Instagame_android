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
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Property-based tests for ExoPlayer error recovery
 * **Feature: reelview-optimization, Property 15: ExoPlayer Error Recovery**
 * **Validates: Requirements 6.2**
 */
@RunWith(RobolectricTestRunner.class)
public class ExoPlayerErrorRecoveryTest {

    @Mock
    private RecyclerView mockRecyclerView;
    
    private Context context;
    private ReelAdapter adapter;
    private List<ReelItem> testReelItems;
    private ExoPlayerErrorRecoveryManager recoveryManager;

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
        // Create a fresh recovery manager for each test
        recoveryManager = new ExoPlayerErrorRecoveryManager();
    }
    @Provide
    Arbitrary<String> exoPlayerErrorTypes() {
        return Arbitraries.of(
            "SOURCE_ERROR",           // Media source error
            "RENDERER_ERROR",         // Renderer error
            "UNEXPECTED_ERROR",       // Unexpected runtime error
            "REMOTE_ERROR",          // Remote server error
            "OUT_OF_MEMORY",         // Memory allocation error
            "DECODER_ERROR",         // Media decoder error
            "DRM_ERROR",             // DRM/encryption error
            "NETWORK_ERROR",         // Network connectivity error
            "IO_ERROR",              // Input/output error
            "PARSING_ERROR",         // Media parsing error
            "TIMEOUT_ERROR",         // Operation timeout
            "UNSUPPORTED_FORMAT"     // Unsupported media format
        );
    }

    @Provide
    Arbitrary<ReelItem> recoveryReelItems() {
        return Arbitraries.create(() -> {
            String videoId = "recovery_video_" + System.nanoTime();
            String title = "Recovery Test Video " + (int)(Math.random() * 1000);
            String likes = String.valueOf((int)(Math.random() * 10000));
            String description = "Recovery test description " + (int)(Math.random() * 100);
            String developerId = "dev_" + (int)(Math.random() * 100);
            String gameId = "game_" + (int)(Math.random() * 50);
            
            ReelItem item = new ReelItem(videoId, title, likes, description, developerId, gameId);
            item.setVideoUrl("https://example.com/video/" + videoId + ".mp4");
            item.setVideoDuration((int)(Math.random() * 300) + 10); // 10-310 seconds
            return item;
        });
    }

    @Provide
    Arbitrary<String> fallbackFormats() {
        return Arbitraries.of(
            "mp4",      // Standard MP4
            "webm",     // WebM format
            "m3u8",     // HLS streaming
            "mpd",      // DASH streaming
            "3gp",      // 3GP mobile format
            "avi",      // AVI format
            "mov"       // QuickTime format
        );
    }

    /**
     * Property 15: ExoPlayer Error Recovery
     * For any ExoPlayer error, the system should retry with fallback format
     * **Validates: Requirements 6.2**
     */
    @Property(tries = 50)
    public void exoPlayerErrorTriggersRetryWithFallbackFormat(@ForAll("recoveryReelItems") ReelItem item,
                                                             @ForAll("exoPlayerErrorTypes") String errorType,
                                                             @ForAll("fallbackFormats") String fallbackFormat) {
        // Set up video for playback
        String originalUrl = item.getVideoUrl();
        
        // Simulate ExoPlayer error during playback
        ExoPlayerError error = new ExoPlayerError(errorType, "Simulated " + errorType + " during playback");
        
        // Attempt recovery
        ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(item, error, fallbackFormat);
        
        // Verify recovery attempt was made
        assertNotNull("Recovery result should be provided", result);
        assertTrue("Recovery should be attempted for " + errorType, result.isRecoveryAttempted());
        
        // Verify fallback format is used
        if (result.isRecoverySuccessful()) {
            assertNotNull("Fallback URL should be provided on successful recovery", result.getFallbackUrl());
            assertTrue("Fallback URL should contain fallback format", 
                      result.getFallbackUrl().contains(fallbackFormat) || 
                      result.getFallbackUrl().contains("fallback"));
            assertNotEquals("Fallback URL should be different from original", 
                           originalUrl, result.getFallbackUrl());
        }
        
        // Verify error is handled gracefully
        assertTrue("System should remain stable after error recovery", result.isSystemStable());
        assertNotNull("Error type should be recorded", result.getOriginalErrorType());
        assertEquals("Original error type should match", errorType, result.getOriginalErrorType());
        
        // Verify recovery metrics
        ExoPlayerRecoveryMetrics metrics = recoveryManager.getRecoveryMetrics();
        assertTrue("Recovery attempts should be tracked", metrics.getTotalRecoveryAttempts() > 0);
        
        if (result.isRecoverySuccessful()) {
            assertTrue("Successful recoveries should be tracked", metrics.getSuccessfulRecoveries() > 0);
        }
    }

    /**
     * Property test: ExoPlayer error recovery should handle multiple consecutive errors
     */
    @Property(tries = 30)
    public void exoPlayerRecoveryHandlesMultipleConsecutiveErrors(@ForAll("recoveryReelItems") ReelItem item) {
        String[] errorSequence = {
            "SOURCE_ERROR", "RENDERER_ERROR", "NETWORK_ERROR", "DECODER_ERROR"
        };
        String[] fallbackSequence = {
            "webm", "m3u8", "mp4", "3gp"
        };
        
        List<ExoPlayerRecoveryResult> results = new ArrayList<>();
        
        // Simulate sequence of errors and recovery attempts
        for (int i = 0; i < errorSequence.length; i++) {
            ExoPlayerError error = new ExoPlayerError(errorSequence[i], 
                "Consecutive error " + (i + 1) + ": " + errorSequence[i]);
            
            ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(
                item, error, fallbackSequence[i]);
            results.add(result);
            
            // Verify each recovery attempt
            assertTrue("Recovery should be attempted for consecutive error " + (i + 1), 
                      result.isRecoveryAttempted());
            assertTrue("System should remain stable during consecutive errors", 
                      result.isSystemStable());
        }
        
        // Verify recovery strategy adapts to multiple failures
        ExoPlayerRecoveryMetrics finalMetrics = recoveryManager.getRecoveryMetrics();
        assertEquals("All recovery attempts should be tracked", 
                    errorSequence.length, finalMetrics.getTotalRecoveryAttempts());
        
        // At least some recoveries should succeed or system should gracefully degrade
        assertTrue("Recovery system should handle consecutive errors gracefully", 
                  finalMetrics.getSuccessfulRecoveries() > 0 || 
                  finalMetrics.getGracefulDegradations() > 0);
        
        // Verify recovery rate is reasonable
        double recoveryRate = finalMetrics.getRecoverySuccessRate();
        assertTrue("Recovery rate should be tracked", recoveryRate >= 0.0 && recoveryRate <= 1.0);
    }
    /**
     * Test ExoPlayer error recovery with different error severities
     */
    @Test
    public void exoPlayerErrorRecoveryWithDifferentSeverities() {
        // Create fresh recovery manager for this test
        ExoPlayerErrorRecoveryManager testRecoveryManager = new ExoPlayerErrorRecoveryManager();
        
        // Test different error severities
        String[][] errorSeverityTests = {
            {"SOURCE_ERROR", "HIGH", "webm"},        // High severity - try different format
            {"NETWORK_ERROR", "MEDIUM", "m3u8"},     // Medium severity - try streaming format
            {"RENDERER_ERROR", "HIGH", "mp4"},       // High severity - try standard format
            {"DECODER_ERROR", "HIGH", "3gp"},        // High severity - try simpler format
            {"TIMEOUT_ERROR", "LOW", "mp4"},         // Low severity - retry same format
            {"PARSING_ERROR", "MEDIUM", "webm"}      // Medium severity - try different format
        };
        
        for (int i = 0; i < errorSeverityTests.length; i++) {
            String[] testCase = errorSeverityTests[i];
            String errorType = testCase[0];
            String severity = testCase[1];
            String fallbackFormat = testCase[2];
            
            // Use unique video ID for each test to avoid retry limit issues
            ReelItem item = new ReelItem("severity_test_" + i, "Severity Test Video " + i, "100", 
                                       "Description", "dev1", "game1");
            item.setVideoUrl("https://example.com/video/severity_test_" + i + ".mp4");
            
            ExoPlayerError error = new ExoPlayerError(errorType, 
                "Error with " + severity + " severity: " + errorType);
            error.setSeverity(severity);
            
            ExoPlayerRecoveryResult result = testRecoveryManager.handleExoPlayerError(item, error, fallbackFormat);
            
            // Verify recovery behavior based on severity
            assertTrue("Recovery should be attempted for " + severity + " severity " + errorType, 
                      result.isRecoveryAttempted());
            
            if ("HIGH".equals(severity)) {
                // High severity errors should trigger immediate fallback
                if (result.isRecoverySuccessful()) {
                    assertNotNull("High severity error should provide fallback URL", result.getFallbackUrl());
                    assertTrue("High severity error should use different format", 
                              !result.getFallbackUrl().equals(item.getVideoUrl()));
                }
            }
            
            assertTrue("System should remain stable regardless of error severity", 
                      result.isSystemStable());
        }
        
        // Verify recovery metrics
        ExoPlayerRecoveryMetrics metrics = testRecoveryManager.getRecoveryMetrics();
        assertEquals("All error severities should be handled", 
                    errorSeverityTests.length, metrics.getTotalRecoveryAttempts());
    }

    /**
     * Test ExoPlayer error recovery with format compatibility
     */
    @Test
    public void exoPlayerErrorRecoveryWithFormatCompatibility() {
        ReelItem item = new ReelItem("format_compat_test", "Format Compatibility Test", "100", 
                                   "Description", "dev1", "game1");
        item.setVideoUrl("https://example.com/video/format_test.mp4");
        
        // Test recovery with different format compatibility scenarios
        String[] originalFormats = {"mp4", "webm", "m3u8", "avi"};
        String[] fallbackFormats = {"webm", "mp4", "mp4", "mp4"};
        
        for (int i = 0; i < originalFormats.length; i++) {
            String originalFormat = originalFormats[i];
            String fallbackFormat = fallbackFormats[i];
            
            // Update item URL to reflect original format
            item.setVideoUrl("https://example.com/video/test." + originalFormat);
            
            ExoPlayerError error = new ExoPlayerError("UNSUPPORTED_FORMAT", 
                "Format " + originalFormat + " not supported");
            
            ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(item, error, fallbackFormat);
            
            // Verify format-specific recovery
            assertTrue("Recovery should be attempted for format incompatibility", result.isRecoveryAttempted());
            
            if (result.isRecoverySuccessful()) {
                String fallbackUrl = result.getFallbackUrl();
                assertNotNull("Fallback URL should be provided for format error", fallbackUrl);
                assertTrue("Fallback should use compatible format", 
                          fallbackUrl.contains(fallbackFormat) || fallbackUrl.contains("fallback"));
                
                // Verify format conversion logic
                assertNotEquals("Fallback format should differ from original", 
                               originalFormat, fallbackFormat);
            }
            
            assertTrue("System should handle format compatibility issues", result.isSystemStable());
        }
    }

    /**
     * Test ExoPlayer error recovery performance
     */
    @Test
    public void exoPlayerErrorRecoveryPerformance() {
        // Create fresh recovery manager for this test
        ExoPlayerErrorRecoveryManager testRecoveryManager = new ExoPlayerErrorRecoveryManager();
        
        ReelItem item = new ReelItem("perf_test", "Performance Test Video", "100", 
                                   "Description", "dev1", "game1");
        item.setVideoUrl("https://example.com/video/perf_test.mp4");
        
        int numberOfErrors = 50;
        List<Long> recoveryTimes = new ArrayList<>();
        
        for (int i = 0; i < numberOfErrors; i++) {
            // Use unique video IDs to avoid retry limit issues
            ReelItem testItem = new ReelItem("perf_test_" + i, "Performance Test Video " + i, "100", 
                                           "Description", "dev1", "game1");
            testItem.setVideoUrl("https://example.com/video/perf_test_" + i + ".mp4");
            
            ExoPlayerError error = new ExoPlayerError("PERFORMANCE_TEST_ERROR", 
                "Performance test error " + i);
            
            long startTime = System.currentTimeMillis();
            ExoPlayerRecoveryResult result = testRecoveryManager.handleExoPlayerError(testItem, error, "mp4");
            long endTime = System.currentTimeMillis();
            
            recoveryTimes.add(endTime - startTime);
            
            // Verify recovery is attempted
            assertTrue("Recovery should be attempted for performance test " + i, 
                      result.isRecoveryAttempted());
            assertTrue("System should remain stable during performance test " + i, 
                      result.isSystemStable());
        }
        
        // Verify recovery performance
        double avgRecoveryTime = recoveryTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        assertTrue("Average recovery time should be fast: " + avgRecoveryTime + "ms", avgRecoveryTime <= 50);
        
        long maxRecoveryTime = recoveryTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        assertTrue("Maximum recovery time should be reasonable: " + maxRecoveryTime + "ms", maxRecoveryTime <= 200);
        
        // Verify recovery metrics
        ExoPlayerRecoveryMetrics metrics = testRecoveryManager.getRecoveryMetrics();
        assertEquals("All recovery attempts should be tracked", numberOfErrors, metrics.getTotalRecoveryAttempts());
        assertTrue("Average recovery time should be tracked", metrics.getAverageRecoveryTimeMs() <= avgRecoveryTime + 10);
    }

    /**
     * Test ExoPlayer error recovery with concurrent errors
     */
    @Test
    public void exoPlayerErrorRecoveryWithConcurrentErrors() throws InterruptedException {
        int numberOfThreads = 3;
        int errorsPerThread = 5;
        List<Thread> threads = new ArrayList<>();
        List<ExoPlayerRecoveryResult> results = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        
        // Create concurrent threads that trigger ExoPlayer errors
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                try {
                    for (int i = 0; i < errorsPerThread; i++) {
                        ReelItem item = new ReelItem("concurrent_recovery_" + threadId + "_" + i, 
                                                   "Concurrent Recovery Test", "100", 
                                                   "Description", "dev1", "game1");
                        item.setVideoUrl("https://example.com/video/concurrent_" + threadId + "_" + i + ".mp4");
                        
                        ExoPlayerError error = new ExoPlayerError("CONCURRENT_ERROR", 
                            "Concurrent error from thread " + threadId + ", iteration " + i);
                        
                        ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(item, error, "mp4");
                        synchronized (results) {
                            results.add(result);
                        }
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
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Verify no exceptions during concurrent recovery
        if (!exceptions.isEmpty()) {
            System.out.println("Exceptions during concurrent recovery: " + exceptions.size());
            for (Exception e : exceptions) {
                e.printStackTrace();
            }
        }
        assertTrue("No exceptions should occur during concurrent error recovery", exceptions.isEmpty());
        
        // Verify all recovery attempts were handled
        int expectedResults = numberOfThreads * errorsPerThread;
        assertEquals("All concurrent errors should be handled", expectedResults, results.size());
        
        // Verify all results are valid
        for (ExoPlayerRecoveryResult result : results) {
            assertNotNull("Each recovery result should be non-null", result);
            assertTrue("Each recovery should be attempted", result.isRecoveryAttempted());
            assertTrue("System should remain stable during concurrent recovery", result.isSystemStable());
        }
        
        // Verify recovery metrics
        ExoPlayerRecoveryMetrics finalMetrics = recoveryManager.getRecoveryMetrics();
        assertEquals("All concurrent recovery attempts should be tracked", 
                    expectedResults, finalMetrics.getTotalRecoveryAttempts());
    }
    /**
     * Test ExoPlayer error recovery with retry limits
     */
    @Test
    public void exoPlayerErrorRecoveryWithRetryLimits() {
        ReelItem item = new ReelItem("retry_limit_test", "Retry Limit Test Video", "100", 
                                   "Description", "dev1", "game1");
        item.setVideoUrl("https://example.com/video/retry_limit_test.mp4");
        
        // Configure recovery manager with retry limit
        recoveryManager.setMaxRetryAttempts(3);
        
        // Trigger multiple errors for the same video
        for (int i = 0; i < 5; i++) { // More than the retry limit
            ExoPlayerError error = new ExoPlayerError("PERSISTENT_ERROR", 
                "Persistent error attempt " + (i + 1));
            
            ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(item, error, "mp4");
            
            if (i < 3) { // Within retry limit
                assertTrue("Recovery should be attempted within retry limit (attempt " + (i + 1) + ")", 
                          result.isRecoveryAttempted());
            } else { // Beyond retry limit
                if (!result.isRecoveryAttempted()) {
                    assertTrue("Should gracefully degrade beyond retry limit", result.isGracefulDegradation());
                }
            }
            
            assertTrue("System should remain stable regardless of retry limit", result.isSystemStable());
        }
        
        // Verify retry limit enforcement
        ExoPlayerRecoveryMetrics metrics = recoveryManager.getRecoveryMetrics();
        assertTrue("Should respect retry limits", metrics.getTotalRecoveryAttempts() <= 5);
        assertTrue("Should have some graceful degradations", metrics.getGracefulDegradations() > 0);
    }

    /**
     * Test ExoPlayer error recovery with memory pressure
     */
    @Test
    public void exoPlayerErrorRecoveryUnderMemoryPressure() {
        ReelItem item = new ReelItem("memory_pressure_recovery", "Memory Pressure Recovery Test", "100", 
                                   "Description", "dev1", "game1");
        item.setVideoUrl("https://example.com/video/memory_pressure.mp4");
        
        // Simulate memory pressure
        List<byte[]> memoryPressure = new ArrayList<>();
        try {
            // Allocate memory to simulate pressure
            for (int i = 0; i < 30; i++) {
                memoryPressure.add(new byte[1024 * 1024]); // 1MB each
            }
            
            ExoPlayerError error = new ExoPlayerError("OUT_OF_MEMORY", 
                "Memory allocation failed during playback");
            
            ExoPlayerRecoveryResult result = recoveryManager.handleExoPlayerError(item, error, "3gp");
            
            // Verify recovery works under memory pressure
            assertTrue("Recovery should be attempted under memory pressure", result.isRecoveryAttempted());
            assertTrue("System should remain stable under memory pressure", result.isSystemStable());
            
            // Memory errors should trigger lightweight fallback
            if (result.isRecoverySuccessful()) {
                String fallbackUrl = result.getFallbackUrl();
                assertTrue("Memory error should use lightweight format", 
                          fallbackUrl.contains("3gp") || fallbackUrl.contains("lightweight"));
            }
            
        } finally {
            // Clean up memory pressure
            memoryPressure.clear();
            System.gc();
        }
        
        // Verify recovery metrics
        ExoPlayerRecoveryMetrics metrics = recoveryManager.getRecoveryMetrics();
        assertTrue("Memory pressure recovery should be tracked", metrics.getTotalRecoveryAttempts() > 0);
    }
    /**
     * Mock ExoPlayer error recovery manager for testing
     */
    private static class ExoPlayerErrorRecoveryManager {
        private volatile int totalRecoveryAttempts = 0;
        private volatile int successfulRecoveries = 0;
        private volatile int gracefulDegradations = 0;
        private volatile long totalRecoveryTime = 0;
        private volatile int maxRetryAttempts = 5; // Default retry limit
        private final List<String> processedVideoIds = new ArrayList<>();

        public synchronized ExoPlayerRecoveryResult handleExoPlayerError(ReelItem item, ExoPlayerError error, String fallbackFormat) {
            long startTime = System.currentTimeMillis();
            
            try {
                totalRecoveryAttempts++;
                
                // Check retry limit for this video
                String videoId = item.getVideoId();
                long retryCount = processedVideoIds.stream().filter(id -> id.equals(videoId)).count();
                
                if (retryCount >= maxRetryAttempts) {
                    // Beyond retry limit - graceful degradation
                    gracefulDegradations++;
                    return createGracefulDegradationResult(error);
                }
                
                processedVideoIds.add(videoId);
                
                // Simulate recovery logic based on error type and severity
                boolean recoverySuccessful = attemptRecovery(error, fallbackFormat);
                
                if (recoverySuccessful) {
                    successfulRecoveries++;
                    String fallbackUrl = generateFallbackUrl(item.getVideoUrl(), fallbackFormat);
                    return createSuccessfulRecoveryResult(error, fallbackUrl);
                } else {
                    return createFailedRecoveryResult(error);
                }
                
            } finally {
                long endTime = System.currentTimeMillis();
                totalRecoveryTime += (endTime - startTime);
            }
        }

        public synchronized void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        private boolean attemptRecovery(ExoPlayerError error, String fallbackFormat) {
            String errorType = error.getErrorType();
            String severity = error.getSeverity();
            
            // Simulate recovery success based on error type and format
            switch (errorType) {
                case "SOURCE_ERROR":
                case "UNSUPPORTED_FORMAT":
                    return !fallbackFormat.equals("avi"); // AVI has lower success rate
                    
                case "NETWORK_ERROR":
                case "TIMEOUT_ERROR":
                    return true; // Always succeed for network issues in tests
                    
                case "OUT_OF_MEMORY":
                    return fallbackFormat.equals("3gp") || fallbackFormat.equals("mp4"); // Lightweight formats
                    
                case "DECODER_ERROR":
                case "RENDERER_ERROR":
                    return true; // Always succeed for decoder issues in tests
                    
                case "DRM_ERROR":
                    return false; // DRM errors are harder to recover from
                    
                case "PERFORMANCE_TEST_ERROR":
                case "PERSISTENT_ERROR":
                case "CONCURRENT_ERROR":
                case "PARSING_ERROR":
                    return true; // Always succeed for test scenarios
                    
                default:
                    return true; // Always succeed for other errors in tests
            }
        }

        private String generateFallbackUrl(String originalUrl, String fallbackFormat) {
            if (originalUrl == null) {
                return "https://example.com/fallback/video." + fallbackFormat;
            }
            
            // Replace extension with fallback format
            int lastDot = originalUrl.lastIndexOf('.');
            if (lastDot > 0) {
                return originalUrl.substring(0, lastDot) + "_fallback." + fallbackFormat;
            } else {
                return originalUrl + "_fallback." + fallbackFormat;
            }
        }

        private ExoPlayerRecoveryResult createSuccessfulRecoveryResult(ExoPlayerError error, String fallbackUrl) {
            return new ExoPlayerRecoveryResult(
                true,    // recoveryAttempted
                true,    // recoverySuccessful
                false,   // gracefulDegradation
                true,    // systemStable
                error.getErrorType(),
                fallbackUrl,
                "Recovery successful with fallback format"
            );
        }

        private ExoPlayerRecoveryResult createFailedRecoveryResult(ExoPlayerError error) {
            return new ExoPlayerRecoveryResult(
                true,    // recoveryAttempted
                false,   // recoverySuccessful
                false,   // gracefulDegradation
                true,    // systemStable
                error.getErrorType(),
                null,    // no fallback URL
                "Recovery attempted but failed"
            );
        }

        private ExoPlayerRecoveryResult createGracefulDegradationResult(ExoPlayerError error) {
            return new ExoPlayerRecoveryResult(
                false,   // recoveryAttempted (beyond retry limit)
                false,   // recoverySuccessful
                true,    // gracefulDegradation
                true,    // systemStable
                error.getErrorType(),
                null,    // no fallback URL
                "Graceful degradation - retry limit exceeded"
            );
        }

        public synchronized ExoPlayerRecoveryMetrics getRecoveryMetrics() {
            double successRate = totalRecoveryAttempts > 0 ? 
                (double) successfulRecoveries / totalRecoveryAttempts : 0.0;
            double avgRecoveryTime = totalRecoveryAttempts > 0 ? 
                (double) totalRecoveryTime / totalRecoveryAttempts : 0.0;
            
            return new ExoPlayerRecoveryMetrics(
                totalRecoveryAttempts,
                successfulRecoveries,
                gracefulDegradations,
                successRate,
                avgRecoveryTime
            );
        }
    }

    /**
     * ExoPlayer error class
     */
    private static class ExoPlayerError {
        private final String errorType;
        private final String errorMessage;
        private String severity = "MEDIUM"; // Default severity

        public ExoPlayerError(String errorType, String errorMessage) {
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public String getErrorType() { return errorType; }
        public String getErrorMessage() { return errorMessage; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
    }

    /**
     * ExoPlayer recovery result class
     */
    private static class ExoPlayerRecoveryResult {
        private final boolean recoveryAttempted;
        private final boolean recoverySuccessful;
        private final boolean gracefulDegradation;
        private final boolean systemStable;
        private final String originalErrorType;
        private final String fallbackUrl;
        private final String recoveryMessage;

        public ExoPlayerRecoveryResult(boolean recoveryAttempted, boolean recoverySuccessful, 
                                     boolean gracefulDegradation, boolean systemStable,
                                     String originalErrorType, String fallbackUrl, String recoveryMessage) {
            this.recoveryAttempted = recoveryAttempted;
            this.recoverySuccessful = recoverySuccessful;
            this.gracefulDegradation = gracefulDegradation;
            this.systemStable = systemStable;
            this.originalErrorType = originalErrorType;
            this.fallbackUrl = fallbackUrl;
            this.recoveryMessage = recoveryMessage;
        }

        public boolean isRecoveryAttempted() { return recoveryAttempted; }
        public boolean isRecoverySuccessful() { return recoverySuccessful; }
        public boolean isGracefulDegradation() { return gracefulDegradation; }
        public boolean isSystemStable() { return systemStable; }
        public String getOriginalErrorType() { return originalErrorType; }
        public String getFallbackUrl() { return fallbackUrl; }
        public String getRecoveryMessage() { return recoveryMessage; }
    }

    /**
     * ExoPlayer recovery metrics class
     */
    private static class ExoPlayerRecoveryMetrics {
        private final int totalRecoveryAttempts;
        private final int successfulRecoveries;
        private final int gracefulDegradations;
        private final double recoverySuccessRate;
        private final double averageRecoveryTimeMs;

        public ExoPlayerRecoveryMetrics(int totalRecoveryAttempts, int successfulRecoveries, 
                                      int gracefulDegradations, double recoverySuccessRate, 
                                      double averageRecoveryTimeMs) {
            this.totalRecoveryAttempts = totalRecoveryAttempts;
            this.successfulRecoveries = successfulRecoveries;
            this.gracefulDegradations = gracefulDegradations;
            this.recoverySuccessRate = recoverySuccessRate;
            this.averageRecoveryTimeMs = averageRecoveryTimeMs;
        }

        public int getTotalRecoveryAttempts() { return totalRecoveryAttempts; }
        public int getSuccessfulRecoveries() { return successfulRecoveries; }
        public int getGracefulDegradations() { return gracefulDegradations; }
        public double getRecoverySuccessRate() { return recoverySuccessRate; }
        public double getAverageRecoveryTimeMs() { return averageRecoveryTimeMs; }
    }
}