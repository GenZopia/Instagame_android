package com.genzopia.Instagame

import android.content.Context
import com.genzopia.Instagame.utils.NotificationPermissionManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Property-based tests for NotificationPermissionManager retry logic.
 * Feature: fcm-notifications-force-update
 * 
 * These tests validate that NotificationPermissionManager correctly handles
 * notification permission rejection timestamps and 30-day retry intervals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // Android 13 (TIRAMISU) where POST_NOTIFICATIONS is required
class NotificationPermissionManagerPropertyTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        // Clear any previous state
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()
    }

    /**
     * Property 11: 30-day retry interval
     * **Validates: Requirements 3.4**
     * 
     * For any rejection timestamp, if 30 days have elapsed, the system should allow 
     * permission request again. This property tests the core retry interval logic
     * by generating various timestamps and verifying that permissions are only 
     * requested again after the 30-day interval has passed.
     */
    @Test
    fun `property 11 - 30-day retry interval - exhaustive test`() {
        // Test key boundary values and representative samples
        val testCases = listOf(
            0,  // Today
            1,  // 1 day ago
            15, // 15 days ago (halfway)
            29, // 29 days ago (just before threshold)
            30, // Exactly 30 days ago (threshold)
            31, // 31 days ago (just after threshold)
            45, // 45 days ago
            60  // 60 days ago
        )

        for (daysElapsed in testCases) {
            // Clear state for each test case
            val manager = NotificationPermissionManager(context)
            manager.clearRejectionTimestamp()
            
            // Calculate timestamp based on days elapsed
            val currentTime = System.currentTimeMillis()
            val rejectionTimestamp = currentTime - TimeUnit.DAYS.toMillis(daysElapsed.toLong())
            
            // Simulate permission rejection at the calculated timestamp
            val prefs = context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_rejection_timestamp", rejectionTimestamp)
                .apply()
            
            // Property verification
            val shouldRequest = manager.shouldRequestPermission()
            
            if (daysElapsed >= 30) {
                // After 30 days, should allow permission request
                assertTrue(
                    "Permission should be requestable after $daysElapsed days (>= 30 days)",
                    shouldRequest
                )
                
                // Verify getDaysUntilNextRetry returns 0
                val daysRemaining = manager.getDaysUntilNextRetry()
                assertEquals(
                    "Days until next retry should be 0 when interval has passed (days elapsed: $daysElapsed)",
                    0,
                    daysRemaining
                )
            } else {
                // Before 30 days, should NOT allow permission request
                assertFalse(
                    "Permission should NOT be requestable after $daysElapsed days (< 30 days)",
                    shouldRequest
                )
                
                // Verify getDaysUntilNextRetry returns positive value
                val daysRemaining = manager.getDaysUntilNextRetry()
                assertTrue(
                    "Days until next retry should be positive when interval hasn't passed (days elapsed: $daysElapsed)",
                    daysRemaining > 0
                )
                
                // Verify the days remaining is approximately correct (allow 1 day tolerance for rounding)
                val expectedDaysRemaining = 30 - daysElapsed
                assertTrue(
                    "Days remaining ($daysRemaining) should be close to expected ($expectedDaysRemaining) for days elapsed $daysElapsed",
                    Math.abs(daysRemaining - expectedDaysRemaining) <= 1
                )
            }
        }
    }

    /**
     * Additional test: Verify never-rejected case
     * When no rejection timestamp exists, permission should be requestable immediately.
     */
    @Test
    fun `property 11 - never rejected allows immediate permission request`() {
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()
        
        // Verify permission should be requestable
        assertTrue(
            "Permission should be requestable when never rejected",
            manager.shouldRequestPermission()
        )
        
        // Verify getDaysUntilNextRetry returns 0
        assertEquals(
            "Days until next retry should be 0 when never rejected",
            0,
            manager.getDaysUntilNextRetry()
        )
        
        // Verify last rejection timestamp is 0
        assertEquals(
            "Last rejection timestamp should be 0 when never rejected",
            0L,
            manager.getLastRejectionTimestamp()
        )
    }

    /**
     * Additional test: Verify exact boundary at 30 days
     * Tests the exact moment when the retry interval is reached.
     */
    @Test
    fun `property 11 - exact 30-day boundary allows permission request`() {
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()
        
        // Set rejection timestamp to exactly 30 days ago
        val currentTime = System.currentTimeMillis()
        val exactlyThirtyDaysAgo = currentTime - (30L * 24 * 60 * 60 * 1000)
        
        val prefs = context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_rejection_timestamp", exactlyThirtyDaysAgo)
            .apply()
        
        // Verify permission should be requestable at exactly 30 days
        assertTrue(
            "Permission should be requestable at exactly 30 days",
            manager.shouldRequestPermission()
        )
        
        // Verify getDaysUntilNextRetry returns 0
        assertEquals(
            "Days until next retry should be 0 at exactly 30 days",
            0,
            manager.getDaysUntilNextRetry()
        )
    }
}

