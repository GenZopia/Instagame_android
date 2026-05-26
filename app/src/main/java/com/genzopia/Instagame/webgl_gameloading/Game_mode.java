package com.genzopia.Instagame.webgl_gameloading;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.common.BaseActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.genzopia.Instagame.databinding.ActivityGameModeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Game_mode extends BaseActivity {
    private static final String TAG = "Game_mode";
    private static final int CAMERA_PERMISSION_REQUEST = 100;
    private static final int MICROPHONE_PERMISSION_REQUEST = 101;

    // ── Ad blacklist — used in both shouldInterceptRequest & shouldOverrideUrlLoading ──
    private static final String[] AD_DOMAINS = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "googletagmanager.com", "google-analytics.com", "pagead2.googlesyndication.com",
            "adservice.google.com", "recaptcha.net", "hcaptcha.com",
            "adnxs.com", "adsrvr.org", "moatads.com", "outbrain.com", "taboola.com",
            "popads.net", "popcash.net", "propellerads.com", "adcash.com",
            "hilltopads.net", "trafficjunky.com", "exoclick.com", "juicyads.com",
            "clickadu.com", "adsterra.com", "yllix.com", "revcontent.com",
            "mgid.com", "valueimpression.com", "ero-advertising.com"
    };

    private ActivityGameModeBinding binding;
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;

    private String gameId;
    private String currentUserId;

    private OkHttpClient httpClient;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        gameId = getIntent().getStringExtra("game_id");

        Log.d(TAG, "Game ID: " + gameId);
        Log.d(TAG, "Current User ID: " + currentUserId);

        fetchGameDataAndGetSignedUrl();
    }

    private void fetchGameDataAndGetSignedUrl() {
        if (gameId == null || gameId.isEmpty()) {
            Log.e(TAG, "Game ID is null or empty");
            Toast.makeText(this, "Game information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String gameUserId = snapshot.child("user_id").getValue(String.class);
                    String orientation = snapshot.child("orientation").getValue(String.class);
                    String gameLink = snapshot.child("game_link").getValue(String.class);

                    Log.d(TAG, "Game user_id: " + gameUserId);
                    Log.d(TAG, "Orientation: " + orientation);
                    Log.d(TAG, "Game link: " + gameLink);

                    setScreenOrientation(orientation);

                    if (gameLink != null && !gameLink.isEmpty()) {
                        Log.d(TAG, "Using direct game_link: " + gameLink);
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                setupWebView(gameLink);
                            }
                        });
                    } else if (gameUserId != null && !gameUserId.isEmpty()) {
                        getSignedGameUrl(gameUserId);
                    } else {
                        Log.e(TAG, "Game user_id is null or empty");
                        Toast.makeText(Game_mode.this, "Game owner information not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Log.e(TAG, "Game not found: " + gameId);
                    Toast.makeText(Game_mode.this, "Game not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Error fetching game data: " + error.getMessage());
                Toast.makeText(Game_mode.this, "Error loading game data", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
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

    private void getSignedGameUrl(String gameUserId) {
        String linkSignerUrl = "https://link-signer.genzopia.workers.dev/?userid=" + gameUserId + "&gameid=" + gameId;
        Log.d(TAG, "Link-signer URL: " + linkSignerUrl);

        Request request = new Request.Builder().url(linkSignerUrl).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        Toast.makeText(Game_mode.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Link-signer response: " + responseBody);
                    try {
                        Gson gson = new Gson();
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        boolean success = jsonResponse.get("success").getAsBoolean();
                        if (success) {
                            String signedGameUrl = jsonResponse.get("url").getAsString();
                            Log.d(TAG, "Signed game URL: " + signedGameUrl);
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    setupWebView(signedGameUrl);
                                }
                            });
                        } else {
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Toast.makeText(Game_mode.this, "Failed to get game URL", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "JSON parse error: " + e.getMessage());
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Game_mode.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                } else {
                    Log.e(TAG, "HTTP error: " + response.code());
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            Toast.makeText(Game_mode.this, "HTTP error: " + response.code(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    // ── Helper: check if a URL belongs to an ad domain ──────────────────────
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

            // ── Core settings ────────────────────────────────────────────────
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

            // ── Block popups & new windows ───────────────────────────────────
            webSettings.setJavaScriptCanOpenWindowsAutomatically(false); // was true — changed!
            webSettings.setSupportMultipleWindows(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            }

            // ── WebViewClient ────────────────────────────────────────────────
            webView.setWebViewClient(new WebViewClient() {

                // Block ad network requests before they load
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

                // Block ad redirects — blacklist only, allow all game URLs freely
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    String host = request.getUrl().getHost();
                    String url = request.getUrl().toString();

                    // Always allow blob/data (game assets)
                    if (url.startsWith("blob:") || url.startsWith("data:")) {
                        return false;
                    }

                    // Block known ad domains
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
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description);
                }
            });

            // ── WebChromeClient ──────────────────────────────────────────────
            webView.setWebChromeClient(new WebChromeClient() {

                // Only grant camera/mic — deny notification/location used by ads
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
            super.onBackPressed();
        }
    }

    public String getGameId() {
        return gameId;
    }
}