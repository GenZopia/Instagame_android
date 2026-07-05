package com.genzopia.Instagame.webgl_gameloading;

import android.app.Application;
import android.os.Bundle;
import android.util.Log;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import coil.Coil;
import coil.ImageLoader;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.genzopia.Instagame.BuildConfig;
import com.genzopia.Instagame.ForceUpdateDialog;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.utils.DataPrefetchService;
import com.genzopia.Instagame.utils.RemoteConfigManager;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;

/**
 * Application class.
 *
 * Kicks off DataPrefetchService and RemoteConfigManager as early as possible
 * (process start) so MainActivity only needs to wait briefly before showing the UI.
 */
public class MyApplication extends Application {

    // ── App-level prefetch state ──────────────────────────────────────────
    private static volatile boolean prefetchDone = false;
    private static volatile boolean configDone = false;
    private static volatile RemoteConfigManager remoteConfigManager;
    private static final List<Runnable> readyCallbacks = new ArrayList<>();
    private static final Object lock = new Object();
    // Tracked by ActivityLifecycleCallbacks — the currently resumed FragmentActivity (if any)
    private androidx.fragment.app.FragmentActivity currentActivity = null;
    // Prevents smooth update dialog from showing more than once per app session
    private boolean smoothUpdateShownThisSession = false;

    public static boolean isPrefetchDone() { return prefetchDone; }
    public static boolean isConfigDone() { return configDone; }
    public static boolean isAppDataReady() { return configDone; }

    public static RemoteConfigManager getRemoteConfigManager() { return remoteConfigManager; }

    /** Invoked on main thread as soon as remote config is done — prefetch continues in background. */
    public static void whenReady(Runnable callback) {
        synchronized (lock) {
            if (configDone) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(callback);
            } else {
                readyCallbacks.add(callback);
            }
        }
    }

    private static void notifyIfReady() {
        synchronized (lock) {
            if (!configDone) return;
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            for (Runnable cb : readyCallbacks) main.post(cb);
            readyCallbacks.clear();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupCoil();
        prewarmLottie();

        // Init analytics first so all subsequent events are captured
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.init(this);
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppCreated();
        reIdentifyReturningUser();

        // ── Start prefetch + config fetch as early as possible ───────────
        startAppPrefetch();

        // ── Enforce force-update globally on any foreground activity ─────
        registerForceUpdateEnforcer();

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppForegrounded();
            }

            @Override
            public void onStop(LifecycleOwner owner) {
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppBackgrounded();
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.flushEvents();
            }
        });
    }

    /**
     * Registers an ActivityLifecycleCallbacks that watches for the first resumed
     * FragmentActivity after config is loaded. If a force-update is required it shows
     * ForceUpdateDialog on that activity — regardless of which activity was launched.
     * Also handles smooth-update: shown in MainActivity once per every 3 app opens.
     */
    private void registerForceUpdateEnforcer() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(android.app.Activity activity) {
                if (!(activity instanceof androidx.fragment.app.FragmentActivity)) return;
                currentActivity = (androidx.fragment.app.FragmentActivity) activity;
                if (!configDone) return;
                showUpdateDialogsIfNeeded(currentActivity);
            }

            @Override public void onActivityPaused(android.app.Activity a) {
                if (a == currentActivity) currentActivity = null;
            }

            @Override public void onActivityCreated(android.app.Activity a, Bundle b) {}
            @Override public void onActivityStarted(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
    }

    private void showUpdateDialogsIfNeeded(androidx.fragment.app.FragmentActivity activity) {
        RemoteConfigManager rcm = remoteConfigManager;
        if (rcm == null) return;

        // Force update — blocks everything, shown on any activity
        if (rcm.isForceUpdateRequired()) {
            if (activity.getSupportFragmentManager()
                    .findFragmentByTag(com.genzopia.Instagame.ForceUpdateDialog.TAG) != null) return;
            com.genzopia.Instagame.ForceUpdateDialog.newInstance(rcm.getForceMinVersionString())
                    .show(activity.getSupportFragmentManager(), com.genzopia.Instagame.ForceUpdateDialog.TAG);
            return;
        }

        // Smooth update — only shown in MainActivity, once every 3 app opens, once per session
        if (smoothUpdateShownThisSession) return;
        if (!(activity instanceof com.genzopia.Instagame.MainActivity)) return;
        if (!rcm.isSmoothUpdateAvailable()) return;
        if (activity.getSupportFragmentManager()
                .findFragmentByTag(com.genzopia.Instagame.SmoothUpdateDialog.TAG) != null) return;
        if (!shouldShowSmoothUpdateThisOpen()) return;

        smoothUpdateShownThisSession = true;
        com.genzopia.Instagame.SmoothUpdateDialog.newInstance(rcm.getSmoothMinVersionString())
                .show(activity.getSupportFragmentManager(), com.genzopia.Instagame.SmoothUpdateDialog.TAG);
    }

    /** Returns true once every 3 app opens. Counter stored in SharedPreferences. */
    private boolean shouldShowSmoothUpdateThisOpen() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("update_prefs", android.content.Context.MODE_PRIVATE);
        int count = prefs.getInt("app_open_count", 0) + 1;
        prefs.edit().putInt("app_open_count", count % 3).apply();
        return count % 3 == 0;
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void startAppPrefetch() {
        // Remote config — no UI blocked on this; when it's done, show update dialogs if needed
        remoteConfigManager = new RemoteConfigManager();
        remoteConfigManager.fetchConfig(success -> {
            Log.d("MyApplication", "Remote config done, success=" + success);
            configDone = true;
            notifyIfReady();
            // If an activity is already in the foreground, check for updates now
            if (currentActivity != null) {
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> showUpdateDialogsIfNeeded(currentActivity));
            }
            return kotlin.Unit.INSTANCE;
        });

        // Data + player pool prefetch
        DataPrefetchService.INSTANCE.startPrefetch(this, () -> {
            Log.d("MyApplication", "Data prefetch done");
            prefetchDone = true;
            notifyIfReady();
            return kotlin.Unit.INSTANCE;
        });
    }

    private void reIdentifyReturningUser() {
        com.google.firebase.auth.FirebaseUser firebaseUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.setUserId(uid);
        com.genzopia.Instagame.glide.GlideImageLoader.warmToken();

        new Thread(() -> {
            try {
                retrofit2.Response<com.genzopia.Instagame.gateway.UserProfileDTO> resp =
                        com.genzopia.Instagame.gateway.GatewayClient.INSTANCE.getCallApi()
                                .getMyProfile()
                                .execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    com.genzopia.Instagame.gateway.UserProfileDTO p = resp.body();
                    String photoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(p.getProfile_photo_url());
                    com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.identifyUser(
                            uid,
                            p.getFull_name() != null ? p.getFull_name() : "",
                            "",
                            photoUrl,
                            "returning"
                    );
                }
            } catch (Exception e) {
                Log.e("AmplitudeDebug", "reIdentify gateway fetch FAILED: " + e.getMessage());
            }
        }).start();
    }

    public static LottieComposition cachedComposition = null;

    public static LottieComposition getCachedComposition() {
        return cachedComposition;
    }

    private void prewarmLottie() {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int rawId = isNight
                ? R.raw.game_logo_dark_theme
                : R.raw.game_logo_white_theme;

        new Thread(() -> {
            try {
                java.io.InputStream stream = getResources().openRawResource(rawId);
                LottieComposition composition =
                        LottieCompositionFactory.fromJsonInputStreamSync(stream, null).getValue();
                cachedComposition = composition;
                Log.d("LottiePrewarm", "✅ Prewarm complete (sync thread)");
            } catch (Exception e) {
                Log.e("LottiePrewarm", "❌ Prewarm failed: " + e.getMessage());
            }
        }).start();
    }

    private void setupCoil() {
        String gatewayBase = BuildConfig.GATEWAY_BASE_URL.replaceAll("/$", "");
        String apiKey = BuildConfig.GATEWAY_API_KEY;

        OkHttpClient coilClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                String url = original.url().toString();

                if (url.contains("file-upload-worker.genzopia.workers.dev")) {
                    String key = original.url().queryParameter("key");
                    if (key != null && !key.isEmpty()) {
                        url = gatewayBase + "/media/file?key=" + key;
                        original = original.newBuilder().url(url).build();
                    }
                }

                if (!gatewayBase.isEmpty() && url.startsWith(gatewayBase) && url.contains("/media/file")) {
                    Log.d("profile_photo", "Coil → gateway: " + url);
                    okhttp3.Request.Builder reqBuilder = original.newBuilder()
                        .header("x-api-key", apiKey);
                    String token = com.genzopia.Instagame.glide.GlideImageLoader.getCachedToken();
                    if (token != null) reqBuilder.header("Authorization", "Bearer " + token);
                    return chain.proceed(reqBuilder.build());
                }

                return chain.proceed(original);
            })
            .build();

        Coil.setImageLoader(
            new ImageLoader.Builder(this)
                .callFactory(coilClient)
                .build()
        );
    }
}
