package com.genzopia.Instagame.webgl_gameloading;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.databinding.ActivityGameModeBinding;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

public class Game_mode extends AppCompatActivity {
    private ActivityGameModeBinding binding;
    private GeckoSession geckoSession;
    
    // Variables to store developer ID and game ID
    private String developerId;
    private String gameId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Inflate and set layout
        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Retrieve intent extras
        developerId = getIntent().getStringExtra("developer_id");
        gameId = getIntent().getStringExtra("game_id");
        
        // Log the received values for debugging
        Log.d("Game_mode", "Developer ID: " + developerId);
        Log.d("Game_mode", "Game ID: " + gameId);

        // Lock to landscape
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        setupGeckoView();
    }

    private void setupGeckoView() {
        GeckoRuntime runtime = MyApplication.getGeckoRuntime(getBaseContext());

        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
                .viewportMode(GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .build();

        geckoSession = new GeckoSession(settings);
        geckoSession.open(runtime);

        binding.geckoView.setSession(geckoSession);
        geckoSession.loadUri("www.youtube.com");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        if (geckoSession != null) {
            geckoSession.close();
            geckoSession = null;
        }

        binding = null;
    }
    
    // Getter methods for accessing the stored values
    public String getDeveloperId() {
        return developerId;
    }
    
    public String getGameId() {
        return gameId;
    }
}
