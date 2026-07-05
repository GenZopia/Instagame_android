package com.genzopia.Instagame.Post;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.genzopia.Instagame.R;
import com.genzopia.Instagame.VideoHlsConverter;
import com.google.firebase.auth.FirebaseAuth;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService hlsExecutor = Executors.newSingleThreadExecutor();
    private Future<?> uploadFuture;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_REDELIVER_INTENT; }
        if (ACTION_UPLOAD.equals(intent.getAction())) {
            String title = intent.getStringExtra(EXTRA_TITLE);
            String description = intent.getStringExtra(EXTRA_DESCRIPTION);
            String gameId = intent.getStringExtra(EXTRA_GAME_ID);
            String videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI);
            String fileExtension = intent.getStringExtra(EXTRA_FILE_EXTENSION);
            String devid = intent.getStringExtra(DEVID);
            showNotification(0, false); // must call startForeground immediately
            uploadVideo(title, description, gameId, videoUriString, fileExtension, devid);
        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            isCancelled = true;
            if (uploadFuture != null) uploadFuture.cancel(true);
            stopForeground(true);
            stopSelf();
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void uploadVideo(String title, String description, String gameId, String videoUriString, String fileExtension, String devid) {
        uploadFuture = executor.submit(() -> {
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
                // Upload the video bytes DIRECTLY to the Cloudflare worker (like the
                // legacy app). The gateway can't proxy the file because Cloud Run
                // rejects request bodies over ~32 MiB with HTTP 413. The object is
                // stored under the video_id key, matching what HLS/playback expect.
                java.util.Map<String, String> workerParams = new java.util.HashMap<>();
                workerParams.put("video_id", videoUniqueId);
                workerParams.put("title", title);
                workerParams.put("description", description);
                workerParams.put("game_id", gameId);
                FileUploader.uploadVideoToWorkerDirect(renamedFile, workerParams, (uploadSuccess, uploadResponse) -> {
                    if (!uploadSuccess) {
                        android.util.Log.e("VideoUploadService", "Worker upload failed for " + videoUniqueId + ": " + uploadResponse);
                        renamedFile.delete();
                        showNotification(0, true);
                        sendResult("fail");
                        stopForeground(true);
                        stopSelf();
                        return;
                    }

                    // Bytes are stored — now register the metadata via the SECURED
                    // gateway (small JSON, well under the 32 MiB limit). The gateway
                    // performs the Firebase write that the locked-down rules forbid
                    // the client from doing directly.
                    java.util.Map<String, String> regMeta = new java.util.HashMap<>();
                    regMeta.put("video_id", videoUniqueId);
                    String workerKey = FileUploader.parseWorkerKey(uploadResponse);
                    if (workerKey != null) regMeta.put("key", workerKey);
                    regMeta.put("video_title", title);
                    regMeta.put("game_id", gameId);
                    FileUploader.registerVideoViaGateway(regMeta, (regSuccess, regResponse) -> {
                        if (regSuccess) {
                            hlsExecutor.submit(() -> {
                                new Handler(Looper.getMainLooper()).post(() ->
                                    Toast.makeText(this, "HLS conversion started for " + videoUniqueId, Toast.LENGTH_SHORT).show());
                                String manifestKey = VideoHlsConverter.triggerConversion(videoUniqueId);
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    String msg = manifestKey != null
                                        ? "HLS ready: " + manifestKey
                                        : "HLS conversion failed for " + videoUniqueId;
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                });
                            });
                            renamedFile.delete();
                            showNotification(100, true);
                            sendResult("success");
                        } else {
                            android.util.Log.e("VideoUploadService", "Register failed for " + videoUniqueId + ": " + regResponse);
                            renamedFile.delete();
                            showNotification(0, true);
                            sendResult("fail");
                        }
                        stopForeground(true);
                        stopSelf();
                    });
                }, progress -> {
                    showNotification(progress, false);
                    sendProgress(progress);
                });
            } catch (Exception e) {
                sendResult("fail");
                stopForeground(true);
                stopSelf();
            }
        });
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
} 