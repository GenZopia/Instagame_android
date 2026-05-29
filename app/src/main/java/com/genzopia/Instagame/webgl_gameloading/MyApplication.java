package com.genzopia.Instagame.webgl_gameloading;

import android.app.Application;
import android.content.Context;
import android.util.Log;

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
        prewarmLottie();  // ✅
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
