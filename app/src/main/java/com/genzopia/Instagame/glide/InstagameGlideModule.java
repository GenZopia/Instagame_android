package com.genzopia.Instagame.glide;

import android.content.Context;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;
import com.genzopia.Instagame.BuildConfig;

import java.io.InputStream;

import okhttp3.OkHttpClient;

@GlideModule
public final class InstagameGlideModule extends AppGlideModule {

    @Override
    public void registerComponents(Context context, Glide glide, Registry registry) {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                okhttp3.Request original = chain.request();
                String url = original.url().toString();
                okhttp3.Request request;
                if (url.contains("file-upload-worker.genzopia.workers.dev")) {
                    Log.d("profile_photo", "Glide[module] → " + url);
                    request = original.newBuilder()
                        .header("x-api-key", BuildConfig.FILE_UPLOAD_API_KEY)
                        .build();
                } else {
                    request = original;
                }
                okhttp3.Response response = chain.proceed(request);
                Log.d("profile_photo", "Glide[module] ← " + response.code() + " " + url);
                return response;
            })
            .build();

        registry.replace(GlideUrl.class, InputStream.class,
            new OkHttpUrlLoader.Factory(client));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
