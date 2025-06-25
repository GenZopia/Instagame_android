package com.genzopia.Instagame.webgl_gameloading;

import android.app.Application;
import android.content.Context;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

public class MyApplication extends Application {
    private static GeckoRuntime geckoRuntime;

    public static synchronized GeckoRuntime getGeckoRuntime(Context context) {
        if (geckoRuntime == null) {
            // Configure runtime settings to disable all zoom functionality
            GeckoRuntimeSettings settings = new GeckoRuntimeSettings.Builder()
                    .forceUserScalableEnabled(false)  // Disable pinch-to-zoom
                    .doubleTapZoomingEnabled(false)   // Disable double-tap zoom
                    .build();

            geckoRuntime = GeckoRuntime.create(context, settings);
        }
        return geckoRuntime;
    }
}