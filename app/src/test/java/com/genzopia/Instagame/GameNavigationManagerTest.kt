package com.genzopia.Instagame

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.genzopia.Instagame.utils.GameNavigationManager
import com.google.firebase.FirebaseApp
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Unit tests for GameNavigationManager deeplink integration.
 * Feature: fix-deeplink-navigation
 * 
 * These tests validate that GameNavigationManager correctly stores and retrieves
 * game IDs for deeplink navigation, ensuring HomeFragmentCompose can consume them.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class GameNavigationManagerTest {
    
    @Before
    fun setup() {
        // Initialize Firebase for test environment
        val context = ApplicationProvider.getApplicationContext<Application>()
        try {
            FirebaseApp.initializeApp(context)
        } catch (e: IllegalStateException) {
            // Firebase already initialized, ignore
        }
        // Clear any pending game IDs from previous tests
        GameNavigationManager.getInstance().clearPendingGameId()
    }

    @After
    fun cleanup() {
        GameNavigationManager.getInstance().clearPendingGameId()
    }

    // ==================== Task 4.1: Verify GameNavigationManager stores game IDs correctly ====================

    @Test
    fun `setPendingGameId stores game ID correctly`() {
        val gameId = "test-game-123"
        
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        val retrievedId = GameNavigationManager.getInstance().getPendingGameId()
        assertEquals(
            "setPendingGameId should store the game ID",
            gameId,
            retrievedId
        )
    }

    @Test
    fun `consumePendingGameId retrieves and clears game ID`() {
        val gameId = "consume-test-456"
        
        // Store a game ID
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // Consume should return the ID
        val retrievedId = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals(
            "consumePendingGameId should return the stored game ID",
            gameId,
            retrievedId
        )
        
        // After consumption, ID should be cleared
        val shouldBeNull = GameNavigationManager.getInstance().getPendingGameId()
        assertNull(
            "Game ID should be cleared after consumption to prevent re-opening",
            shouldBeNull
        )
    }

    @Test
    fun `consumePendingGameId prevents re-opening same game`() {
        val gameId = "no-reopen-789"
        
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // First consumption returns the ID
        val firstConsume = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals("First consume should return game ID", gameId, firstConsume)
        
        // Second consumption should return null (already consumed)
        val secondConsume = GameNavigationManager.getInstance().consumePendingGameId()
        assertNull(
            "Second consume should return null to prevent re-opening",
            secondConsume
        )
    }

    @Test
    fun `clearPendingGameId clears stored game ID`() {
        val gameId = "clear-test-321"
        
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        assertNotNull(
            "Game ID should be stored",
            GameNavigationManager.getInstance().getPendingGameId()
        )
        
        GameNavigationManager.getInstance().clearPendingGameId()
        assertNull(
            "Game ID should be cleared",
            GameNavigationManager.getInstance().getPendingGameId()
        )
    }

    @Test
    fun `getPendingGameId does not clear the ID`() {
        val gameId = "get-test-654"
        
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // Multiple getPendingGameId calls should return the same ID
        val first = GameNavigationManager.getInstance().getPendingGameId()
        val second = GameNavigationManager.getInstance().getPendingGameId()
        
        assertEquals("getPendingGameId should return the ID", gameId, first)
        assertEquals("getPendingGameId should not clear the ID", gameId, second)
    }

    @Test
    fun `HomeFragmentCompose can retrieve game ID via consumePendingGameId`() {
        val gameId = "home-fragment-987"
        
        // Simulate MainActivity storing the game ID from a deeplink
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // Simulate HomeFragmentCompose.onResume() consuming the ID
        val consumedId = GameNavigationManager.getInstance().consumePendingGameId()
        
        assertNotNull(
            "HomeFragmentCompose should be able to retrieve the game ID",
            consumedId
        )
        assertEquals(
            "Retrieved game ID should match the stored ID",
            gameId,
            consumedId
        )
        
        // Verify it's cleared after consumption
        assertNull(
            "Game ID should be cleared after HomeFragmentCompose consumes it",
            GameNavigationManager.getInstance().getPendingGameId()
        )
    }

    @Test
    fun `singleton instance is consistent`() {
        val instance1 = GameNavigationManager.getInstance()
        val instance2 = GameNavigationManager.getInstance()
        
        assertSame(
            "getInstance should return the same singleton instance",
            instance1,
            instance2
        )
    }

    @Test
    fun `setting null game ID stores null`() {
        GameNavigationManager.getInstance().setPendingGameId("test")
        assertNotNull(GameNavigationManager.getInstance().getPendingGameId())
        
        GameNavigationManager.getInstance().setPendingGameId(null)
        assertNull(
            "Setting null should clear the game ID",
            GameNavigationManager.getInstance().getPendingGameId()
        )
    }

    @Test
    fun `setting empty string stores empty string`() {
        GameNavigationManager.getInstance().setPendingGameId("")
        
        val retrieved = GameNavigationManager.getInstance().getPendingGameId()
        assertEquals(
            "Empty string should be stored as-is",
            "",
            retrieved
        )
    }

    @Test
    fun `multiple setPendingGameId calls overwrite previous value`() {
        GameNavigationManager.getInstance().setPendingGameId("first-game")
        GameNavigationManager.getInstance().setPendingGameId("second-game")
        
        val retrieved = GameNavigationManager.getInstance().getPendingGameId()
        assertEquals(
            "Latest setPendingGameId should overwrite previous value",
            "second-game",
            retrieved
        )
    }
}
