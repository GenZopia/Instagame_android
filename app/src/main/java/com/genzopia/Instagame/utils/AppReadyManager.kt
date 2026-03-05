package com.genzopia.Instagame.utils

import android.util.Log

/**
 * Manages app ready state to coordinate splash screen dismissal
 */
object AppReadyManager {
    private const val TAG = "AppReadyManager"
    
    @Volatile
    private var isDataReady = false
    
    @Volatile
    private var isUIReady = false
    
    private var onReadyCallback: (() -> Unit)? = null
    
    /**
     * Mark data as ready (called from SplashActivity when prefetch completes)
     */
    fun markDataReady() {
        Log.d(TAG, "Data marked as ready")
        isDataReady = true
        checkAndNotify()
    }
    
    /**
     * Mark UI as ready (called from MainActivity when first frame is rendered)
     */
    fun markUIReady() {
        Log.d(TAG, "UI marked as ready")
        isUIReady = true
        checkAndNotify()
    }
    
    /**
     * Set callback to be invoked when both data and UI are ready
     */
    fun setOnReadyCallback(callback: () -> Unit) {
        onReadyCallback = callback
        checkAndNotify()
    }
    
    private fun checkAndNotify() {
        if (isDataReady && isUIReady) {
            Log.d(TAG, "Both data and UI ready - invoking callback")
            onReadyCallback?.invoke()
            reset()
        }
    }
    
    /**
     * Reset state for next app launch
     */
    fun reset() {
        isDataReady = false
        isUIReady = false
        onReadyCallback = null
    }
    
    /**
     * Check if app is ready
     */
    fun isReady(): Boolean = isDataReady && isUIReady
}
