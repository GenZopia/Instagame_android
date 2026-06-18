package com.genzopia.Instagame

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bumptech.glide.Glide
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.genzopia.Instagame.utils.FCMTokenManager
import java.util.concurrent.ExecutionException

/**
 * FCM Service — handles data-only push notifications with rich media support.
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 4.2, 4.3, 4.4, 7.1, 7.2, 7.5
 */
class InstagameFCMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "InstagameFCMService"
        private const val CHANNEL_ID = "fcm_default_channel"
        private const val CHANNEL_NAME = "Push Notifications"
    }

    /** Called when a data-only FCM message arrives (foreground + background). */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")
        if (remoteMessage.data.isEmpty()) return

        val data = remoteMessage.data
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val imageUrl = data["imageUrl"]
        showNotification(title, body, imageUrl, data)
    }

    /** Called when FCM token refreshes — sync to database. Requirements: 4.2 */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        FCMTokenManager.updateTokenInDatabase(token)
    }

    // ── Notification display ────────────────────────────────────────────────

    private fun showNotification(
        title: String,
        body: String,
        imageUrl: String?,
        data: Map<String, String>
    ) {
        try {
            createNotificationChannel()
            val pendingIntent = createPendingIntent(data)
            val bitmap = if (!imageUrl.isNullOrBlank()) downloadImage(imageUrl) else null

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            if (bitmap != null) {
                // Req 1.1, 1.2: BigPictureStyle displays image at any aspect ratio
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(title)
                        .setSummaryText(body)
                )
            } else {
                // Req 1.3: text-only fallback
                builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }

            val notificationManager = NotificationManagerCompat.from(this)
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying notification: ${e.message}", e)
        }
    }

    /** Downloads image from URL; returns null gracefully on failure. Req 1.4, 7.2 */
    private fun downloadImage(imageUrl: String): Bitmap? {
        return try {
            Glide.with(applicationContext)
                .asBitmap()
                .load(imageUrl)
                .submit()
                .get()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM downloading notification image: $imageUrl")
            null
        } catch (e: ExecutionException) {
            Log.w(TAG, "Failed to download notification image: ${e.message}")
            null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while downloading notification image")
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    /** Builds a PendingIntent that routes to the right screen. Req 1.5 */
    private fun createPendingIntent(data: Map<String, String>): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data["action"]?.let { putExtra("notification_action", it) }
            data["targetId"]?.let { putExtra("notification_target_id", it) }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(this, 0, intent, flags)
    }
}
