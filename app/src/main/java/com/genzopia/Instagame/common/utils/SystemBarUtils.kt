package com.genzopia.Instagame.common.utils

import android.os.Build
import android.view.Window
import android.view.WindowInsetsController
import androidx.core.view.WindowInsetsCompat
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
     * Restore the default edge-to-edge transparent bars used by the rest of the app.
     */
    fun restoreDefaultBars(window: Window) {
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        // Default: light icons on light backgrounds
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true

        if (Build.VERSION.SDK_INT >= 30) {
            window.decorView.windowInsetsController?.let { ic ->
                ic.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_DEFAULT
            }
        }
    }

    /**
     * Hide the system bars (for true full-screen experiences).
     * Call when the reel screen is showing.
     */
    fun hideSystemBars(window: Window) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /**
     * Show the system bars again (when leaving reel screen).
     */
    fun showSystemBars(window: Window) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
