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
 * (process start) so SplashActivity only needs to show the logo and route —
 * not wait on network work.
 */
public class MyApplication extends Application {

    // ── App-level prefetch state ──────────────────────────────────────────
    private static volatile boolean prefetchDone = false;
    private static volatile boolean configDone = false;
    private static volatile RemoteConfigManager remoteConfigManager;
    private static final List<Runnable> readyCallbacks = new ArrayList<>();
    private static final Object lock = new Object();

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
     * ForceUpdateDialog on that activity — regardless of which activity was launched
     * (splash, deep link, notification, etc.).
     */
    private void registerForceUpdateEnforcer() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(android.app.Activity activity) {
                if (!(activity instanceof androidx.fragment.app.FragmentActivity)) return;
                if (!configDone) return;
                RemoteConfigManager rcm = remoteConfigManager;
                if (rcm == null || !rcm.isForceUpdateRequired()) return;

                androidx.fragment.app.FragmentActivity fa = (androidx.fragment.app.FragmentActivity) activity;
                // Don't stack duplicate dialogs
                if (fa.getSupportFragmentManager().findFragmentByTag(ForceUpdateDialog.TAG) != null) return;

                String minVersion = rcm.getForceMinVersionString();
                ForceUpdateDialog.newInstance(minVersion)
                        .show(fa.getSupportFragmentManager(), ForceUpdateDialog.TAG);
            }

            @Override public void onActivityCreated(android.app.Activity a, Bundle b) {}
            @Override public void onActivityStarted(android.app.Activity a) {}
            @Override public void onActivityPaused(android.app.Activity a) {}
            @Override public void onActivityStopped(android.app.Activity a) {}
            @Override public void onActivitySaveInstanceState(android.app.Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(android.app.Activity a) {}
        });
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void startAppPrefetch() {
        // Remote config
        remoteConfigManager = new RemoteConfigManager();
        remoteConfigManager.fetchConfig(success -> {
            Log.d("MyApplication", "Remote config done, success=" + success);
            configDone = true;
            notifyIfReady();
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
