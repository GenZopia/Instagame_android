package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.genzopia.Instagame.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Ensure bottom navigation is visible and properly positioned
        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            Log.d(TAG, "BottomNavigationView found");
            navView.setVisibility(View.VISIBLE);
            
            // Force the bottom navigation to be visible
            navView.post(() -> {
                navView.setVisibility(View.VISIBLE);
                Log.d(TAG, "BottomNavigationView visibility set to VISIBLE");
            });
            
            // Passing each menu ID as a set of Ids because each
            // menu should be considered as top level destinations.
            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications,R.id.navigation_profile)
                    .build();
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            NavigationUI.setupWithNavController(navView, navController);
            
            Log.d(TAG, "BottomNavigationView setup completed");
        } else {
            Log.e(TAG, "BottomNavigationView not found!");
        }
        
        // Check if we need to navigate to dashboard
        checkForDashboardNavigation();
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkForDashboardNavigation();
    }
    
    private void checkForDashboardNavigation() {
        Intent intent = getIntent();
        if (intent != null) {
            boolean shouldNavigateToDashboard = intent.getBooleanExtra("navigate_to_dashboard", false);
            String videoIdToPlay = intent.getStringExtra("play_video_id");
            
            if (shouldNavigateToDashboard) {
                // Clear the flags
                intent.removeExtra("navigate_to_dashboard");
                intent.removeExtra("play_video_id");
                
                // Set the video to play in VideoNavigationManager
                if (videoIdToPlay != null) {
                    com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                        .setPendingVideoId(videoIdToPlay);
                    com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                        .setShouldPlayInReelView(true);
                }
                
                // Navigate to dashboard after a short delay to ensure activity is ready
                new android.os.Handler().postDelayed(() -> {
                    navigateToDashboard();
                }, 1000);
            }
        }
    }

    /**
     * Navigate to dashboard fragment
     */
    public void navigateToDashboard() {
        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            navController.navigate(R.id.navigation_dashboard);
            
            // Update bottom navigation selection
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView != null) {
                navView.setSelectedItemId(R.id.navigation_dashboard);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to dashboard: " + e.getMessage());
        }
    }

}