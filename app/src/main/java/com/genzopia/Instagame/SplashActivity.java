package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.splashscreen.SplashScreen;

import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.webgl_gameloading.MyApplication;
import com.google.firebase.auth.FirebaseAuth;

/**
 * SplashActivity — installs the AndroidX SplashScreen and routes to the next screen.
 *
 * The OS draws the splash icon (windowSplashScreenAnimatedIcon) immediately at launch —
 * zero black frames. We keep the splash visible via setKeepOnScreenCondition until
 * MyApplication signals that prefetch + remote config are done (or timeout fires).
 */
public class SplashActivity extends BaseActivity {

    private static final String TAG = "SplashActivity";
    private static final long MAX_WAIT_MS = 3_000;

    private volatile boolean dataReady = false;
    private boolean hasNavigated = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long splashStartMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be called BEFORE super.onCreate / setContentView
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        splashStartMs = System.currentTimeMillis();

        // Keep splash on screen until data is ready
        splashScreen.setKeepOnScreenCondition(() -> !dataReady);
        // Disable the default exit animation — prevents the icon appearing to "show twice"
        splashScreen.setOnExitAnimationListener(provider -> provider.remove());

        // Track open source
        android.net.Uri deepLinkData = getIntent().getData();
        boolean isDeepLink = deepLinkData != null;
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackAppOpened(
                isDeepLink ? "deep_link" : "cold_start",
                isDeepLink && deepLinkData.getLastPathSegment() != null
                        ? deepLinkData.getLastPathSegment() : null
        );
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("splash");

        // Hard timeout — don't wait forever if network is down
        handler.postDelayed(() -> {
            if (hasNavigated) return;
            Log.w(TAG, "Timeout — navigating without full data");
            com.genzopia.Instagame.utils.RemoteConfigManager rcm = MyApplication.getRemoteConfigManager();
            if (rcm != null && rcm.isForceUpdateRequired()) {
                dataReady = true;
                return;
            }
            dataReady = true;
            navigateToNextScreen();
        }, MAX_WAIT_MS);

        // Navigate as soon as both prefetch + config are done
        MyApplication.whenReady(() -> {
            long totalMs = System.currentTimeMillis() - splashStartMs;
            Log.d(TAG, "App data ready in " + totalMs + "ms");
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackSplashLoadTime(0, totalMs, totalMs);
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackSplashCompleted(totalMs, true);
            onAppDataReady();
        });
    }

    private void onAppDataReady() {
        com.genzopia.Instagame.utils.RemoteConfigManager rcm = MyApplication.getRemoteConfigManager();

        if (rcm != null) {
            if (rcm.isForceUpdateRequired()) {
                // Don't navigate — MyApplication's enforcer will show the dialog
                // on whichever activity is in the foreground. Just reveal the splash.
                dataReady = true;
                return;
            }

            boolean showSmooth = rcm.isSmoothUpdateAvailable() && shouldShowSmoothUpdate();
            getSharedPreferences("update_prefs", MODE_PRIVATE).edit()
                    .putBoolean("pending_smooth_update", showSmooth)
                    .putString("smooth_min_version", rcm.getSmoothMinVersionString())
                    .apply();
        }

        dataReady = true;
        navigateToNextScreen();
    }

    private void navigateToNextScreen() {
        if (hasNavigated) return;
        hasNavigated = true;

        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Intent intent = new Intent(this, isLoggedIn ? MainActivity.class : LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.net.Uri data = getIntent().getData();
        if (data != null) {
            String gameId = resolveGameId(data);
            if (gameId != null && !gameId.isEmpty()) {
                intent.putExtra("deep_link_game_id", gameId);
            }
        }

        startActivity(intent);
        finish();
    }

    private String resolveGameId(android.net.Uri data) {
        String scheme = data.getScheme();
        String host = data.getHost();
        if (("https".equals(scheme) || "http".equals(scheme)) &&
                ("www.genzopia.com".equals(host) || "genzopia.com".equals(host))) {
            String path = data.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                return path.startsWith("/games/")
                        ? path.substring("/games/".length())
                        : (path.startsWith("/") ? path.substring(1) : path);
            }
        } else if ("genzopia".equals(scheme) && "game".equals(host)) {
            return data.getLastPathSegment();
        }
        return null;
    }

    private boolean shouldShowSmoothUpdate() {
        android.content.SharedPreferences prefs = getSharedPreferences("update_prefs", MODE_PRIVATE);
        int count = prefs.getInt("app_open_count", 0) + 1;
        prefs.edit().putInt("app_open_count", count % 3).apply();
        return count % 3 == 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
