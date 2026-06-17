package com.genzopia.Instagame.utils;

import android.content.Context;
import android.content.Intent;

/**
 * Helper class to share games using deep links.
 * Example usage of DeepLinkGenerator.
 */
public class GameShareHelper {

    /**
     * Share a game via Android share sheet
     *
     * @param context Android context
     * @param gameId The game ID to share
     * @param gameName The name of the game
     */
    public static void shareGame(Context context, String gameId, String gameName) {
        String shareText = DeepLinkGenerator.generateGameShareText(gameId, gameName);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out " + gameName);
        
        Intent chooser = Intent.createChooser(shareIntent, "Share game via");
        context.startActivity(chooser);
    }

    /**
     * Copy game link to clipboard
     *
     * @param context Android context
     * @param gameId The game ID
     * @param gameName The name of the game (for clipboard label)
     */
    public static void copyGameLinkToClipboard(Context context, String gameId, String gameName) {
        String link = DeepLinkGenerator.generateGameLink(gameId);
        
        android.content.ClipboardManager clipboard = 
            (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(
            "Game Link: " + gameName, 
            link
        );
        clipboard.setPrimaryClip(clip);
        
        // Show toast
        android.widget.Toast.makeText(
            context, 
            "Link copied to clipboard", 
            android.widget.Toast.LENGTH_SHORT
        ).show();
    }

    /**
     * Share game via specific social media apps
     *
     * @param context Android context
     * @param gameId The game ID
     * @param gameName The game name
     * @param packageName Package name of the app (e.g., "com.whatsapp")
     */
    public static void shareGameViaApp(Context context, String gameId, String gameName, String packageName) {
        String shareText = DeepLinkGenerator.generateGameShareText(gameId, gameName);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.setPackage(packageName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out " + gameName);
        
        try {
            context.startActivity(shareIntent);
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(
                context,
                "App not installed",
                android.widget.Toast.LENGTH_SHORT
            ).show();
        }
    }

    // Common social media package names
    public static final String WHATSAPP = "com.whatsapp";
    public static final String FACEBOOK = "com.facebook.katana";
    public static final String TWITTER = "com.twitter.android";
    public static final String INSTAGRAM = "com.instagram.android";
    public static final String TELEGRAM = "org.telegram.messenger";
}
