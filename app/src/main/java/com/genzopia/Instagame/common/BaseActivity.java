package com.genzopia.Instagame.common;

import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity — extend every Activity in the app from this.
 *
 * Responsibilities:
 *  1. Enables edge-to-edge rendering so content draws behind system bars.
 *  2. Requests the highest refresh rate the display supports (120 Hz, 90 Hz, etc.)
 *  3. Keeps the screen on while the app is in the foreground.
 *  4. Ensures hardware acceleration is active (belt-and-suspenders on top of the manifest flag).
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Enable edge-to-edge with a solid black status bar (white icons) and
        // a subtle dark scrim on the nav bar (white icons).
        // Individual screens can override these colors when needed (e.g. reel → pure black).
        // Using SystemBarStyle avoids the flickering/override issues that happen when
        // setting window.statusBarColor manually in onResume.
        EdgeToEdge.enable(
                this,
                SystemBarStyle.dark(android.graphics.Color.BLACK),    // status bar: solid black, white icons
                SystemBarStyle.dark(0x33000000)                       // nav bar: subtle scrim, white icons
        );
        // Must be called BEFORE super.onCreate so window flags are set early.
        applyMaxRefreshRate();
        super.onCreate(savedInstanceState);
    }

    private void applyMaxRefreshRate() {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
        setHighestRefreshRateMode();
    }

    private void setHighestRefreshRateMode() {
        // preferredDisplayModeId requires API 23+.
        // Remove this check if your minSdk is already >= 23.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;

        try {
            Display display = getWindowManager().getDefaultDisplay();
            Display.Mode[] modes = display.getSupportedModes();
            if (modes == null || modes.length == 0) return;

            Display.Mode bestMode = modes[0];
            for (Display.Mode mode : modes) {
                if (mode.getRefreshRate() > bestMode.getRefreshRate()) {
                    bestMode = mode;
                }
            }

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.preferredDisplayModeId = bestMode.getModeId();
            getWindow().setAttributes(params);
        } catch (Exception e) {
            // Silently ignore — device may not support mode switching.
        }
    }
}