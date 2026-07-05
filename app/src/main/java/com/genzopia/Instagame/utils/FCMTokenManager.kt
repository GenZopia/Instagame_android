package com.genzopia.Instagame.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.genzopia.Instagame.gateway.FcmTokenRequest
import com.genzopia.Instagame.gateway.GatewayClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Manages FCM token registration and synchronization with Firebase Realtime Database.
 * Requirements: 4.1, 4.2, 4.5, 7.1
 */
object FCMTokenManager {

    private const val TAG = "FCMTokenManager"
    private const val PREFS_NAME = "fcm_token_prefs"
    private const val KEY_FCM_TOKEN = "fcm_token"

    /**
     * Retrieves the current FCM token and registers it with the database if a user is signed in.
     * Only registers if notification permission is granted.
     * Requirements: 4.1, 4.5
     */
    fun registerToken(context: Context) {
        val permissionManager = NotificationPermissionManager(context)
        if (!permissionManager.isPermissionGranted()) {
            Log.d(TAG, "Notification permission not granted — skipping FCM token registration")
            return
        }
        // Subscribe to broadcast topic so all-user notifications are received
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")
            .addOnFailureListener { e -> Log.e(TAG, "Topic subscription failed: ${e.message}") }

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                cacheToken(context, token)
                updateTokenInDatabase(token, context)
                // Subscribe to per-user topic for targeted notifications
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    FirebaseMessaging.getInstance().subscribeToTopic("uid_$uid")
                        .addOnFailureListener { e -> Log.e(TAG, "User topic subscription failed: ${e.message}") }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to get FCM token: ${e.message}", e)
            }
    }

    /**
     * Updates the FCM token via the backend Gateway (POST /users/me/fcm-token).
     * On failure, schedules a WorkManager retry. Requirements: 12.1, 12.2
     */
    fun updateTokenInDatabase(token: String, context: Context? = null) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.d(TAG, "No signed-in user — skipping token gateway update")
            return
        }
        GatewayClient.callApi.registerFcmToken(FcmTokenRequest(token)).enqueue(object : retrofit2.Callback<Void> {
            override fun onResponse(call: retrofit2.Call<Void>, response: retrofit2.Response<Void>) {
                if (response.isSuccessful) {
                    Log.d(TAG, "FCM token registered via gateway for uid=$uid")
                } else {
                    Log.e(TAG, "Gateway FCM token HTTP ${response.code()} for uid=$uid")
                    context?.let { scheduleTokenSync(it) }
                }
            }
            override fun onFailure(call: retrofit2.Call<Void>, t: Throwable) {
                Log.e(TAG, "Failed to register FCM token via gateway for uid=$uid: ${t.message}", t)
                context?.let { scheduleTokenSync(it) }
            }
        })
    }

    /** Schedules a WorkManager job to retry token sync when network is available. */
    private fun scheduleTokenSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<TokenSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cacheToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getCachedToken(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FCM_TOKEN, null)
}

/** WorkManager Worker that retries FCM token registration when network is available. */
class TokenSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            FCMTokenManager.registerToken(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Log.e("TokenSyncWorker", "Token sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
