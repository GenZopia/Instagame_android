package com.genzopia.Instagame.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

/**
 * Centralized management of notification permission requests and rejection tracking.
 * 
 * This manager handles:
 * - Permission state checking across Android versions
 * - 30-day retry interval after rejection
 * - Rejection timestamp storage in SharedPreferences
 * - Permanently denied state tracking
 * 
 * Usage:
 * ```
 * val manager = NotificationPermissionManager(context)
 * if (manager.shouldRequestPermission()) {
 *     manager.requestPermission(activity, REQUEST_CODE)
 * }
 * ```
 */
class NotificationPermissionManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "notification_permission_prefs"
        private const val KEY_LAST_REJECTION_TIMESTAMP = "last_rejection_timestamp"
        private const val KEY_PERMISSION_PERMANENTLY_DENIED = "permission_permanently_denied"
        private const val RETRY_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days in milliseconds
        
        /**
         * Request code for notification permission requests.
         * Activities should use this code when requesting permission.
         */
        const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1001
    }
    
    private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if notification permission is granted.
     * 
     * @return true if permission is granted, or if Android version < 13 (permission not required)
     */
    fun isPermissionGranted(): Boolean {
        // For Android 12 and below, notifications are allowed by default
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        
        // For Android 13+, check POST_NOTIFICATIONS permission
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Check if we should request notification permission.
     * 
     * Returns true if:
     * - Android version >= 13
     * - Permission not already granted
     * - Permission not permanently denied
     * - Either never been rejected, or 30 days have passed since last rejection
     * 
     * @return true if permission should be requested
     */
    fun shouldRequestPermission(): Boolean {
        // For Android 12 and below, no need to request
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        
        // Already granted
        if (isPermissionGranted()) {
            return false
        }
        
        // Permanently denied (user selected "Never ask again")
        if (isPermanentlyDenied()) {
            return false
        }
        
        // Check if 30 days have passed since last rejection
        val lastRejectionTimestamp = getLastRejectionTimestamp()
        if (lastRejectionTimestamp == 0L) {
            // Never been rejected, should request
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceRejection = currentTime - lastRejectionTimestamp
        
        return timeSinceRejection >= RETRY_INTERVAL_MS
    }
    
    /**
     * Request notification permission with proper handling.
     * 
     * This method should be called from an Activity when shouldRequestPermission() returns true.
     * The activity must override onRequestPermissionsResult() to handle the result.
     * 
     * @param activity The activity requesting permission
     * @param requestCode The request code to use (default: REQUEST_CODE_NOTIFICATION_PERMISSION)
     */
    fun requestPermission(activity: Activity, requestCode: Int = REQUEST_CODE_NOTIFICATION_PERMISSION) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }
    
    /**
     * Handle permission result from activity.
     * 
     * Call this method from onRequestPermissionsResult() in your activity:
     * ```
     * override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
     *     if (requestCode == NotificationPermissionManager.REQUEST_CODE_NOTIFICATION_PERMISSION) {
     *         val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
     *         notificationPermissionManager.handlePermissionResult(granted)
     *     }
     * }
     * ```
     * 
     * @param granted true if permission was granted, false if denied
     */
    fun handlePermissionResult(granted: Boolean) {
        if (granted) {
            // Permission granted, clear rejection timestamp
            clearRejectionTimestamp()
        } else {
            // Permission denied, store current timestamp
            storeRejectionTimestamp(System.currentTimeMillis())
        }
    }
    
    /**
     * Mark permission as permanently denied (user selected "Never ask again").
     * 
     * To detect permanent denial, check in your activity:
     * ```
     * if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
     *     // User selected "Never ask again"
     *     manager.markPermanentlyDenied()
     * }
     * ```
     */
    fun markPermanentlyDenied() {
        sharedPreferences.edit()
            .putBoolean(KEY_PERMISSION_PERMANENTLY_DENIED, true)
            .apply()
    }
    
    /**
     * Check if permission is permanently denied.
     * 
     * @return true if user selected "Never ask again"
     */
    fun isPermanentlyDenied(): Boolean {
        return sharedPreferences.getBoolean(KEY_PERMISSION_PERMANENTLY_DENIED, false)
    }
    
    /**
     * Get the timestamp of the last permission rejection.
     * 
     * @return timestamp in milliseconds, or 0 if never rejected
     */
    fun getLastRejectionTimestamp(): Long {
        return sharedPreferences.getLong(KEY_LAST_REJECTION_TIMESTAMP, 0L)
    }
    
    /**
     * Store the rejection timestamp.
     * 
     * @param timestamp timestamp in milliseconds
     */
    private fun storeRejectionTimestamp(timestamp: Long) {
        sharedPreferences.edit()
            .putLong(KEY_LAST_REJECTION_TIMESTAMP, timestamp)
            .apply()
    }
    
    /**
     * Get days remaining until next retry.
     * 
     * @return number of days remaining, or 0 if retry is available
     */
    fun getDaysUntilNextRetry(): Int {
        val lastRejectionTimestamp = getLastRejectionTimestamp()
        if (lastRejectionTimestamp == 0L) {
            return 0
        }
        
        val currentTime = System.currentTimeMillis()
        val timeSinceRejection = currentTime - lastRejectionTimestamp
        
        if (timeSinceRejection >= RETRY_INTERVAL_MS) {
            return 0
        }
        
        val timeRemaining = RETRY_INTERVAL_MS - timeSinceRejection
        return TimeUnit.MILLISECONDS.toDays(timeRemaining).toInt()
    }
    
    /**
     * Clear rejection timestamp (for testing/debugging or when permission is granted).
     */
    fun clearRejectionTimestamp() {
        sharedPreferences.edit()
            .remove(KEY_LAST_REJECTION_TIMESTAMP)
            .remove(KEY_PERMISSION_PERMANENTLY_DENIED)
            .apply()
    }
}
