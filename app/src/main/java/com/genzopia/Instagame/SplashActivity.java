package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import androidx.media3.common.util.UnstableApi;
import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.genzopia.Instagame.MainActivity;
import com.genzopia.Instagame.common.BaseActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.genzopia.Instagame.utils.DataPrefetchService;

import kotlin.Unit;

public class SplashActivity extends BaseActivity {
    private static final String TAG = "SplashActivity";
    private static final long MAX_WAIT_MS = 20000; // 20s hard timeout — gives slow connections time to load reel metadata
    private boolean hasNavigated = false;
    private boolean animationComplete = false;
    private boolean dataLoaded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        Log.d(TAG, "SplashActivity started - beginning data prefetch");
        
        // Start prefetching data immediately in background with callback
        startDataPrefetch();

        // Hard timeout: if data never loads (bad network / Firebase cold start),
        // navigate anyway after MAX_WAIT_MS so the user is never stuck forever
        handler.postDelayed(() -> {
            if (!hasNavigated) {
                Log.w(TAG, "Timeout reached — navigating without full prefetch");
                dataLoaded = true;
                checkAndNavigate();
            }
        }, MAX_WAIT_MS);

        // Make splash fullscreen for immersive experience
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        WebView webView = findViewById(R.id.web_splash);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // Switch animation based on theme
        int nightModeFlags = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;

        final boolean isNight = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        final int rawId = isNight ? R.raw.game_logo_dark_theme : R.raw.game_logo_white_theme;
        
        String html = "<!DOCTYPE html>\n" +
                "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                "<style>html,body{height:100%;margin:0;padding:0;background:transparent;overflow:hidden;}#anim{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;}#anim>div{width:80vw;height:80vh;max-width:500px;max-height:500px;}</style>" +
                "</head><body>" +
                "<div id=\"anim\"></div>" +
                "<script src=\"https://cdnjs.cloudflare.com/ajax/libs/lottie-web/5.12.2/lottie.min.js\"></script>" +
                "<script>" +
                "(function(){" +
                "  function done(){window.AndroidSplash && AndroidSplash.onDone();}" +
                "  var fallback = setTimeout(done, 3000);" +
                "  try {" +
                "    var jsonStr = (window.AndroidSplash && AndroidSplash.getJson()) || '{}';" +
                "    var data = JSON.parse(jsonStr);" +
                "    var anim = lottie.loadAnimation({container: document.getElementById('anim'),renderer:'svg',loop:false,autoplay:true,animationData:data});" +
                "    anim.addEventListener('complete', function(){ clearTimeout(fallback); done(); });" +
                "  } catch(e) { /* fallback timer will fire */ }" +
                "})();" +
                "</script>" +
                "</body></html>";

        final String jsonString = readRawJson(rawId);

        webView.addJavascriptInterface(new Object(){
            @android.webkit.JavascriptInterface
            public void onDone(){
                runOnUiThread(() -> {
                    Log.d(TAG, "Animation complete");
                    animationComplete = true;
                    checkAndNavigate();
                });
            }
            @android.webkit.JavascriptInterface
            public String getJson(){
                return jsonString == null ? "{}" : jsonString;
            }
        }, "AndroidSplash");

        webView.setWebViewClient(new WebViewClient());
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    /**
     * Start prefetching data in background with callback
     */
    @OptIn(markerClass = UnstableApi.class)
    private void startDataPrefetch() {
        // Non-blocking: prefetch fires in background, callback returns immediately
        // so navigation is driven purely by the splash animation completing
        DataPrefetchService.INSTANCE.startPrefetch(this, () -> {
            Log.d(TAG, "Prefetch callback received (immediate)");
            dataLoaded = true;
            checkAndNavigate();
            return Unit.INSTANCE;
        });
    }

    /**
     * Check if both animation and data are ready, then navigate
     */
    private void checkAndNavigate() {
        if (hasNavigated) return;
        
        Log.d(TAG, "Check navigate - Animation: " + animationComplete + ", Data: " + dataLoaded);
        
        // Only navigate when BOTH animation is complete AND data is loaded
        if (animationComplete && dataLoaded) {
            hasNavigated = true;
            Log.d(TAG, "Both conditions met - navigating to next screen");
            navigateToNextScreen();
        } else {
            Log.d(TAG, "Waiting... Animation: " + animationComplete + ", Data: " + dataLoaded);
        }
    }

    private void navigateToNextScreen() {
        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        Intent intent = new Intent(SplashActivity.this,
                isLoggedIn ? MainActivity.class : LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Forward deep link video ID if launched from a share link
        android.net.Uri data = getIntent().getData();
        if (data != null) {
            String videoId = null;
            String scheme = data.getScheme();
            String host = data.getHost();

            if ("https".equals(scheme) && "instagame.genzopia.com".equals(host)) {
                // HTTPS App Link: https://instagame.genzopia.com/video/{videoId}
                videoId = data.getLastPathSegment();
            } else if ("instagame".equals(scheme) && "video".equals(host)) {
                // Custom URI scheme: instagame://video/{videoId}
                videoId = data.getLastPathSegment();
            }

            if (videoId != null && !videoId.isEmpty()) {
                intent.putExtra("deep_link_video_id", videoId);
            }
        }

        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private String readRawJson(int resId) {
        try (InputStream is = getResources().openRawResource(resId);
             BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
