package com.genzopia.Instagame.webgl_gameloading;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.databinding.ActivityGameModeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;

public class Game_mode extends AppCompatActivity {
    private ActivityGameModeBinding binding;
    private GeckoSession geckoSession;
    
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

        // Retrieve intent extras - only game_id now
        gameId = getIntent().getStringExtra("game_id");
        
        // Log the received values for debugging
        Log.d("Game_mode", "Game ID: " + gameId);
        Log.d("Game_mode", "Current User ID: " + currentUserId);

        // Lock to landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Fetch game data to get the user_id, then get signed game URL
        fetchGameDataAndGetSignedUrl();
    }

    private void fetchGameDataAndGetSignedUrl() {
        if (gameId == null || gameId.isEmpty()) {
            Log.e("Game_mode", "Game ID is null or empty");
            Toast.makeText(this, "Game information not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Fetch game data from Firebase to get the user_id
        DatabaseReference gameRef = FirebaseDatabase.getInstance().getReference("games").child(gameId);
        gameRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String gameUserId = snapshot.child("user_id").getValue(String.class);
                    Log.d("Game_mode", "Game user_id from Firebase: " + gameUserId);
                    
                    if (gameUserId != null && !gameUserId.isEmpty()) {
                        // Now get the signed game URL using the game's user_id
                        getSignedGameUrl(gameUserId);
                    } else {
                        Log.e("Game_mode", "Game user_id is null or empty");
                        Toast.makeText(Game_mode.this, "Game owner information not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Log.e("Game_mode", "Game not found in Firebase: " + gameId);
                    Toast.makeText(Game_mode.this, "Game not found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Game_mode", "Error fetching game data: " + error.getMessage());
                Toast.makeText(Game_mode.this, "Error loading game data", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void getSignedGameUrl(String gameUserId) {
        // Build the link-signer URL with game's user_id and gameid parameters
        String linkSignerUrl = "https://link-signer.genzopia.workers.dev/?userid=" + gameUserId + "&gameid=" + gameId;
        
        // Detailed logging to verify the request
        Log.d("Game_mode", "=== LINK-SIGNER REQUEST DETAILS ===");
        Log.d("Game_mode", "Base URL: https://link-signer.genzopia.workers.dev/");
        Log.d("Game_mode", "Game User ID (from game data): " + gameUserId);
        Log.d("Game_mode", "Game ID: " + gameId);
        Log.d("Game_mode", "Full URL: " + linkSignerUrl);
        Log.d("Game_mode", "URL Parameters:");
        Log.d("Game_mode", "  - userid: " + gameUserId);
        Log.d("Game_mode", "  - gameid: " + gameId);
        Log.d("Game_mode", "=====================================");

        Request request = new Request.Builder()
                .url(linkSignerUrl)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("Game_mode", "Network error: " + e.getMessage());
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
                    Log.d("Game_mode", "Link-signer response: " + responseBody);
                    
                    try {
                        // Parse JSON response
                        Gson gson = new Gson();
                        JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                        
                        boolean success = jsonResponse.get("success").getAsBoolean();
                        if (success) {
                            String signedGameUrl = jsonResponse.get("url").getAsString();
                            Log.d("Game_mode", "Signed game URL: " + signedGameUrl);
                            
                            // Load the signed game URL in GeckoView
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    setupGeckoView(signedGameUrl);
                                } else {
                                    Log.w("Game_mode", "Activity is finishing, skipping GeckoView setup");
                                }
                            });
                        } else {
                            Log.e("Game_mode", "Link-signer returned success=false");
                            runOnUiThread(() -> {
                                if (!isFinishing() && !isDestroyed()) {
                                    Toast.makeText(Game_mode.this, "Failed to get game URL", Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            });
                        }
                    } catch (Exception e) {
                        Log.e("Game_mode", "Error parsing JSON response: " + e.getMessage());
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) {
                                Toast.makeText(Game_mode.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                                finish();
                            }
                        });
                    }
                } else {
                    Log.e("Game_mode", "HTTP error: " + response.code());
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

    private void setupGeckoView(String gameUrl) {
        // Check if activity is still valid and binding is not null
        if (isFinishing() || isDestroyed() || binding == null) {
            Log.w("Game_mode", "Activity is finishing or binding is null, skipping GeckoView setup");
            return;
        }

        try {
        GeckoRuntime runtime = MyApplication.getGeckoRuntime(getBaseContext());

        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build();

        geckoSession = new GeckoSession(settings);
        geckoSession.open(runtime);

        binding.geckoView.setSession(geckoSession);
            
            // Load the signed game URL
            Log.d("Game_mode", "Loading signed game URL in GeckoView: " + gameUrl);
            geckoSession.loadUri(gameUrl);
        } catch (Exception e) {
            Log.e("Game_mode", "Error setting up GeckoView: " + e.getMessage());
            Toast.makeText(this, "Error loading game", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        if (geckoSession != null) {
            geckoSession.close();
            geckoSession = null;
        }

        if (executorService != null) {
            executorService.shutdown();
        }

        binding = null;
    }
    
    // Getter methods for accessing the stored values
    public String getGameId() {
        return gameId;
    }
}
