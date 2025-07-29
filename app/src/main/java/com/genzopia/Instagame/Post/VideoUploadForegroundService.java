package com.genzopia.Instagame.Post;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.genzopia.Instagame.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VideoUploadForegroundService extends Service {
    public static final String CHANNEL_ID = "video_upload_channel";
    public static final String ACTION_UPLOAD = "com.genzopia.Instagame.UPLOAD_VIDEO";
    public static final String ACTION_CANCEL = "com.genzopia.Instagame.CANCEL_UPLOAD";
    public static final String BROADCAST_PROGRESS = "com.genzopia.Instagame.UPLOAD_PROGRESS";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_DESCRIPTION = "description";
    public static final String EXTRA_GAME_ID = "game_id";
    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_FILE_EXTENSION = "file_extension";
    public static final String DEVID = "dev_id";
    public static final String EXTRA_RESULT = "result";

    private boolean isCancelled = false;
    private int notificationId = 1001;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPLOAD.equals(intent.getAction())) {
            String title = intent.getStringExtra(EXTRA_TITLE);
            String description = intent.getStringExtra(EXTRA_DESCRIPTION);
            String gameId = intent.getStringExtra(EXTRA_GAME_ID);
            String videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI);
            String fileExtension = intent.getStringExtra(EXTRA_FILE_EXTENSION);
            String devid=intent.getStringExtra(DEVID);
            uploadVideo(title, description, gameId, videoUriString, fileExtension,devid);
        } else if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            isCancelled = true;
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void uploadVideo(String title, String description, String gameId, String videoUriString, String fileExtension,String devid) {
        new Thread(() -> {
            try {
                String videoUniqueId = "video_" + UUID.randomUUID().toString().replace("-", "");
                File originalFile = FileUtils.getFileFromUri(this, android.net.Uri.parse(videoUriString));
                if (originalFile == null) {
                    sendResult("fail");
                    stopForeground(true);
                    stopSelf();
                    return;
                }
                File renamedFile = new File(getCacheDir(), videoUniqueId + fileExtension);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    java.nio.file.Files.copy(originalFile.toPath(), renamedFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                showNotification(0, false);
                // Upload to Cloudflare with real progress
                FileUploader.uploadFileToWorker(renamedFile, "video", Map.of(
                        "video_id", videoUniqueId,
                        "title", title,
                        "description", description,
                        "game_id", gameId
                ), (success, response) -> {
                    if (success) {
                        saveVideoMetadataToFirebase(title, description, gameId, renamedFile, fileExtension, videoUniqueId,devid);
                        renamedFile.delete();
                        showNotification(100, true);
                        sendResult("success");
                    } else {
                        renamedFile.delete();
                        showNotification(0, true);
                        sendResult("fail");
                    }
                    stopForeground(true);
                    stopSelf();
                }, progress -> {
                    showNotification(progress, false);
                    sendProgress(progress);
                });
            } catch (Exception e) {
                sendResult("fail");
                stopForeground(true);
                stopSelf();
            }
        }).start();
    }

    private void showNotification(int progress, boolean done) {
        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(done ? "Upload Complete" : "Uploading video…")
                .setContentText(done ? "Your video was uploaded." : "Please wait while your video uploads.")
                .setOngoing(!done)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        if (!done) {
            builder.setProgress(100, progress, false);
            builder.addAction(new NotificationCompat.Action(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Cancel",
                    PendingIntent.getService(this, 0, new Intent(this, VideoUploadForegroundService.class).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
            ));
        } else {
            builder.setProgress(0, 0, false);
        }
        Notification notification = builder.build();
        startForeground(notificationId, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Video Uploads",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows upload progress for videos");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private void sendProgress(int progress) {
        Intent intent = new Intent(BROADCAST_PROGRESS);
        intent.putExtra(EXTRA_PROGRESS, progress);
        sendBroadcast(intent);
    }

    private void sendResult(String result) {
        Intent intent = new Intent(BROADCAST_PROGRESS);
        intent.putExtra(EXTRA_RESULT, result);
        sendBroadcast(intent);
    }

    private void saveVideoMetadataToFirebase(String title, String description, String gameId, File file, String fileExtension, String videoUniqueId,String devid) {
        String now = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault()).format(new Date());
        
        // 1. Save detailed video information in the videos node
        DatabaseReference videosRef = FirebaseDatabase.getInstance().getReference("videos").child(videoUniqueId);
        Map<String, Object> videoData = new HashMap<>();
        videoData.put("created_at", now);
        videoData.put("description", description);
        videoData.put("file_size", String.valueOf(file.length()));
        videoData.put("game_id", gameId);
        videoData.put("is_verified", false);
        videoData.put("like_count", "0");
        videoData.put("share_count", "0");
        videoData.put("user_id", devid);
        videoData.put("video_id", videoUniqueId);  // Store clean video ID without extension
        videoData.put("video_title", title);
        videoData.put("view_count", "0");
        
        // Save to videos node
        videosRef.setValue(videoData).addOnSuccessListener(aVoid -> {
            // 2. Save video association in user's videos node (video_id = true)
            DatabaseReference userVideosRef = FirebaseDatabase.getInstance().getReference("users").child(devid).child("videos").child(videoUniqueId);
            userVideosRef.setValue(true).addOnSuccessListener(aVoid2 -> {
                // Both saves completed successfully
                System.out.println("Video saved successfully in both locations: " + videoUniqueId);
            }).addOnFailureListener(e -> {
                System.err.println("Failed to save video association to user: " + e.getMessage());
            });
        }).addOnFailureListener(e -> {
            System.err.println("Failed to save video metadata: " + e.getMessage());
        });
    }
} 