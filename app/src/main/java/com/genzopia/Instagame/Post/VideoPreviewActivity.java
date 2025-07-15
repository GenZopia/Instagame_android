package com.genzopia.Instagame.Post;

import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.R;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class VideoPreviewActivity extends AppCompatActivity {

    private VideoView videoView;
    private MaterialButton uploadBtn;
    private MaterialButton saveToGalleryBtn;
    private ImageButton playPauseButton;
    private ImageButton closeButton;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    
    private Uri videoUri;
    private boolean isRecordedVideo = false;
    private boolean isPlaying = true;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_video_preview);

        // Initialize views
        videoView = findViewById(R.id.video_view);
        uploadBtn = findViewById(R.id.btn_upload);
        saveToGalleryBtn = findViewById(R.id.btn_save_to_gallery);
        playPauseButton = findViewById(R.id.playPauseButton);
        closeButton = findViewById(R.id.closeButton);
        seekBar = findViewById(R.id.seekBar);
        currentTimeText = findViewById(R.id.currentTime);
        totalTimeText = findViewById(R.id.totalTime);

        // Get video URI from intent
        String uriString = getIntent().getStringExtra("video_uri");
        if (uriString == null) {
            finish();
            return;
        }
        videoUri = Uri.parse(uriString);
        
        // Check if this is a recorded video
        isRecordedVideo = getIntent().getBooleanExtra("is_recorded_video", false);
        if (isRecordedVideo) {
            saveToGalleryBtn.setVisibility(android.view.View.VISIBLE);
        }

        setupVideoPlayer();
        setupControls();
    }

    private void setupVideoPlayer() {
        videoView.setVideoURI(videoUri);
        
        videoView.setOnPreparedListener(mp -> {
            // Set up seek bar
            seekBar.setMax(mp.getDuration());
            totalTimeText.setText(formatTime(mp.getDuration()));
            
            // Start playing
            mp.setLooping(true);
            videoView.start();
            isPlaying = true;
            updatePlayPauseButton();
            
            // Start seek bar updates
            startSeekBarUpdates();
        });

        videoView.setOnCompletionListener(mp -> {
            isPlaying = false;
            updatePlayPauseButton();
        });
    }

    private void setupControls() {
        // Play/Pause button
        playPauseButton.setOnClickListener(v -> {
            if (isPlaying) {
                videoView.pause();
                isPlaying = false;
            } else {
                videoView.start();
                isPlaying = true;
            }
            updatePlayPauseButton();
        });

        // Close button
        closeButton.setOnClickListener(v -> finish());

        // Seek bar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    videoView.seekTo(progress);
                    currentTimeText.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Upload button
        uploadBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, VideoUploadInfoActivity.class);
            intent.putExtra("video_uri", videoUri.toString());
            startActivity(intent);
            finish();
        });

        // Save to Gallery button
        saveToGalleryBtn.setOnClickListener(v -> saveToGallery());
    }

    private void updatePlayPauseButton() {
        if (isPlaying) {
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
        } else {
            playPauseButton.setImageResource(android.R.drawable.ic_media_play);
        }
    }

    private void startSeekBarUpdates() {
        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    int currentPosition = videoView.getCurrentPosition();
                    seekBar.setProgress(currentPosition);
                    currentTimeText.setText(formatTime(currentPosition));
                }
                handler.postDelayed(this, 100);
            }
        };
        handler.post(updateSeekBar);
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    private void saveToGallery() {
        new Thread(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, "InstaGame_" + System.currentTimeMillis() + ".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/InstaGame");

                Uri contentUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (contentUri != null) {
                    try (java.io.InputStream inputStream = getContentResolver().openInputStream(videoUri);
                         java.io.OutputStream outputStream = getContentResolver().openOutputStream(contentUri)) {
                        if (inputStream != null && outputStream != null) {
                            byte[] buffer = new byte[8192];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                            runOnUiThread(() -> Toast.makeText(this, "Video saved to gallery", Toast.LENGTH_SHORT).show());
                        }
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Failed to save video: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void doUpload() {
        new Thread(() -> {
            File file = FileUtils.getFileFromUri(this, videoUri);
            if (file == null) {
                runOnUiThread(() -> Toast.makeText(this, "Unable to read file", Toast.LENGTH_SHORT).show());
                return;
            }
            runOnUiThread(() -> {
                uploadBtn.setEnabled(false);
                uploadBtn.setText("Uploading...");
            });
            String videoId = UUID.randomUUID().toString();
            FileUploader.uploadFileToWorker(
                    file,
                    "video",
                    Map.of("video_id", videoId),
                    (success, response) -> runOnUiThread(() -> {
                        uploadBtn.setEnabled(true);
                        uploadBtn.setText("Upload");
                        if (success) {
                            Toast.makeText(this, "Upload succeeded!", Toast.LENGTH_LONG).show();
                            finish();
                        } else {
                            Toast.makeText(this, "Upload failed: " + response, Toast.LENGTH_LONG).show();
                        }
                    })
            );
        }).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
            isPlaying = false;
            updatePlayPauseButton();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateSeekBar != null) {
            handler.removeCallbacks(updateSeekBar);
        }
    }
}
