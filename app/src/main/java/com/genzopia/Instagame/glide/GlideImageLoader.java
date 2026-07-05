package com.genzopia.Instagame.glide;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaderFactory;
import com.bumptech.glide.load.model.LazyHeaders;
import com.genzopia.Instagame.BuildConfig;

/**
 * Loads profile photos that require x-api-key + Bearer token.
 *
 * The token is fetched once per session on a background thread and cached.
 * Glide.load() is called on the main thread with the cached token.
 * Falls back to x-api-key only if no token is available.
 */
public final class GlideImageLoader {

    private static final String TAG = "GlideImageLoader";
    private static final String MEDIA_PATH = "/media/file";

    // Cached on first successful fetch; cleared on sign-out
    private static volatile String sCachedIdToken = null;

    /**
     * Call this once after sign-in to pre-warm the token cache.
     * Safe to call from any thread.
     */
    public static void warmToken() {
        new Thread(() -> {
            try {
                com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (user == null) return;
                com.google.android.gms.tasks.Tasks.await(
                    user.getIdToken(false).addOnSuccessListener(result -> {
                        sCachedIdToken = result.getToken();
                        Log.d(TAG, "Token cached for image loading");
                    })
                );
            } catch (Exception e) {
                Log.w(TAG, "Token warm failed: " + e.getMessage());
            }
        }).start();
    }

    public static void clearToken() {
        sCachedIdToken = null;
    }

    /**
     * Returns a valid Firebase ID token for the current user.
     * Returns the cached token when available; otherwise fetches one synchronously.
     * MUST be called off the main thread (Glide invokes header factories on its
     * background executor).
     */
    private static String resolveIdToken() {
        String cached = sCachedIdToken;
        if (cached != null) return cached;
        try {
            com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) return null;
            com.google.firebase.auth.GetTokenResult result =
                com.google.android.gms.tasks.Tasks.await(user.getIdToken(false));
            String token = result.getToken();
            if (token != null) {
                sCachedIdToken = token;
            }
            return token;
        } catch (Exception e) {
            Log.w(TAG, "Synchronous token fetch failed: " + e.getMessage());
            return null;
        }
    }

    /** Used by MyApplication.setupCoil() interceptor */
    public static String getCachedToken() {
        return sCachedIdToken;
    }

    public static void load(Context context, String url,
                            @DrawableRes int placeholder, ImageView into) {
        if (url == null || url.isEmpty()) {
            Glide.with(context).load(placeholder).into(into);
            return;
        }

        String gatewayBase = BuildConfig.GATEWAY_BASE_URL.replaceAll("/$", "");

        // Gateway media URL — needs x-api-key + Bearer
        if (!gatewayBase.isEmpty() ) {
            Log.d(TAG, "loading gateway media: " + url);

            LazyHeaders.Builder headers = new LazyHeaders.Builder()
                .addHeader("x-api-key", BuildConfig.GATEWAY_API_KEY)
                // Resolve the Bearer token lazily (on Glide's background thread) so the
                // request always carries a valid token, even if warmToken() has not
                // finished caching it yet. Falls back to x-api-key only if unavailable.
                .addHeader("Authorization", new LazyHeaderFactory() {
                    @Override
                    public String buildHeader() {
                        String token = resolveIdToken();
                        return token != null ? "Bearer " + token : null;
                    }
                });

            GlideUrl glideUrl = new GlideUrl(url, headers.build());
            Log.e("test2000",glideUrl.toString());
            Glide.with(context)
                .load(glideUrl)
                // Glide's default network timeout is only 2500ms, which makes
                // profile photos intermittently fail on slower connections.
                // Give it a much larger window so images load reliably.
                .timeout(60000)
                .placeholder(placeholder)
                .error(placeholder)
                .into(into);
            return;
        }

        // All other URLs (Google photos, CDN, etc.)
        Glide.with(context)
            .load(url)
            .timeout(60000)
            .placeholder(placeholder)
            .error(placeholder)
            .into(into);
        Log.e("test2000",url);
    }
}
