package com.genzopia.Instagame.webgl_gameloading;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import coil.Coil;
import coil.ImageLoader;

import com.genzopia.Instagame.BuildConfig;



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
