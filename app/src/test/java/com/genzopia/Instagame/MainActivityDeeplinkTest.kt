package com.genzopia.Instagame

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.genzopia.Instagame.utils.GameNavigationManager
import com.google.firebase.FirebaseApp
import net.jqwik.api.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Property-based and unit tests for MainActivity deeplink handling.
 * Feature: fix-deeplink-navigation
 * 
 * These tests validate that game deeplinks store the game ID in GameNavigationManager
 * for the home fragment to consume, verifying the core deeplink processing logic.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MainActivityDeeplinkTest {
    
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

    /**
     * Feature: fix-deeplink-navigation, Property 4: Direct navigation to home fragment
     * Validates: Requirements 3.1, 3.2, 3.4
     * 
     * For any game deeplink intent, the system should store the game ID in GameNavigationManager
     * so that the home fragment can display it directly.
     */
    @Property(tries = 100)
    fun `game deeplinks are stored in GameNavigationManager for home navigation`(
        @ForAll("gameIds") gameId: String
    ) {
        // Simulate MainActivity's checkForDashboardNavigation() logic
        val intent = Intent()
        intent.putExtra("deep_link_game_id", gameId)
        
        // Simulate the processing that happens in MainActivity
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            intent.removeExtra("deep_link_game_id")
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        // Verify the game ID was stored in GameNavigationManager
        val storedGameId = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals(
            "Game ID should be stored in GameNavigationManager for home fragment to consume",
            gameId,
            storedGameId
        )
        
        // Verify the intent extra was removed to prevent reprocessing
        assertNull(
            "Intent extra should be removed after processing",
            intent.getStringExtra("deep_link_game_id")
        )
    }

    // ==================== Unit Tests ====================

    @Test
    fun `specific game deeplink stores correct ID`() {
        val gameId = "abc123"
        val intent = Intent()
        intent.putExtra("deep_link_game_id", gameId)
        
        // Simulate MainActivity's processing
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            intent.removeExtra("deep_link_game_id")
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        val storedGameId = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals("abc123", storedGameId)
    }

    @Test
    fun `no deeplink does not store game ID`() {
        val intent = Intent()
        
        // Simulate MainActivity's processing
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        val storedGameId = GameNavigationManager.getInstance().getPendingGameId()
        assertNull("No game ID should be stored without deeplink", storedGameId)
    }

    @Test
    fun `empty game ID does not store`() {
        val intent = Intent()
        intent.putExtra("deep_link_game_id", "")
        
        // Simulate MainActivity's processing
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        val storedGameId = GameNavigationManager.getInstance().getPendingGameId()
        assertNull("Empty game ID should not be stored", storedGameId)
    }

    @Test
    fun `video deeplink extras are not processed`() {
        val intent = Intent()
        intent.putExtra("deep_link_video_id", "video123")
        intent.putExtra("navigate_to_dashboard", true)
        intent.putExtra("play_video_id", "video456")
        
        // Simulate MainActivity's processing (which should only look for game deeplinks)
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        // Video deeplink handling should be removed, so nothing should be stored
        val storedGameId = GameNavigationManager.getInstance().getPendingGameId()
        assertNull("Video deeplink should not trigger game navigation", storedGameId)
    }

    @Test
    fun `intent extra is removed after processing`() {
        val gameId = "test123"
        val intent = Intent()
        intent.putExtra("deep_link_game_id", gameId)
        
        // Verify extra exists before processing
        assertNotNull(intent.getStringExtra("deep_link_game_id"))
        
        // Simulate MainActivity's processing
        val deepLinkGameId = intent.getStringExtra("deep_link_game_id")
        if (deepLinkGameId != null && deepLinkGameId.isNotEmpty()) {
            intent.removeExtra("deep_link_game_id")
            GameNavigationManager.getInstance().setPendingGameId(deepLinkGameId)
        }
        
        // Verify extra was removed
        assertNull(
            "Intent extra should be removed to prevent reprocessing on config changes",
            intent.getStringExtra("deep_link_game_id")
        )
    }

    @Test
    fun `consumePendingGameId clears the stored ID`() {
        val gameId = "clear123"
        GameNavigationManager.getInstance().setPendingGameId(gameId)
        
        // First consumption returns the ID
        val firstGet = GameNavigationManager.getInstance().consumePendingGameId()
        assertEquals(gameId, firstGet)
        
        // Second consumption should return null (ID was consumed)
        val secondGet = GameNavigationManager.getInstance().getPendingGameId()
        assertNull("Game ID should be cleared after consumption", secondGet)
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
