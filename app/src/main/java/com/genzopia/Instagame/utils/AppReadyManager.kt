package com.genzopia.Instagame.utils

import android.util.Log

/**
 * Manages app ready state to coordinate splash screen dismissal.
 * All state mutations are synchronized to prevent the race condition where
 * two threads both see isDataReady && isUIReady == true and fire the callback twice.
 */
object AppReadyManager {
    private const val TAG = "AppReadyManager"

    private var isDataReady = false
    private var isUIReady = false
    private var onReadyCallback: (() -> Unit)? = null

    @Synchronized
    fun markDataReady() {
        Log.d(TAG, "Data marked as ready")
        isDataReady = true
        checkAndNotify()
    }

    @Synchronized
    fun markUIReady() {
        Log.d(TAG, "UI marked as ready")
        isUIReady = true
        checkAndNotify()
    }

    @Synchronized
    fun setOnReadyCallback(callback: () -> Unit) {
        onReadyCallback = callback
        checkAndNotify()
    }

    // Must be called from a synchronized context
    private fun checkAndNotify() {
        if (isDataReady && isUIReady) {
            Log.d(TAG, "Both data and UI ready - invoking callback")
            val cb = onReadyCallback
            reset()          // clear before invoking so a re-entrant call won't double-fire
            cb?.invoke()
        }
    }

    @Synchronized
    fun reset() {
        isDataReady = false
        isUIReady = false
        onReadyCallback = null
    }

    @Synchronized
    fun isReady(): Boolean = isDataReady && isUIReady
}
