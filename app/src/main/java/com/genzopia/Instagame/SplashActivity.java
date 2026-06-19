package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.utils.DataPrefetchService;
import com.genzopia.Instagame.utils.RemoteConfigManager;
import com.genzopia.Instagame.webgl_gameloading.MyApplication;
import com.google.firebase.auth.FirebaseAuth;

import kotlin.Unit;

public class SplashActivity extends BaseActivity {
    private static final String TAG = "SplashActivity";
    private static final long MAX_WAIT_MS = 15000;
    private boolean hasNavigated = false;
    private boolean animationComplete = false;
    private boolean dataLoaded = false;
    private boolean configFetched = false;
    private boolean isDeepLink = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RemoteConfigManager remoteConfigManager;

    // Perf tracking
    private long splashStartMs;
    private long animationEndMs;
    private long prefetchEndMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        splashStartMs = System.currentTimeMillis();

        remoteConfigManager = new RemoteConfigManager();
        // Req 10.1: fetch config async — doesn't block animation or prefetch
        remoteConfigManager.fetchConfig(success -> {
            Log.d(TAG, "Remote Config fetch complete, success=" + success);
            configFetched = true;
            checkAndNavigate();
            return kotlin.Unit.INSTANCE;
        });

        // Track app open — detect source
        android.net.Uri deepLinkData = getIntent().getData();
        isDeepLink = deepLinkData != null;
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackAppOpened(
                isDeepLink ? "deep_link" : "cold_start",
                isDeepLink ? deepLinkData.getLastPathSegment() : null
        );
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("splash");

        // Start data prefetch immediately
        startDataPrefetch();

        // Hard timeout fallback
        handler.postDelayed(() -> {
            if (!hasNavigated) {
                Log.w(TAG, "Timeout reached — navigating without full prefetch");
                dataLoaded = true;
                configFetched = true; // Req 2.5: on failure, allow app to continue
                checkAndNavigate();
            }
        }, MAX_WAIT_MS);

        if (isDeepLink) {
            // Show static logo from drawable when opened via deeplink (no animation)
            LottieAnimationView lottieView = findViewById(R.id.lottieView);
            lottieView.setVisibility(View.GONE);
            
            android.widget.ImageView staticLogoView = findViewById(R.id.staticLogoView);
            staticLogoView.setImageResource(R.drawable.playstore4);
            staticLogoView.setVisibility(View.VISIBLE);
            
            // Skip animation, mark as complete immediately
            animationComplete = true;
            Log.d(TAG, "Deeplink detected - showing static logo");
        } else {
            // Show logo animation for normal app launch
            setupLottie();
        }
    }

    private void setupLottie() {
        // Hide static logo FIRST for normal launch
        android.widget.ImageView staticLogoView = findViewById(R.id.staticLogoView);
        if (staticLogoView != null) {
            staticLogoView.setVisibility(View.GONE);
            Log.d(TAG, "Static logo hidden for normal launch");
        }
        
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int rawId = isNight ? R.raw.game_logo_dark_theme : R.raw.game_logo_white_theme;

        LottieAnimationView lottieView = findViewById(R.id.lottieView);
        if (lottieView != null) {
            lottieView.setVisibility(View.VISIBLE);
            lottieView.enableMergePathsForKitKatAndAbove(true);
            Log.d(TAG, "Lottie view shown for normal launch");
        }

        lottieView.addAnimatorListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                Log.d(TAG, "Animation complete");
                animationEndMs = System.currentTimeMillis();
                animationComplete = true;
                checkAndNavigate();
            }
        });

        waitForCompositionAndPlay(lottieView, rawId, 0);
    }


    private void waitForCompositionAndPlay(LottieAnimationView lottieView, int rawId, int attempt) {
        LottieComposition cached = MyApplication.getCachedComposition();
        if (cached != null) {
            Log.d("LottiePrewarm", "✅ Used cached on attempt " + attempt);
            lottieView.setComposition(cached);
            lottieView.setMinProgress(0.05f);
            lottieView.setProgress(0.05f);
            lottieView.playAnimation();
        } else if (attempt < 20) {
            // ✅ Poll every 16ms (1 frame) max 20 times = 320ms max wait
            Log.d("LottiePrewarm", "⏳ Waiting for cache attempt " + attempt);
            handler.postDelayed(() -> waitForCompositionAndPlay(lottieView, rawId, attempt + 1), 16);
        } else {
            // Fallback — load directly
            Log.d("LottiePrewarm", "⚠️ Fallback to direct load");
            LottieCompositionFactory.fromRawRes(this, rawId)
                    .addListener(composition -> {
                        lottieView.setComposition(composition);
                        lottieView.setMinProgress(0.05f);
                        lottieView.setProgress(0.05f);
                        lottieView.playAnimation();
                    });
        }
    }
    @OptIn(markerClass = UnstableApi.class)
    private void startDataPrefetch() {
        DataPrefetchService.INSTANCE.startPrefetch(this, () -> {
            Log.d(TAG, "Prefetch callback received");
            prefetchEndMs = System.currentTimeMillis();
            dataLoaded = true;
            checkAndNavigate();
            return Unit.INSTANCE;
        });
    }

    private void checkAndNavigate() {
        if (hasNavigated) return;
        if (animationComplete && dataLoaded && configFetched) {
            hasNavigated = true;
            long now = System.currentTimeMillis();
            long animDuration = animationEndMs > 0 ? animationEndMs - splashStartMs : 0;
            long prefetchDuration = prefetchEndMs > 0 ? prefetchEndMs - splashStartMs : 0;
            long totalDuration = now - splashStartMs;
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackSplashLoadTime(
                    animDuration, prefetchDuration, totalDuration
            );
            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackSplashCompleted(
                    totalDuration, dataLoaded
            );
            // Check force update first (non-dismissible)
            if (remoteConfigManager.isForceUpdateRequired()) {
                String minVersion = remoteConfigManager.getForceMinVersionString();
                ForceUpdateDialog dialog = ForceUpdateDialog.newInstance(minVersion);
                dialog.show(getSupportFragmentManager(), ForceUpdateDialog.TAG);
                // Don't navigate — block here until user updates
            } else {
                // Store smooth update flag for HomeFragment to show (once every 5 opens)
                boolean showSmooth = remoteConfigManager.isSmoothUpdateAvailable() && shouldShowSmoothUpdate();
                getSharedPreferences("update_prefs", MODE_PRIVATE)
                        .edit()
                        .putBoolean("pending_smooth_update", showSmooth)
                        .putString("smooth_min_version", remoteConfigManager.getSmoothMinVersionString())
                        .apply();
                navigateToNextScreen();
            }
        }
    }

    private void navigateToNextScreen() {
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Intent intent = new Intent(SplashActivity.this,
                isLoggedIn ? MainActivity.class : LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.net.Uri data = getIntent().getData();
        if (data != null) {
            String gameId = null;
            String scheme = data.getScheme();
            String host = data.getHost();

            // Handle game deep links: genzopia.com/games/{gameId}, www.genzopia.com/games/{gameId}, 
            // genzopia.com/{gameId}, www.genzopia.com/{gameId}, or genzopia://game/{gameId}
            if (("https".equals(scheme) || "http".equals(scheme)) && 
                    ("www.genzopia.com".equals(host) || "genzopia.com".equals(host))) {
                String path = data.getPath();
                if (path != null && !path.isEmpty() && !"/".equals(path)) {
                    // Handle /games/{gameId} pattern
                    if (path.startsWith("/games/")) {
                        gameId = path.substring("/games/".length());
                    } 
                    // Handle legacy /{gameId} pattern
                    else {
                        gameId = path.startsWith("/") ? path.substring(1) : path;
                    }
                }
            }
            // Custom URI scheme: genzopia://game/{gameId}
            else if ("genzopia".equals(scheme) && "game".equals(host)) {
                gameId = data.getLastPathSegment();
            }

            if (gameId != null && !gameId.isEmpty()) {
                intent.putExtra("deep_link_game_id", gameId);
            }
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    /**
     * Increments the app-open counter and returns true only on every 5th open.
     * Counter is stored in SharedPreferences and resets after reaching 5.
     */
    private boolean shouldShowSmoothUpdate() {
        android.content.SharedPreferences prefs = getSharedPreferences("update_prefs", MODE_PRIVATE);
        int count = prefs.getInt("app_open_count", 0) + 1;
        prefs.edit().putInt("app_open_count", count % 5).apply();
        return count % 5 == 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}