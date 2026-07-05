package com.genzopia.Instagame;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.core.splashscreen.SplashScreen;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.genzopia.Instagame.LoginActivities.LoginActivity;
import com.genzopia.Instagame.common.BaseActivity;
import com.genzopia.Instagame.databinding.ActivityMainBinding;
import com.genzopia.Instagame.onboarding.TutorialController;
import com.genzopia.Instagame.utils.NotificationPermissionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private static final String TAG = "MainActivity";
    private static boolean isAppInForeground = true;
    private NotificationPermissionManager notificationPermissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install splash — OS dismisses it as soon as the first frame is drawn (no artificial wait)
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setOnExitAnimationListener(provider -> provider.remove());
        super.onCreate(savedInstanceState);

        // Analytics
        android.net.Uri deepLinkUri = getIntent().getData();
        boolean isDeepLink = deepLinkUri != null;
        com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackAppOpened(
                isDeepLink ? "deep_link" : "cold_start",
                isDeepLink && deepLinkUri.getLastPathSegment() != null
                        ? deepLinkUri.getLastPathSegment() : null
        );

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            routeToLogin();
            return;
        }

        setupMainUI();
    }

    private void routeToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Build the main UI — only called when user is authenticated and data is ready. */
    private void setupMainUI() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // FCM token + notification permission
        com.genzopia.Instagame.utils.FCMTokenManager.INSTANCE.registerToken(this);
        notificationPermissionManager = new NotificationPermissionManager(this);
        if (notificationPermissionManager.shouldRequestPermission()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                androidx.core.app.ActivityCompat.requestPermissions(
                        this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION
                );
            }
        }

        // Handle deep link URI that launched this activity
        android.net.Uri deepLinkUri = getIntent().getData();
        if (deepLinkUri != null) {
            String gameId = resolveGameId(deepLinkUri);
            if (gameId != null && !gameId.isEmpty()) {
                getIntent().putExtra("deep_link_game_id", gameId);
                getIntent().setData(null);
            }
        }

        Intent intent = getIntent();
        boolean hasGameDeeplink = intent != null &&
                intent.getStringExtra("deep_link_game_id") != null &&
                !intent.getStringExtra("deep_link_game_id").isEmpty();

        // Edge-to-edge insets
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars());
            binding.getRoot().setPadding(0, systemBars.top, 0, 0);
            binding.navView.setPadding(
                    binding.navView.getPaddingLeft(),
                    binding.navView.getPaddingTop(),
                    binding.navView.getPaddingRight(),
                    systemBars.bottom);
            return insets;
        });

        BottomNavigationView navView = findViewById(R.id.nav_view);
        if (navView != null) {
            navView.setVisibility(View.VISIBLE);

            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.navigation_home, R.id.navigation_dashboard,
                    R.id.navigation_notifications, R.id.navigation_profile)
                    .build();
            NavController navController = Navigation.findNavController(
                    this, R.id.nav_host_fragment_activity_main);
            NavigationUI.setupWithNavController(navView, navController);

            final String[] previousTab = {"dashboard"};

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

                String tabName;
                int id = item.getItemId();
                if (id == R.id.navigation_home)               tabName = "home";
                else if (id == R.id.navigation_dashboard)     tabName = "dashboard";
                else if (id == R.id.navigation_notifications) tabName = "notifications";
                else                                          tabName = "profile";

                com.genzopia.Instagame.analytics.InstagameAnalytics.INSTANCE.trackBottomNavTapped(
                        tabName, previousTab[0]);
                com.genzopia.Instagame.analytics.SessionTracker.INSTANCE.onScreenChanged(tabName);
                previousTab[0] = tabName;

                return NavigationUI.onNavDestinationSelected(item, navController);
            });

            if (hasGameDeeplink) {
                navView.post(() -> navView.setSelectedItemId(R.id.navigation_home));
            }
        } else {
            Log.e(TAG, "BottomNavigationView not found!");
        }

        processGameDeeplink();
        processNotificationIntent(getIntent());
    }

    // ── Deep link / URI helpers ───────────────────────────────────────────

    private String resolveGameId(android.net.Uri data) {
        String scheme = data.getScheme();
        String host = data.getHost();
        if (("https".equals(scheme) || "http".equals(scheme)) &&
                ("www.genzopia.com".equals(host) || "genzopia.com".equals(host))) {
            String path = data.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                return path.startsWith("/games/")
                        ? path.substring("/games/".length())
                        : (path.startsWith("/") ? path.substring(1) : path);
            }
        } else if ("genzopia".equals(scheme) && "game".equals(host)) {
            return data.getLastPathSegment();
        }
        return null;
    }

    private void processGameDeeplink() {
        Intent intent = getIntent();
        if (intent == null) return;
        String deepLinkGameId = intent.getStringExtra("deep_link_game_id");
        if (deepLinkGameId != null && !deepLinkGameId.isEmpty()) {
            intent.removeExtra("deep_link_game_id");
            com.genzopia.Instagame.utils.GameNavigationManager.getInstance()
                    .setPendingGameId(deepLinkGameId);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        processGameDeeplink();
        processNotificationIntent(intent);
    }

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
        intent.removeExtra("notification_action");
        intent.removeExtra("notification_target_id");
    }

    // ── Navigation helpers (called from other fragments) ──────────────────

    public void navigateToDashboard() {
        try {
            NavController navController = Navigation.findNavController(
                    this, R.id.nav_host_fragment_activity_main);
            navController.navigate(R.id.navigation_dashboard);
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView != null) navView.setSelectedItemId(R.id.navigation_dashboard);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to dashboard: " + e.getMessage());
        }
    }

    public void navigateToHome() {
        try {
            NavController navController = Navigation.findNavController(
                    this, R.id.nav_host_fragment_activity_main);
            navController.navigate(R.id.navigation_home);
            BottomNavigationView navView = findViewById(R.id.nav_view);
            if (navView != null) navView.setSelectedItemId(R.id.navigation_home);
        } catch (Exception e) {
            Log.e(TAG, "Error navigating to home: " + e.getMessage());
        }
    }

    // ── Permission result ─────────────────────────────────────────────────

    @Override
    public void onRequestPermissionsResult(int requestCode,
            @androidx.annotation.NonNull String[] permissions,
            @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION) {
            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            notificationPermissionManager.handlePermissionResult(granted);
            if (!granted && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (!androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                        this, android.Manifest.permission.POST_NOTIFICATIONS)) {
                    notificationPermissionManager.markPermanentlyDenied();
                }
            }
            if (granted) com.genzopia.Instagame.utils.FCMTokenManager.INSTANCE.registerToken(this);
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    @Override
    protected void onPause() {
        super.onPause();
        isAppInForeground = false;
        sendBroadcast(new Intent("com.genzopia.Instagame.ACTION_PAUSE_VIDEOS"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        isAppInForeground = true;
        sendBroadcast(new Intent("com.genzopia.Instagame.ACTION_RESUME_VIDEOS"));
    }

    public static boolean isAppInForeground() {
        return isAppInForeground;
    }
}
