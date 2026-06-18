package com.genzopia.Instagame

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM Service for handling push notifications with rich media support.
 * 
 * This service receives data-only FCM payloads and displays notifications with:
 * - Title and body text
 * - Optional images with automatic aspect ratio handling
 * - Custom routing based on notification payload
 * 
 * Note: Uses DATA payloads (not notification payloads) for full control over
 * notification display in both foreground and background.
 */
class InstagameFCMService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "InstagameFCMService"
    }
    
    /**
     * Called when a new FCM data message is received.
     * Handles both foreground and background delivery.
     * 
     * Note: Only triggered for DATA payloads, not notification payloads
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")
        
        // Extract data payload
        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: $data")
            
            // TODO: Extract title, body, imageUrl, action, targetId from data
            // TODO: Download image if present
            // TODO: Build and display notification
        }
    }
    
    /**
     * Called when FCM token is refreshed.
     * Updates token in Firebase Realtime Database.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        Log.d(TAG, "FCM token refreshed: $token")
        
        // TODO: Update token in Firebase Realtime Database
        // TODO: Store token in SharedPreferences
    }
}
