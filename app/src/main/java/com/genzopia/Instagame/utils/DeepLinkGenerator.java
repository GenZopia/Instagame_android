package com.genzopia.Instagame.utils;

import com.genzopia.Instagame.BuildConfig;

/**
 * Utility class for generating deep links for games and other content.
 * Uses BASE_DOMAIN from BuildConfig so you can change the domain in one place.
 */
public class DeepLinkGenerator {

    /**
     * Generate a deep link for a game (HTTPS format - requires App Links setup)
     * Example: https://www.genzopia.com/game123
     *
     * @param gameId The ID of the game
     * @return The full deep link URL
     */
    public static String generateGameLink(String gameId) {
        return "https://" + BuildConfig.BASE_DOMAIN + "/" + gameId;
    }

    /**
     * Generate a custom URI scheme link for a game (always works, no server setup needed)
     * Example: genzopia://game/game123
     * Use this for sharing in WhatsApp, SMS, etc. until App Links are verified
     *
     * @param gameId The ID of the game
     * @return The custom URI deep link
     */
    public static String generateGameLinkCustomScheme(String gameId) {
        return "genzopia://game/" + gameId;
    }

    /**
     * Get the base domain from BuildConfig
     * @return The base domain
     */
    public static String getBaseDomain() {
        return BuildConfig.BASE_DOMAIN;
    }

    /**
     * Generate a shareable text for a game (uses custom scheme for reliability)
     * @param gameId The game ID
     * @param gameName The game name
     * @return Share text with deep link
     */
    public static String generateGameShareText(String gameId, String gameName) {
        return "Check out " + gameName + " on Genzopia!\n" + generateGameLinkCustomScheme(gameId);
    }

    /**
     * Generate a shareable text for a game (HTTPS version)
     * Use this once App Links are set up
     * @param gameId The game ID
     * @param gameName The game name
     * @return Share text with HTTPS deep link
     */
    public static String generateGameShareTextHttps(String gameId, String gameName) {
        return "Check out " + gameName + " on Genzopia!\n" + generateGameLink(gameId);
    }
}
