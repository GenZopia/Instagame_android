package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.onboarding.TutorialController;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.firebase.auth.FirebaseAuth;

import com.genzopia.Instagame.databinding.ActivityMainBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";
    private static boolean isAppInForeground = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Apply window insets: bottom nav consumes navigation bar height,
        // nav host fragment gets the remaining space (status bar is drawn behind).
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
            // Status bar padding on the root container top
            binding.getRoot().setPadding(0, systemBars.top, 0, 0);
            // Navigation bar padding on the bottom nav so it sits above the gesture bar
            binding.navView.setPadding(
                    binding.navView.getPaddingLeft(),
                    binding.navView.getPaddingTop(),
                    binding.navView.getPaddingRight(),
                    systemBars.bottom);
            return insets;
        });

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

            // Track bottom nav taps
            final String[] previousTab = {"dashboard"}; // default start tab

            // Block bottom nav if onboarding not yet complete
            navView.setOnItemSelectedListener(item -> {
                String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                        ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
                boolean onboardingDone = uid == null || TutorialController.isComplete(this, uid);

                if (!onboardingDone && item.getItemId() != R.id.navigation_dashboard) {
                    Toast.makeText(this,
                            "Please complete the onboarding first 🎮",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }

                // Resolve tab name for analytics
                String tabName;
                int id = item.getItemId();
                if (id == R.id.navigation_home)          tabName = "home";
                else if (id == R.id.navigation_dashboard) tabName = "dashboard";
                else if (id == R.id.navigation_notifications) tabName = "notifications";
                else                                      tabName = "profile";

                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackBottomNavTapped(
                        tabName, previousTab[0]);
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged(tabName);
                previousTab[0] = tabName;

                return NavigationUI.onNavDestinationSelected(item, navController);
            });
            
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

            // Handle deep link video ID forwarded from SplashActivity
            String deepLinkVideoId = intent.getStringExtra("deep_link_video_id");
            if (deepLinkVideoId != null && !deepLinkVideoId.isEmpty()) {
                intent.removeExtra("deep_link_video_id");
                com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                    .setPendingVideoId(deepLinkVideoId);
                com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                    .setShouldPlayInReelView(true);
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    navigateToDashboard();
                }, 1000);
                return;
            }

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
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
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
    
    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        Log.d(TAG, "App paused - videos should pause");
        // Broadcast pause event to fragments
        sendBroadcast(new Intent("com.genzopia.Instagame.ACTION_PAUSE_VIDEOS"));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        Log.d(TAG, "App resumed - videos should resume");
        // Broadcast resume event to fragments
        sendBroadcast(new Intent("com.genzopia.Instagame.ACTION_RESUME_VIDEOS"));
    }
    
    public static boolean isAppInForeground() {
        return isAppInForeground;
    }

}