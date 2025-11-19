package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.SystemClock;
import android.os.Handler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.genzopia.Instagame.MainActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {
    private boolean hasNavigated = false;
    private long splashStartMs = 0L;
    private static final long MIN_SPLASH_MS = 2000L; // 2s minimum display

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        splashStartMs = SystemClock.uptimeMillis();

        // Optional: Make splash fullscreen (hide status & nav bar)
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        WebView webView = findViewById(R.id.web_splash);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        // Switch animation based on theme (night/day) using raw JSON resources
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
                "  var fallback = setTimeout(done, 2500);" +
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
                    if (hasNavigated) return;
                    long elapsed = SystemClock.uptimeMillis() - splashStartMs;
                    long delay = Math.max(0, MIN_SPLASH_MS - elapsed);
                    new Handler(getMainLooper()).postDelayed(() -> {
                        if (hasNavigated) return;
                        hasNavigated = true;
                        boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
                        Intent intent = new Intent(SplashActivity.this,
                                isLoggedIn ? MainActivity.class : LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }, delay);
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
}
