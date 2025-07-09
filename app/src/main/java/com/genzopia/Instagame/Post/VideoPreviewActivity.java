package com.genzopia.Instagame.Post;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.genzopia.Instagame.R;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class VideoPreviewActivity extends AppCompatActivity {

    private VideoView videoView;
    private Button uploadBtn;
    private Uri videoUri;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_video_preview);

        videoView = findViewById(R.id.video_view);
        uploadBtn = findViewById(R.id.btn_upload);

        String uriString = getIntent().getStringExtra("video_uri");
        if (uriString == null) {
            finish();
            return;
        }
        videoUri = Uri.parse(uriString);

        // play
        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> mp.setLooping(true));
        videoView.start();

        uploadBtn.setOnClickListener(v -> doUpload());
    }

    private void doUpload() {
        // Convert Uri → File
        File file = FileUtils.getFileFromUri(this, videoUri);
        if (file == null) {
            Toast.makeText(this, "Unable to read file", Toast.LENGTH_SHORT).show();
            return;
        }

        // generate an ID for this video
        String videoId = UUID.randomUUID().toString();

        FileUploader.uploadFileToWorker(
                file,
                "video",
                Map.of("video_id", videoId),
                (success, response) -> runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(this, "Upload succeeded!", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Upload failed: " + response, Toast.LENGTH_LONG).show();
                    }
                })
        );
    }
}
