package com.genzopia.Instagame.webgl_gameloading;

import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.databinding.ActivityGameModeBinding;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

public class Game_mode extends AppCompatActivity {
    private ActivityGameModeBinding binding;
    private GeckoSession geckoSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        // Inflate and set layout
        binding = ActivityGameModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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
}
