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
import com.genzopia.Instagame.webgl_gameloading.MyApplication;
import com.google.firebase.auth.FirebaseAuth;

import kotlin.Unit;

public class SplashActivity extends BaseActivity {
    private static final String TAG = "SplashActivity";
    private static final long MAX_WAIT_MS = 15000;
    private boolean hasNavigated = false;
    private boolean animationComplete = false;
    private boolean dataLoaded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Perf tracking
    private long splashStartMs;
    private long animationEndMs;
    private long prefetchEndMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        splashStartMs = System.currentTimeMillis();

        // Track app open — detect source
        android.net.Uri deepLinkData = getIntent().getData();
        boolean isDeepLink = deepLinkData != null;
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
                checkAndNavigate();
            }
        }, MAX_WAIT_MS);

        setupLottie();
    }

    private void setupLottie() {
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int rawId = isNight ? R.raw.game_logo_dark_theme : R.raw.game_logo_white_theme;

        LottieAnimationView lottieView = findViewById(R.id.lottieView);
        lottieView.enableMergePathsForKitKatAndAbove(true);

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
        if (animationComplete && dataLoaded) {
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
            navigateToNextScreen();
        }
    }

    private void navigateToNextScreen() {
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Intent intent = new Intent(SplashActivity.this,
                isLoggedIn ? MainActivity.class : LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        android.net.Uri data = getIntent().getData();
        if (data != null) {
            String videoId = null;
            String scheme = data.getScheme();
            String host = data.getHost();

            if ("https".equals(scheme) && "instagame.genzopia.com".equals(host)) {
                videoId = data.getLastPathSegment();
            } else if ("instagame".equals(scheme) && "video".equals(host)) {
                videoId = data.getLastPathSegment();
            }

            if (videoId != null && !videoId.isEmpty()) {
                intent.putExtra("deep_link_video_id", videoId);
            }
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}