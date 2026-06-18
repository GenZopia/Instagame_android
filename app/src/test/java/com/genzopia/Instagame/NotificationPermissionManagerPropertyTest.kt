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
        
        val currentTime = System.currentTimeMillis()
        val exactlyThirtyDaysAgo = currentTime - (30L * 24 * 60 * 60 * 1000)
        
        val prefs = context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("last_rejection_timestamp", exactlyThirtyDaysAgo)
            .apply()
        
        assertTrue(
            "Permission should be requestable at exactly 30 days",
            manager.shouldRequestPermission()
        )
        assertEquals(
            "Days until next retry should be 0 at exactly 30 days",
            0,
            manager.getDaysUntilNextRetry()
        )
    }

    /**
     * Property 10: Rejection timestamp storage
     * **Validates: Requirements 3.3**
     *
     * When the user rejects notification permission, the system SHALL store the current
     * timestamp in SharedPreferences. This property verifies that:
     * - handlePermissionResult(false) stores a timestamp close to current time
     * - The stored timestamp is retrievable via getLastRejectionTimestamp()
     * - The timestamp is within a reasonable delta of System.currentTimeMillis()
     */
    @Test
    fun `property 10 - rejection stores timestamp in cache`() {
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()

        val before = System.currentTimeMillis()
        manager.handlePermissionResult(granted = false)
        val after = System.currentTimeMillis()

        val stored = manager.getLastRejectionTimestamp()
        assertTrue("Timestamp should be stored on rejection", stored > 0L)
        assertTrue("Stored timestamp should be >= before call", stored >= before)
        assertTrue("Stored timestamp should be <= after call", stored <= after)
    }

    @Test
    fun `property 10 - grant clears rejection timestamp`() {
        val manager = NotificationPermissionManager(context)
        // First reject
        manager.handlePermissionResult(granted = false)
        assertTrue("Timestamp stored after rejection", manager.getLastRejectionTimestamp() > 0L)

        // Then grant
        manager.handlePermissionResult(granted = true)
        assertEquals("Timestamp cleared after grant", 0L, manager.getLastRejectionTimestamp())
    }

    /**
     * Property 12: Timestamp update on retry rejection
     * **Validates: Requirements 3.5**
     *
     * When the user rejects permission after the 30-day interval has passed and we retry,
     * the system SHALL update the rejection timestamp (not keep the old one).
     * This ensures the next retry is calculated from the most recent rejection.
     */
    @Test
    fun `property 12 - timestamp updated on second rejection after 30-day interval`() {
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()

        // Simulate first rejection 31 days ago
        val thirtyOneDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)
        val prefs = context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_rejection_timestamp", thirtyOneDaysAgo).apply()

        // Verify permission is re-requestable after 30 days
        assertTrue("Should be requestable after 31 days", manager.shouldRequestPermission())

        // User rejects again — timestamp must be updated to now
        val beforeSecondRejection = System.currentTimeMillis()
        manager.handlePermissionResult(granted = false)
        val afterSecondRejection = System.currentTimeMillis()

        val updatedTimestamp = manager.getLastRejectionTimestamp()
        assertTrue("Timestamp should be updated to recent time", updatedTimestamp >= beforeSecondRejection)
        assertTrue("Timestamp should be updated to recent time", updatedTimestamp <= afterSecondRejection)

        // Immediately after second rejection, should NOT be requestable (30-day clock restarted)
        assertFalse(
            "Should NOT be requestable immediately after second rejection",
            manager.shouldRequestPermission()
        )
    }

    @Test
    fun `property 12 - days until retry resets to 30 after second rejection`() {
        val manager = NotificationPermissionManager(context)
        manager.clearRejectionTimestamp()

        // 31 days since first rejection — interval elapsed
        val thirtyOneDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31)
        val prefs = context.getSharedPreferences("notification_permission_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_rejection_timestamp", thirtyOneDaysAgo).apply()

        // Second rejection
        manager.handlePermissionResult(granted = false)

        val daysRemaining = manager.getDaysUntilNextRetry()
        // Should be close to 30 days (allow 1 day rounding tolerance)
        assertTrue("Days remaining should be ~30 after fresh rejection, got $daysRemaining",
            daysRemaining in 29..30)
    }
}

