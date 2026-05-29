package com.genzopia.Instagame.common.utils

import android.os.Build
import android.view.Window
import android.view.WindowInsetsController
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Utilities for controlling system bar (status bar & navigation bar) appearance
 * in a backward-compatible way down to API 24 (Android 7.0).
 */
object SystemBarUtils {

    /**
     * Apply the reel-screen look: true-black status bar + navigation bar
     * with light icons (white). Works on API 24 and up.
     */
    fun applyReelBars(window: Window) {
        // True black backgrounds always work
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK

        // Light (white) icons on the dark background
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false   // false = white icons
        controller.isAppearanceLightNavigationBars = false

        // On API 30+ also tell the system to draw behind bars for full-bleed video
        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController?.let { ic ->
                ic.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    /**
     * Restore bars to the default app state:
     *   - Status bar: solid black with white icons (dark theme style)
     *   - Navigation bar: subtle dark scrim so it's always present
     */
    fun restoreDefaultBars(window: Window) {
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = 0x33000000  // ~20% black — subtle, keeps nav bar visible

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false   // white icons on black status bar
        controller.isAppearanceLightNavigationBars = false  // white icons on scrim

        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController?.let { ic ->
                ic.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_DEFAULT
            }
        }
    }
}
