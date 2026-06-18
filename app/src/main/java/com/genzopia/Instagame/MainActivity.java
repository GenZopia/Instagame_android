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

        // Register FCM token on app start (only if permission is granted)
        com.genzopia.Instagame.utils.FCMTokenManager.INSTANCE.registerToken(this);
        
        // Check if we have a game deeplink BEFORE setting up navigation
        // This prevents default navigation to dashboard (ReelView)
        Intent intent = getIntent();
        boolean hasGameDeeplink = intent != null && 
            intent.getStringExtra("deep_link_game_id") != null &&
            !intent.getStringExtra("deep_link_game_id").isEmpty();
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
            
            // If we have a game deeplink, navigate to home AFTER setting up bottom nav
            // Only do this on initial creation, not on config changes
            if (hasGameDeeplink && savedInstanceState == null) {
                navView.post(() -> {
                    try {
                        // Just set the selected item, the listener will handle navigation
                        navView.setSelectedItemId(R.id.navigation_home);
                        Log.d(TAG, "Selected home tab for deeplink");
                    } catch (Exception e) {
                        Log.e(TAG, "Error selecting home for deeplink: " + e.getMessage());
                    }
                });
            }
        } else {
            Log.e(TAG, "BottomNavigationView not found!");
        }
        
        // Process game deeplink if present
        processGameDeeplink();
        // Process notification intent if launched from a push notification
        processNotificationIntent(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processGameDeeplink();
        processNotificationIntent(intent);
    }
    
    /**
     * Routes incoming notification intents to the appropriate screen.
     * Supports actions: open_game, open_profile, open_video, open_home.
     * Requirements: 1.5
     */
    private void processNotificationIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("notification_action");
        String targetId = intent.getStringExtra("notification_target_id");
        if (action == null) return;

        Log.d(TAG, "Notification action: " + action + ", targetId: " + targetId);

        BottomNavigationView navView = findViewById(R.id.nav_view);
        switch (action) {
            case "open_game":
                if (targetId != null && !targetId.isEmpty()) {
                    com.genzopia.Instagame.utils.GameNavigationManager.getInstance()
                        .setPendingGameId(targetId);
                }
                if (navView != null) navView.setSelectedItemId(R.id.navigation_home);
                break;
            case "open_profile":
                if (navView != null) navView.setSelectedItemId(R.id.navigation_profile);
                break;
            case "open_video":
                if (targetId != null && !targetId.isEmpty()) {
                    com.genzopia.Instagame.utils.VideoNavigationManager
                        .getInstance().setPendingVideoId(targetId);
                }
                if (navView != null) navView.setSelectedItemId(R.id.navigation_dashboard);
                break;
            case "open_home":
            default:
                if (navView != null) navView.setSelectedItemId(R.id.navigation_home);
                break;
        }
        // Clear extras so re-delivery doesn't re-route
        intent.removeExtra("notification_action");
        intent.removeExtra("notification_target_id");
    }

    private void processGameDeeplink() {
        Intent intent = getIntent();
        if (intent != null) {
            // Handle deep link game ID forwarded from SplashActivity
            String deepLinkGameId = intent.getStringExtra("deep_link_game_id");
            if (deepLinkGameId != null && !deepLinkGameId.isEmpty()) {
                intent.removeExtra("deep_link_game_id");
                // Store the game ID to be picked up by HomeFragmentCompose
                com.genzopia.Instagame.utils.GameNavigationManager.getInstance()
                    .setPendingGameId(deepLinkGameId);
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

    /**
     * Navigate to home fragment (for game deep links)
     */
    public void navigateToHome() {
        try {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
            navController.navigate(R.id.navigation_home);
            
            // Update bottom navigation selection
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView != null) {
                navView.setSelectedItemId(R.id.navigation_home);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to home: " + e.getMessage());
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