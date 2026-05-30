package com.genzopia.Instagame.webgl_gameloading;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import coil.Coil;
import coil.ImageLoader;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.genzopia.Instagame.BuildConfig;
import com.genzopia.Instagame.R;

import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Application class.
 *
 * Sets up a custom Coil ImageLoader with an OkHttp interceptor that injects
 * the x-api-key header for any request to file-upload-worker.genzopia.workers.dev.
 * This is required because profile photos are served from that worker which
 * requires authentication.
 */
public class MyApplication extends Application {


    @Override
    public void onCreate() {
        super.onCreate();
        setupCoil();
        prewarmLottie();
        // Init Amplitude — must be first so all subsequent events are captured
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.init(this);
        // Start session tracking
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppCreated();
        // Re-identify returning user so they are never shown as Anonymous
        reIdentifyReturningUser();
        // Register process-level lifecycle observer — fires only on true
        // app foreground/background, NOT on Activity-to-Activity transitions.
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(LifecycleOwner owner) {
                // App came to foreground (from home screen / recents / lock screen)
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppForegrounded();
            }

            @Override
            public void onStop(LifecycleOwner owner) {
                // App went to background — track it and flush immediately
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onAppBackgrounded();
                // Flush queued events so Amplitude receives them before the process dies
                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.flushEvents();
            }
        });
    }

    /**
     * If a user is already logged in (returning session), re-attach their
     * identity to Amplitude immediately so no events fire as Anonymous.
     *
     * Step 1: set userId synchronously from FirebaseAuth (no network call) —
     *         this runs before SplashActivity fires app_opened, so that event
     *         is already attributed to the real user.
     * Step 2: fetch name/email/photo from Realtime DB async and call identifyUser()
     *         to populate the user profile properties including $avatar.
     */
    private void reIdentifyReturningUser() {
        com.google.firebase.auth.FirebaseUser firebaseUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        String uid = firebaseUser.getUid();

        // ── Step 1: set userId immediately (synchronous, no network) ──────────
        // This ensures app_opened and all early events are attributed to the
        // real user, not Anonymous.
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.setUserId(uid);

        // ── Step 2: fetch full profile and set user properties async ──────────
        com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("users").child(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || !snapshot.exists()) {
                        Log.w("AmplitudeDebug", "reIdentify: snapshot null or missing for uid=" + uid);
                        return;
                    }
                    String name = snapshot.child("full_name").getValue(String.class);
                    String email = snapshot.child("email").getValue(String.class);
                    String rawPhotoUrl = snapshot.child("profile_photo_url").getValue(String.class);
                    String photoUrl = com.genzopia.Instagame.utils.ProfilePhotoUtils.sanitize(rawPhotoUrl);
                    Log.d("AmplitudeDebug", "reIdentify → name='" + name + "' rawPhoto='" + rawPhotoUrl + "' sanitized='" + photoUrl + "'");
                    com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.identifyUser(
                            uid,
                            name != null ? name : "",
                            email != null ? email : "",
                            photoUrl,
                            "returning"
                    );
                })
                .addOnFailureListener(e -> Log.e("AmplitudeDebug", "reIdentify DB fetch FAILED: " + e.getMessage()));
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

        // ✅ Parse synchronously on a dedicated thread BEFORE SplashActivity opens
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
        OkHttpClient coilClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                String url = original.url().toString();
                okhttp3.Request request;
                if (url.contains("file-upload-worker.genzopia.workers.dev")) {
                    Log.d("profile_photo", "Coil → " + url);
                    request = original.newBuilder()
                        .header("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
                        .build();
                } else {
                    request = original;
                }
                Response response = chain.proceed(request);
                Log.d("profile_photo", "Coil ← " + response.code() + " " + url);
                return response;
            })
            .build();

        Coil.setImageLoader(
            new ImageLoader.Builder(this)
                .callFactory(coilClient)
                .build()
        );
    }


}
