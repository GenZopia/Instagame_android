package com.genzopia.Instagame

import com.genzopia.Instagame.utils.GameNavigationManager
import net.jqwik.api.*
import org.junit.Assert.*

/**
 * Property-based tests for GameNavigationManager deeplink integration.
 * Feature: fix-deeplink-navigation
 * 
 * These tests validate that GameNavigationManager correctly stores and retrieves
 * game IDs for deeplink navigation using property-based testing.
 */
class GameNavigationManagerPropertyTest {

    /**
     * Feature: fix-deeplink-navigation, Property 5: Game sheet opens for deeplinked game
     * Validates: Requirements 3.3
     * 
     * For any game ID, when stored in GameNavigationManager via setPendingGameId(),
     * it should be correctly retrievable via consumePendingGameId(), ensuring the
     * home fragment can open the game sheet for that specific game ID.
     */
    @Property(tries = 100)
    fun `property 5 - game sheet opens for deeplinked game`(
        @ForAll("gameIds") gameId: String
    ) {
        // Clear any previous state
        GameNavigationManager.getInstance().clearPendingGameId()
        
        // Simulate MainActivity storing a game ID from a deeplink
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // Verify the game ID is stored correctly
        val storedId = GameNavigationManager.getInstance().getPendingGameId()
        assertEquals(
            "Game ID should be stored correctly in GameNavigationManager",
            gameId,
            storedId
        )
        
        // Simulate HomeFragmentCompose consuming the game ID to open the game sheet
        val consumedId = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals(
            "HomeFragmentCompose should retrieve the exact game ID to open game sheet",
            gameId,
            consumedId
        )
        
        // Verify the ID is cleared after consumption
        val afterConsume = GameNavigationManager.getInstance().getPendingGameId()
        assertNull(
            "Game ID should be cleared after consumption to prevent re-opening",
            afterConsume
        )
    }

    // ==================== Arbitraries ====================

    /**
     * Provides arbitrary game IDs for property-based testing.
     * Game IDs are alphanumeric strings of length 5-20.
     */
    @Provide
    fun gameIds(): Arbitrary<String> {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .withCharRange('A', 'Z')
            .withCharRange('0', '9')
            .ofMinLength(5)
            .ofMaxLength(20)
    }
}
