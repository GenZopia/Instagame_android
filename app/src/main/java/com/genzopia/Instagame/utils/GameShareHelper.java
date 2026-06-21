package com.genzopia.Instagame.utils;

import android.content.Context;
import android.content.Intent;

public class GameShareHelper {

    public static void shareGame(Context context, String gameId, String gameName, String imageUrl) {
        String link = "https://www.genzopia.com/games/" + gameId;
        try {
            link += "?img=" + java.net.URLEncoder.encode(imageUrl != null ? imageUrl : "", "UTF-8")
                    + "&name=" + java.net.URLEncoder.encode(gameName != null ? gameName : "", "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) { /* ignore */ }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Hey! Checkout this game 🎮\n" + link);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out " + gameName);
        context.startActivity(Intent.createChooser(shareIntent, "Share game via"));
    }

    // Backward compat — no image
    public static void shareGame(Context context, String gameId, String gameName) {
        shareGame(context, gameId, gameName, null);
    }

    public static void copyGameLinkToClipboard(Context context, String gameId, String gameName) {
        String link = DeepLinkGenerator.generateGameLink(gameId);
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Game Link: " + gameName, link));
        android.widget.Toast.makeText(context, "Link copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
    }

    public static void shareGameViaApp(Context context, String gameId, String gameName, String packageName, String imageUrl) {
        String link = "https://www.genzopia.com/games/" + gameId;
        try {
            link += "?img=" + java.net.URLEncoder.encode(imageUrl != null ? imageUrl : "", "UTF-8")
                    + "&name=" + java.net.URLEncoder.encode(gameName != null ? gameName : "", "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) { /* ignore */ }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.setPackage(packageName);
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Hey! Checkout this game 🎮\n" + link);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out " + gameName);
        try {
            context.startActivity(shareIntent);
        } catch (android.content.ActivityNotFoundException e) {
            android.widget.Toast.makeText(context, "App not installed", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // Backward compat — no image
    public static void shareGameViaApp(Context context, String gameId, String gameName, String packageName) {
        shareGameViaApp(context, gameId, gameName, packageName, null);
    }

    public static final String WHATSAPP = "com.whatsapp";
    public static final String FACEBOOK = "com.facebook.katana";
    public static final String TWITTER = "com.twitter.android";
    public static final String INSTAGRAM = "com.instagram.android";
    public static final String TELEGRAM = "org.telegram.messenger";
}