package com.genzopia.Instagame.onboarding

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

/**
 * Unit tests and property-based tests for [TutorialController].
 *
 * Uses Robolectric to provide a real Android context with in-memory SharedPreferences.
 *
 * **Validates: Requirements 1.1, 1.2, 1.3, 4.1, 4.3, 4.5**
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TutorialControllerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var controller: TutorialController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear SharedPreferences before each test to ensure isolation
        prefs = context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        controller = TutorialController(context)
    }

    // -------------------------------------------------------------------------
    // Property 1: Eligibility Invariant
    // shouldShowTutorial returns true when completion key is absent/false
    // Validates: Requirements 1.1, 1.2, 1.3
    // -------------------------------------------------------------------------

    /**
     * Property 1 (positive): Fresh SharedPreferences → shouldShowTutorial returns true.
     *
     * **Validates: Requirements 1.1, 1.3**
     */
    @Test
    fun `fresh SharedPreferences - shouldShowTutorial returns true`() {
        val uid = "user_abc123"
        assertTrue(
            "shouldShowTutorial should return true when no completion key exists",
            controller.shouldShowTutorial(uid)
        )
    }

    /**
     * Property 1 (negative): After markComplete → shouldShowTutorial returns false.
     *
     * **Validates: Requirements 1.2, 4.1**
     */
    @Test
    fun `after markComplete - shouldShowTutorial returns false`() {
        val uid = "user_abc123"
        val written = controller.markComplete(uid)
        assertTrue("markComplete should return true on success", written)
        assertFalse(
            "shouldShowTutorial should return false after markComplete",
            controller.shouldShowTutorial(uid)
        )
    }

    // -------------------------------------------------------------------------
    // Property 1 — property-based test with arbitrary UID strings
    // Validates: Requirements 1.1, 1.2, 1.3
    // -------------------------------------------------------------------------

    /**
     * Property-based test: Property 1 holds for a wide variety of UID strings,
     * including empty string, very long strings, and strings with special characters.
     *
     * **Validates: Requirements 1.1, 1.2, 1.3**
     */
    @Test
    fun `property 1 holds for arbitrary UID strings`() {
        val arbitraryUids = buildList {
            // Empty UID
            add("")
            // Short UIDs
            add("a")
            add("1")
            // Typical Firebase UID (28 chars)
            add("aBcDeFgHiJkLmNoPqRsTuVwXyZ12")
            // 128-character UID
            add("a".repeat(128))
            // Special characters
            add("uid with spaces")
            add("uid/with/slashes")
            add("uid.with.dots")
            add("uid@with#special\$chars%^&*()")
            add("uid\nwith\nnewlines")
            add("uid\twith\ttabs")
            add("uid\"with\"quotes")
            add("uid'with'single'quotes")
            add("uid\\with\\backslashes")
            add("uid<with>angle<brackets>")
            add("uid{with}braces")
            add("uid[with]brackets")
            // Unicode characters
            add("uid_\u4e2d\u6587")
            add("uid_\uD83D\uDE00") // emoji
            // Numeric-only
            add("1234567890")
            // Mixed case
            add("User_ABC_123")
        }

        for (uid in arbitraryUids) {
            // Reset prefs for each UID to ensure isolation
            prefs.edit().clear().commit()
            // Re-create controller so it picks up the cleared prefs
            val freshController = TutorialController(context)

            // Before completion: should show tutorial
            assertTrue(
                "shouldShowTutorial should return true for fresh prefs with uid='$uid'",
                freshController.shouldShowTutorial(uid)
            )

            // After completion: should NOT show tutorial
            freshController.markComplete(uid)
            assertFalse(
                "shouldShowTutorial should return false after markComplete with uid='$uid'",
                freshController.shouldShowTutorial(uid)
            )
        }
    }

    // -------------------------------------------------------------------------
    // Property 2: Completion Persistence (simulates process restart)
    // Validates: Requirements 4.1, 4.3
    // -------------------------------------------------------------------------

    /**
     * Property 2: A new TutorialController instance sharing the same SharedPreferences
     * after markComplete returns false for shouldShowTutorial — simulating app restart.
     *
     * **Validates: Requirements 4.1, 4.3**
     */
    @Test
    fun `new TutorialController instance after markComplete - shouldShowTutorial returns false`() {
        val uid = "user_restart_test"

        // First instance marks complete
        controller.markComplete(uid)

        // Simulate process restart: create a brand-new TutorialController instance
        // backed by the same SharedPreferences file
        val restartedController = TutorialController(context)
        assertFalse(
            "shouldShowTutorial should return false on a new instance after markComplete (simulates restart)",
            restartedController.shouldShowTutorial(uid)
        )
    }

    // -------------------------------------------------------------------------
    // Property 3: No Cross-User Contamination
    // Validates: Requirements 1.3, 4.1
    // -------------------------------------------------------------------------

    /**
     * Property 3: markComplete for uid1 must not affect shouldShowTutorial for uid2.
     *
     * **Validates: Requirements 1.3, 4.1**
     */
    @Test
    fun `markComplete for uid1 does not affect shouldShowTutorial for uid2`() {
        val uid1 = "user_one"
        val uid2 = "user_two"

        controller.markComplete(uid1)

        assertTrue(
            "shouldShowTutorial for uid2 should still return true after markComplete(uid1)",
            controller.shouldShowTutorial(uid2)
        )
    }

    // -------------------------------------------------------------------------
    // Pending-write retry path
    // Validates: Requirement 4.5
    // -------------------------------------------------------------------------

    /**
     * Pending-write retry path: Simulate a commit() failure by manually setting the
     * pending flag (without setting the completion key). Verify that:
     * 1. shouldShowTutorial returns false (treats as complete regardless of retry outcome)
     * 2. After a successful retry, the pending flag is cleared
     *
     * **Validates: Requirement 4.5**
     */
    @Test
    fun `pending write retry - shouldShowTutorial returns false and clears pending flag`() {
        val uid = "user_pending_retry"
        val completedKey = "onboarding_tutorial_completed_$uid"
        val pendingKey = "onboarding_write_pending_$uid"

        // Simulate the state after a failed commit():
        // - completion key is NOT set (commit failed)
        // - pending flag IS set (written with apply() as fallback)
        prefs.edit()
            .putBoolean(pendingKey, true)
            .remove(completedKey)
            .commit()

        // shouldShowTutorial should return false (treats pending as complete)
        // and internally retry the commit, then clear the pending flag
        val result = controller.shouldShowTutorial(uid)

        assertFalse(
            "shouldShowTutorial should return false when pending flag is set (retry path)",
            result
        )

        // After the retry, the completion key should be written
        assertTrue(
            "completion key should be written after successful retry",
            prefs.getBoolean(completedKey, false)
        )

        // After the retry, the pending flag should be cleared
        assertFalse(
            "pending flag should be cleared after successful retry",
            prefs.getBoolean(pendingKey, false)
        )
    }

    /**
     * Pending-write retry path: After the retry succeeds, a subsequent call to
     * shouldShowTutorial should still return false (completion is durable).
     *
     * **Validates: Requirement 4.5**
     */
    @Test
    fun `after pending retry succeeds - subsequent shouldShowTutorial calls return false`() {
        val uid = "user_pending_subsequent"
        val pendingKey = "onboarding_write_pending_$uid"

        // Set up pending state
        prefs.edit().putBoolean(pendingKey, true).commit()

        // First call triggers retry
        controller.shouldShowTutorial(uid)

        // Subsequent calls should also return false
        assertFalse(
            "shouldShowTutorial should return false on subsequent calls after retry",
            controller.shouldShowTutorial(uid)
        )
    }
}
