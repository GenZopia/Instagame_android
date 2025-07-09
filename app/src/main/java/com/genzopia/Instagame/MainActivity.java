package com.genzopia.Instagame;

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
    }

}