package com.genzopia.Instagame.webgl_gameloading;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
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
    
    private ActivityGameModeBinding binding;
    private WebView webView;
    private PermissionRequest pendingPermissionRequest;
    
    // Variables to store game ID
    private String gameId;
    private String currentUserId;
    
    // HTTP client for making requests
    private OkHttpClient httpClient;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        EdgeToEdge.enable(this);

        // Inflate and set layout
        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize HTTP client and executor
        httpClient = new OkHttpClient();
        executorService = Executors.newSingleThreadExecutor();

        // Get current user ID from Firebase Auth
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Retrieve intent extras
        gameId = getIntent().getStringExtra("game_id");
        
        // Log the received values for debugging
        Log.d(TAG, "Game ID: " + gameId);
        Log.d(TAG, "Current User ID: " + currentUserId);

        // Fetch game data to get the user_id, orientation, and game_link
        fetchGameDataAndGetSignedUrl();
    }

    private void fetchGameDataAndGetSignedUrl() {
        if (gameId == null || gameId.isEmpty()) {
            Log.e(TAG, "Game ID is null or empty");
            Toast.makeText(this, "Game information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Fetch game data from Firebase to get the user_id and orientation
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String gameUserId = snapshot.child("user_id").getValue(String.class);
                    String orientation = snapshot.child("orientation").getValue(String.class);
                    String gameLink = snapshot.child("game_link").getValue(String.class);
                    
                    Log.d(TAG, "Game user_id from Firebase: " + gameUserId);
                    Log.d(TAG, "Game orientation from Firebase: " + orientation);
                    Log.d(TAG, "Game link from Firebase: " + gameLink);
                    
                    // Set screen orientation based on Firebase data
                    setScreenOrientation(orientation);
                    
                    // If game_link is available, load it directly — skip the worker
                    if (gameLink != null && !gameLink.isEmpty()) {
                        Log.d(TAG, "Using direct game_link: " + gameLink);
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                setupWebView(gameLink);
                            }
                        });
                    } else if (gameUserId != null && !gameUserId.isEmpty()) {
                        // No game_link — fall back to signed URL from worker
                        getSignedGameUrl(gameUserId);
                    } else {
                        Log.e(TAG, "Game user_id is null or empty");
                        Toast.makeText(Game_mode.this, "Game owner information not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Log.e(TAG, "Game not found in Firebase: " + gameId);
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

    /**
     * Set screen orientation based on Firebase orientation parameter
     * @param orientation The orientation value from Firebase ("portrait" or "landscape")
     */
    private void setScreenOrientation(String orientation) {
        if (orientation == null || orientation.isEmpty()) {
            // Default to landscape if no orientation specified
            Log.d(TAG, "No orientation specified, defaulting to landscape");
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            return;
        }
        
        // Convert to lowercase for case-insensitive comparison
        String orientationLower = orientation.toLowerCase().trim();
        
        switch (orientationLower) {
            case "portrait":
                Log.d(TAG, "Setting orientation to PORTRAIT");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case "landscape":
                Log.d(TAG, "Setting orientation to LANDSCAPE");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
            default:
                // If orientation value is not recognized, default to landscape
                Log.w(TAG, "Unknown orientation value: " + orientation + ", defaulting to landscape");
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    private void getSignedGameUrl(String gameUserId) {
        // Build the link-signer URL with game's user_id and gameid parameters
        String linkSignerUrl = "https://link-signer.genzopia.workers.dev/?userid=" + gameUserId + "&gameid=" + gameId;
        
        // Detailed logging to verify the request
        Log.d(TAG, "=== LINK-SIGNER REQUEST DETAILS ===");
        Log.d(TAG, "Base URL: https://link-signer.genzopia.workers.dev/");
        Log.d(TAG, "Game User ID (from game data): " + gameUserId);
        Log.d(TAG, "Game ID: " + gameId);
        Log.d(TAG, "Full URL: " + linkSignerUrl);
        Log.d(TAG, "URL Parameters:");
        Log.d(TAG, "  - userid: " + gameUserId);
        Log.d(TAG, "  - gameid: " + gameId);
        Log.d(TAG, "=====================================");

        Request request = new Request.Builder()
                .url(linkSignerUrl)
                .build();

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
                        // Parse JSON response
                        Gson gson = new Gson();
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        
                        boolean success = jsonResponse.get("success").getAsBoolean();
                        if (success) {
                            String signedGameUrl = jsonResponse.get("url").getAsString();
                            Log.d(TAG, "Signed game URL: " + signedGameUrl);
                            
                            // Load the signed game URL in WebView
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    setupWebView(signedGameUrl);
                                } else {
                                    Log.w(TAG, "Activity is finishing, skipping WebView setup");
                                }
                            });
                        } else {
                            Log.e(TAG, "Link-signer returned success=false");
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Toast.makeText(Game_mode.this, "Failed to get game URL", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
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

    private void setupWebView(String gameUrl) {
        // Check if activity is still valid and binding is not null
        if (isFinishing() || isDestroyed() || binding == null) {
            Log.w(TAG, "Activity is finishing or binding is null, skipping WebView setup");
            return;
        }

        try {
            webView = binding.webView;
            
            // Enable JavaScript (required for WebGL games)
            WebSettings webSettings = webView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            
            // Enable DOM storage (required for many games)
            webSettings.setDomStorageEnabled(true);
            
            // Enable database storage
            webSettings.setDatabaseEnabled(true);
            
            // Enable hardware acceleration and GPU
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
            }
            
            // Enable WebGL support
            webSettings.setJavaScriptCanOpenWindowsAutomatically(true);
            
            // Enable mixed content (HTTP and HTTPS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            }
            
            // Enable zoom controls
            webSettings.setBuiltInZoomControls(false);
            webSettings.setDisplayZoomControls(false);
            
            // Enable wide viewport
            webSettings.setUseWideViewPort(true);
            webSettings.setLoadWithOverviewMode(true);
            
            // Enable caching for better performance
            webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
            
            // Enable media playback
            webSettings.setMediaPlaybackRequiresUserGesture(false);
            
            // Set user agent to desktop for better game compatibility
            webSettings.setUserAgentString(webSettings.getUserAgentString() + " Desktop");
            
            // Enable file access
            webSettings.setAllowFileAccess(true);
            webSettings.setAllowContentAccess(true);
            
            // Set WebViewClient to handle page loading
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.d(TAG, "Page loaded successfully: " + url);
                }
                
                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    super.onReceivedError(view, errorCode, description, failingUrl);
                    Log.e(TAG, "WebView error: " + description);
                }
            });
            
            // Set WebChromeClient to handle permissions and console messages
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onPermissionRequest(PermissionRequest request) {
                    Log.d(TAG, "Permission requested: " + java.util.Arrays.toString(request.getResources()));
                    
                    // Store the request for later
                    pendingPermissionRequest = request;
                    
                    // Check if camera permission is requested
                    for (String resource : request.getResources()) {
                        if (resource.equals(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                            // Check if we have camera permission
                            if (ContextCompat.checkSelfPermission(Game_mode.this, Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED) {
                                // Request camera permission from user
                                ActivityCompat.requestPermissions(Game_mode.this,
                                        new String[]{Manifest.permission.CAMERA},
                                        CAMERA_PERMISSION_REQUEST);
                                return;
                            }
                        } else if (resource.equals(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                            // Check if we have microphone permission
                            if (ContextCompat.checkSelfPermission(Game_mode.this, Manifest.permission.RECORD_AUDIO)
                                    != PackageManager.PERMISSION_GRANTED) {
                                // Request microphone permission from user
                                ActivityCompat.requestPermissions(Game_mode.this,
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        MICROPHONE_PERMISSION_REQUEST);
                                return;
                            }
                        }
                    }
                    
                    // Grant all requested permissions
                    request.grant(request.getResources());
                }
                
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    Log.d(TAG, "Console: " + consoleMessage.message() + 
                            " -- From line " + consoleMessage.lineNumber() + 
                            " of " + consoleMessage.sourceId());
                    return true;
                }
                
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    super.onProgressChanged(view, newProgress);
                    Log.d(TAG, "Loading progress: " + newProgress + "%");
                }
            });
            
            // Load the signed game URL
            Log.d(TAG, "Loading signed game URL in WebView: " + gameUrl);
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
                // Grant the pending permission request
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                    pendingPermissionRequest = null;
                }
            } else {
                Log.d(TAG, "Permission denied");
                // Deny the pending permission request
                if (pendingPermissionRequest != null) {
                    pendingPermissionRequest.deny();
                    pendingPermissionRequest = null;
                }
                Toast.makeText(this, "Permission denied. Some game features may not work.", Toast.LENGTH_LONG).show();
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
    
    // Getter methods for accessing the stored values
    public String getGameId() {
        return gameId;
    }
}
