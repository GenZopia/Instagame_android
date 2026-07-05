package com.genzopia.Instagame.webgl_gameloading;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.genzopia.Instagame.common.BaseActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.genzopia.Instagame.databinding.ActivityGameModeBinding;
import com.genzopia.Instagame.gateway.GatewayClient;
import com.genzopia.Instagame.gateway.LaunchUrlResponse;
import com.google.firebase.auth.FirebaseAuth;

import java.io.ByteArrayInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class Game_mode extends BaseActivity {
    private static final String TAG = "Game_mode";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int MICROPHONE_PERMISSION_REQUEST = 101;

    // ================================================================
    // Ad / tracker / telemetry blacklist.
    // Used in both shouldInterceptRequest() and shouldOverrideUrlLoading().
    //
    // NOTE: Do NOT add "config.uca.cloud.unity3d.com" or other Unity
    // *remote-config* endpoints here — some Unity WebGL games fetch
    // required settings from them and blocking it can break the game.
    // Only telemetry/ads/analytics endpoints are listed below.
    // ================================================================
    private static final String[] AD_DOMAINS = {

            // ---- Google / DoubleClick ad stack ----
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "google-analytics.com", "pagead2.googlesyndication.com",
            "adservice.google.com", "adservice.google.co.in", "recaptcha.net", "hcaptcha.com",
            "googletagservices.com", "google.com/pagead",

            // ---- Generic programmatic / display ad exchanges ----
            "adnxs.com", "adsrvr.org", "moatads.com", "outbrain.com", "taboola.com",
            "popads.net", "popcash.net", "propellerads.com", "adcash.com",
            "hilltopads.net", "trafficjunky.com", "exoclick.com", "juicyads.com",
            "clickadu.com", "adsterra.com", "yllix.com", "revcontent.com",
            "mgid.com", "valueimpression.com", "ero-advertising.com",
            "criteo.com", "criteo.net", "smartadserver.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "casalemedia.com", "adform.net",
            "bidswitch.net", "contextweb.com", "sovrn.com", "sharethrough.com",

            // ---- Unity Ads / Unity telemetry (confirmed cdp.cloud.unity3d.com
            //      from live capture, plus documented Unity ad/mediation hosts) ----
            "unityads.unity3d.com", "configv2.unityads.unity3d.com",
            "cdp.cloud.unity3d.com", "events.mz.unity3d.com",
            "gw-is.iads.unity3d.com", "gw-rv.iads.unity3d.com",
            "gw.mediation.unity3d.com", "i-sdk.mediation.unity3d.com",
            "o-iab.mediation.unity3d.com", "o-sdk.mediation.unity3d.com",
            "o.iads.unity3d.com", "scar.unityads.unity3d.com",
            "auction.unityads.unity3d.com", "thind.unityads.unity3d.com",

            // ---- Major mobile ad networks / mediation platforms ----
            "applovin.com", "applvn.com", "safedk.com",              // AppLovin (+ SDK wrapper)
            "ironsrc.com", "ironsource.com", "supersonicads.com",     // ironSource / LevelPlay
            "vungle.com",                                             // Vungle
            "chartboost.com",                                         // Chartboost
            "adcolony.com", "adc-ads.com",                            // AdColony
            "inmobi.com", "inmobicdn.net",                            // InMobi
            "pangleglobal.com", "byteoversea.com", "pangle-ads.com",  // Pangle (ByteDance)
            "tapjoy.com", "tjtapjoy.net", "ultimateadds.com",          // Tapjoy
            "fyber.com", "digitalturbine.com", "inner-active.mobi",   // Fyber / DT Exchange
            "mopub.com",                                              // MoPub (legacy, still called by some SDKs)
            "startapp.com", "startappservice.com",                    // StartApp
            "yandex.ru/ads", "mobileads.yandex.net",                  // Yandex Mobile Ads
            "appodeal.com", "bidmachine.io",                          // Appodeal / BidMachine
            "smaato.net",                                             // Smaato

            // ---- Facebook / Meta Audience Network ----
            "facebook.com/audience", "an.facebook.com", "fbcdn-audience.net",

            // ---- Misc trackers / analytics sometimes bundled in HTML5 game SDKs ----
            "scorecardresearch.com", "quantserve.com", "yieldmo.com",
            "adtelligent.com", "gumgum.com", "media.net", "amazon-adsystem.com"
    };

    private ActivityGameModeBinding binding;
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    private String gameId;
    private String currentUserId;
    private String gameName = "";          // resolved from Firebase
    private long gameActivityStartMs;      // when onCreate fires
    private long gameUrlFetchStartMs;      // when network call starts
    private long gamePageLoadStartMs;      // when WebView starts loading
    private long gamePlayStartMs;          // when game page finishes loading (actual play start)
    private boolean exitedViaBack = false;

    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Hide status bar and navigation bar for true immersive fullscreen gaming
        hideSystemBars();

        executorService = Executors.newSingleThreadExecutor();

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        gameId = getIntent().getStringExtra("game_id");
        gameActivityStartMs = System.currentTimeMillis();

        Log.d(TAG, "Game ID: " + gameId);
        Log.d(TAG, "Current User ID: " + currentUserId);

        // Track launch initiated ? game name resolved later from Firebase
        String launchSource = getIntent().getStringExtra("launch_source");
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackGameLaunchInitiated(
                gameId != null ? gameId : "",
                "",  // name filled in after Firebase fetch
                launchSource != null ? launchSource : "reel_double_tap"
        );
        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged("game_" + gameId);

        fetchGameDataAndGetSignedUrl();
    }

    private void fetchGameDataAndGetSignedUrl() {
        if (gameId == null || gameId.isEmpty()) {
            Log.e(TAG, "Game ID is null or empty");
            Toast.makeText(this, "Game information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Gateway handles both direct game_link and signed URL ? no direct Firebase read needed
        gameUrlFetchStartMs = System.currentTimeMillis();
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                .trackGameUrlFetchStarted(gameId, gameName);
        getSignedGameUrl();
    }

    private void setScreenOrientation(String orientation) {
        if (orientation == null || orientation.isEmpty()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            return;
        }
        switch (orientation.toLowerCase().trim()) {
            case "portrait":
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case "landscape":
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    private void getSignedGameUrl() {
        GatewayClient.INSTANCE.getCallApi().getGameLaunchUrl(gameId)
                .enqueue(new Callback<LaunchUrlResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<LaunchUrlResponse> call,
                                           @NonNull Response<LaunchUrlResponse> resp) {
                        if (resp.isSuccessful() && resp.body() != null) {
                            String signedGameUrl = resp.body().resolvedUrl();
                            if (signedGameUrl == null || signedGameUrl.isEmpty()) {
                                Log.e(TAG, "Gateway returned empty launch URL");
                                runOnUiThread(() -> {
                                    if (!isFinishing() && !isDestroyed()) {
                                        Toast.makeText(Game_mode.this, "Game URL not available", Toast.LENGTH_SHORT).show();
                                        finish();
                                    }
                                });
                                return;
                            }
                            // Apply orientation and game name from gateway response
                            gameName = resp.body().getGameName() != null ? resp.body().getGameName() : "";
                            setScreenOrientation(resp.body().getOrientation());
                            long fetchDuration = System.currentTimeMillis() - gameUrlFetchStartMs;
                            com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                    .trackGameUrlFetchSuccess(gameId, gameName, fetchDuration, "signed");
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) setupWebView(signedGameUrl);
                            });
                        } else {
                            Log.e(TAG, "Gateway launch-url error: " + resp.code());
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Toast.makeText(Game_mode.this, "Failed to get game URL",
                                            Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<LaunchUrlResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "getSignedGameUrl failed: " + t.getMessage());
                        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                .trackGameUrlFetchFailed(gameId, gameName,
                                        t.getMessage() != null ? t.getMessage() : "network_error");
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Game_mode.this, "Network error: " + t.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                });
    }

    // ---- Helper: check if a URL/host belongs to an ad/tracker domain ----
    private boolean isAdDomain(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : AD_DOMAINS) {
            if (lower.contains(domain)) return true;
        }
        return false;
    }

    private void setupWebView(String gameUrl) {
        if (isFinishing() || isDestroyed() || binding == null) {
            Log.w(TAG, "Activity finishing, skipping WebView setup");
            return;
        }

        try {
            webView = binding.webView;

            WebSettings webSettings = webView.getSettings();

            // ---- Core settings ----
            webSettings.setJavaScriptEnabled(true);
            webSettings.setDomStorageEnabled(true);
            webSettings.setDatabaseEnabled(true);
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            webSettings.setBuiltInZoomControls(false);
            webSettings.setDisplayZoomControls(false);
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            webSettings.setAllowFileAccess(true);
            webSettings.setAllowContentAccess(true);
            webSettings.setUserAgentString(webSettings.getUserAgentString() + " Desktop");

            // ---- Block popups & new windows ----
            webSettings.setJavaScriptCanOpenWindowsAutomatically(false); // was true ? changed!
            webSettings.setSupportMultipleWindows(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            }

            // ---- WebViewClient ----
            webView.setWebViewClient(new WebViewClient() {

                // Block ad/tracker network requests before they load
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    String url = request.getUrl().toString();
                    if (isAdDomain(url)) {
                        Log.d(TAG, "[AdBlock] Blocked request: " + url);
                        return new WebResourceResponse(
                                "text/plain", "utf-8",
                                new ByteArrayInputStream("".getBytes())
                        );
                    }
                    return super.shouldInterceptRequest(view, request);
                }

                // Block ad redirects ? blacklist only, allow all game URLs freely
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String host = request.getUrl().getHost();
                    String url = request.getUrl().toString();

                    // Always allow blob/data (game assets)
                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                        return false;
                    }

                    // Block known ad/tracker domains
                    if (isAdDomain(host)) {
                        Log.d(TAG, "[AdBlock] Blocked navigation: " + host);
                        return true;
                    }

                    // Allow everything else (random game subdomains, CDNs, etc.)
                    Log.d(TAG, "[AdBlock] Allowed navigation: " + host);
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page loaded: " + url);
                    if (gamePageLoadStartMs > 0) {
                        long loadDuration = System.currentTimeMillis() - gamePageLoadStartMs;
                        String orient = getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                ? "portrait" : "landscape";
                        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                                .trackGameLoaded(gameId, gameName, orient, loadDuration);
                        com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onGamePlayed();
                        // Start actual play timer ? excludes loading time
                        gamePlayStartMs = System.currentTimeMillis();
                        gamePageLoadStartMs = 0; // fire only once
                    }
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description);
                }
            });

            // ---- WebChromeClient ----
            webView.setWebChromeClient(new WebChromeClient() {

                // Only grant camera/mic ? deny notification/location used by ads
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    Log.d(TAG, "Permission requested: " + java.util.Arrays.toString(request.getResources()));
                    pendingPermissionRequest = request;

                    for (String resource : request.getResources()) {
                        if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                            if (ContextCompat.checkSelfPermission(Game_mode.this, Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(Game_mode.this,
                                        new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
                                return;
                            }
                        } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            if (ContextCompat.checkSelfPermission(Game_mode.this, Manifest.permission.RECORD_AUDIO)
                                    != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(Game_mode.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION_REQUEST);
                                return;
                            }
                        }
                    }

                    // Only grant safe permissions
                    java.util.List<String> safe = new java.util.ArrayList<>();
                    for (String resource : request.getResources()) {
                        if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                                || resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            safe.add(resource);
                        } else {
                            Log.d(TAG, "[AdBlock] Denied permission: " + resource);
                        }
                    }

                    if (!safe.isEmpty()) {
                        request.grant(safe.toArray(new String[0]));
                    } else {
                        request.deny();
                    }
                }

                // Block JS alert/confirm popups from ads
                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    Log.d(TAG, "[AdBlock] Blocked JS alert");
                    result.cancel();
                    return true;
                }

                @Override
                public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                    result.cancel();
                    return true;
                }

                @Override
                public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                    result.cancel();
                    return true;
                }

                // Block popup windows
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                    Log.d(TAG, "[AdBlock] Blocked popup window");
                    return false;
                }

                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    Log.d(TAG, "Console: " + consoleMessage.message()
                            + " -- Line " + consoleMessage.lineNumber()
                            + " of " + consoleMessage.sourceId());
                    return true;
                }

                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    Log.d(TAG, "Loading: " + newProgress + "%");
                }
            });

            Log.d(TAG, "Loading game URL: " + gameUrl);
            gamePageLoadStartMs = System.currentTimeMillis();
            webView.loadUrl(gameUrl);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up WebView: " + e.getMessage());
            Toast.makeText(this, "Error loading game", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST || requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted");
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null;
                }
            } else {
                Log.d(TAG, "Permission denied");
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
                Toast.makeText(this, "Permission denied. Some features may not work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // time_played_ms = time from game page load to exit (excludes loading time).
        // Falls back to time from Activity start if page never finished loading.
        long timePlayedMs = gamePlayStartMs > 0
                ? System.currentTimeMillis() - gamePlayStartMs
                : System.currentTimeMillis() - gameActivityStartMs;
        String exitMethod = exitedViaBack ? "back_button" : "system";
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE
                .trackGameEnded(gameId != null ? gameId : "", gameName, timePlayedMs, exitMethod);
        // Flush immediately so the event isn't lost when the process dies
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.flushEvents();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearCache(true);
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        binding = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            showExitConfirmDialog();
        }
    }

    private void showExitConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Game?")
                .setMessage("Do you really want to exit the game?")
                .setPositiveButton("Exit", (dialog, which) -> {
                    exitedViaBack = true;
                    finish();
                })
                .setNegativeButton("Keep Playing", (dialog, which) -> {
                    dialog.dismiss();
                    // Re-apply immersive mode since the dialog temporarily shows system bars
                    hideSystemBars();
                })
                .setCancelable(true)
                .setOnCancelListener(dialog -> hideSystemBars())
                .show();
    }

    public String getGameId() {
        return gameId;
    }

    // ---- Immersive fullscreen helpers ----

    /**
     * Hides both the status bar and the navigation bar so the game occupies
     * the entire screen. On API 30+ uses WindowInsetsController; on older
     * devices falls back to the legacy SYSTEM_UI_FLAG approach.
     */
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            //noinspection deprecation
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        // Ensure window attributes are set for true fullscreen
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        // For devices with display cutouts (notches), extend into cutout area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.view.WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }
    }

    /**
     * Re-apply immersive mode whenever the window regains focus (e.g. after a
     * dialog or permission prompt dismisses and the bars briefly reappear).
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }
}