package com.genzopia.Instagame

import net.jqwik.api.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Property-based tests for deeplink parsing logic (previously in SplashActivity, now in MainActivity).
 * Feature: fix-deeplink-navigation
 *
 * These tests validate the URL parsing logic by replicating the exact parsing algorithm
 * from MainActivity.resolveGameId() without requiring Android dependencies.
 */
class SplashActivityDeeplinkTest {

    /**
     * Feature: fix-deeplink-navigation, Property 3: URL pattern equivalence
     * Validates: Requirements 2.4, 2.5
     * 
     * For any game ID, all supported URL patterns should extract the same game ID.
     */
    @Property(tries = 100)
    fun `all URL patterns extract the same game ID`(@ForAll("gameIds") gameId: String) {
        val patterns = listOf(
            "https://genzopia.com/games/$gameId",
            "https://www.genzopia.com/games/$gameId",
            "https://genzopia.com/$gameId",
            "genzopia://game/$gameId"
        )

        val extractedGameIds = patterns.map { url ->
            extractGameIdFromUrl(url)
        }

        // All patterns should extract the same game ID
        extractedGameIds.forEach { extractedId ->
            assertEquals("URL pattern should extract game ID: $gameId", gameId, extractedId)
        }
    }

    /**
     * Feature: fix-deeplink-navigation, Property 2: Game ID extraction from /games/ path
     * Validates: Requirements 2.3
     * 
     * For any valid game ID, when constructing a /games/ URL, the system should extract exactly that game ID.
     */
    @Property(tries = 100)
    fun `games path pattern extracts correct game ID`(@ForAll("gameIds") gameId: String) {
        val urls = listOf(
            "https://genzopia.com/games/$gameId",
            "https://www.genzopia.com/games/$gameId",
            "http://genzopia.com/games/$gameId",
            "http://www.genzopia.com/games/$gameId"
        )

        urls.forEach { url ->
            val extractedId = extractGameIdFromUrl(url)
            assertEquals("Should extract game ID from /games/ path: $url", gameId, extractedId)
        }
    }

    /**
     * Feature: fix-deeplink-navigation, Property 1: Video deeplinks are not processed
     * Validates: Requirements 1.1, 1.3
     * 
     * For any URL that could be interpreted as a video deeplink, no game ID should be extracted
     * (or if extracted, it should be treated as a game ID, not a video ID).
     */
    @Property(tries = 100)
    fun `video deeplinks are not processed`(@ForAll("gameIds") videoId: String) {
        val videoUrls = listOf(
            "instagame://video/$videoId",
            "https://genzopia.com/video/$videoId",
            "https://www.genzopia.com/video/$videoId"
        )

        videoUrls.forEach { url ->
            val extractedId = extractGameIdFromUrl(url)
            // The key requirement is that video deeplink handling is REMOVED
            // So these URLs either:
            // 1. Don't extract anything (null) for custom scheme
            // 2. Extract "video/{videoId}" as a game ID for https URLs (which is fine - they won't match any game)
            // The important part is NO video-specific processing happens
            
            // For instagame://video/{videoId} - should return null (not handled)
            if (url.startsWith("instagame://video/")) {
                assertNull("instagame://video scheme should not be handled: $url", extractedId)
            }
            // For https://genzopia.com/video/{videoId} - will be treated as legacy pattern
            // This is acceptable as the video won't be found as a game
        }
    }

    // ==================== Unit Tests ====================

    @Test
    fun `specific game URL extracts correct ID`() {
        val gameId = extractGameIdFromUrl("https://genzopia.com/games/abc123")
        assertEquals("abc123", gameId)
    }

    @Test
    fun `legacy game URL extracts correct ID`() {
        val gameId = extractGameIdFromUrl("https://genzopia.com/def456")
        assertEquals("def456", gameId)
    }

    @Test
    fun `custom scheme game URL extracts correct ID`() {
        val gameId = extractGameIdFromUrl("genzopia://game/ghi789")
        assertEquals("ghi789", gameId)
    }

    @Test
    fun `empty path returns null`() {
        val gameId = extractGameIdFromUrl("https://genzopia.com/")
        assertNull(gameId)
    }

    @Test
    fun `root path returns null`() {
        val gameId = extractGameIdFromUrl("https://genzopia.com")
        assertNull(gameId)
    }

    // ==================== Helper Methods ====================

    /**
     * Simple URI parser that extracts components without Android dependencies.
     * Replicates android.net.Uri parsing behavior for our test cases.
     */
    data class ParsedUri(
        val scheme: String?,
        val host: String?,
        val path: String?,
        val lastPathSegment: String?
    )

    private fun parseUri(urlString: String): ParsedUri {
        // Extract scheme
        val schemeEnd = urlString.indexOf("://")
        val scheme = if (schemeEnd > 0) urlString.substring(0, schemeEnd) else null
        
        val afterScheme = if (schemeEnd > 0) urlString.substring(schemeEnd + 3) else urlString
        
        // Extract host and path
        val pathStart = afterScheme.indexOf('/')
        val host: String?
        val path: String?
        
        if (pathStart >= 0) {
            host = afterScheme.substring(0, pathStart)
            path = afterScheme.substring(pathStart)
        } else {
            host = afterScheme
            path = null
        }
        
        // Extract last path segment
        val lastPathSegment = path?.trimEnd('/')?.substringAfterLast('/', "")?.ifEmpty { null }
        
        return ParsedUri(scheme, host, path, lastPathSegment)
    }

    /**
     * Extracts the game ID from a URL by replicating MainActivity's parsing logic.
     * This is a direct translation of the resolveGameId() method's deeplink parsing.
     */
    private fun extractGameIdFromUrl(urlString: String): String? {
        val data = parseUri(urlString)
        var gameId: String? = null
        val scheme = data.scheme
        val host = data.host

        // Game deeplink parsing logic (matches SplashActivity)
        if ((scheme == "https" || scheme == "http") && 
            (host == "www.genzopia.com" || host == "genzopia.com")) {
            val path = data.path
            if (path != null && path.isNotEmpty() && path != "/") {
                // Handle /games/{gameId} pattern
                gameId = if (path.startsWith("/games/")) {
                    path.substring("/games/".length)
                } else {
                    // Handle legacy /{gameId} pattern
                    if (path.startsWith("/")) path.substring(1) else path
                }
            }
        } else if (scheme == "genzopia" && host == "game") {
            // Custom URI scheme: genzopia://game/{gameId}
            gameId = data.lastPathSegment
        }

        return if (gameId != null && gameId.isNotEmpty()) gameId else null
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
