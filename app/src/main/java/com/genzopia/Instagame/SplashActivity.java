package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.airbnb.lottie.LottieAnimationView;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_splash);

        LottieAnimationView lottieView = findViewById(R.id.lottie_splash);
        int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            lottieView.setAnimation(R.raw.game_logo_white_theme);
        } else {
            lottieView.setAnimation(R.raw.game_logo_dark_theme);
        }
        lottieView.playAnimation();

        // Set the duration for the splash screen (e.g., 2 seconds)
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Start MainActivity after the splash screen
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);
                finish(); // Close SplashActivity so it's not shown when back button is pressed
            }
        }, 3000); // 3000 milliseconds = 3 seconds
    }

}
