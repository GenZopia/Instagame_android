package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.AutoCompleteTextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.genzopia.Instagame.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import de.hdodenhof.circleimageview.CircleImageView;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;
import android.widget.ArrayAdapter;

public class VideoUploadInfoActivity extends AppCompatActivity {
    private VideoView videoView;
    private TextInputEditText inputTitle;
    private TextInputEditText inputDescription;
    private ChipGroup gameTagChipGroup;
    private MaterialButton btnConfirmUpload;
    private Uri videoUri;
    // User info views
    private CircleImageView userAvatar;
    private TextView userName;
    private TextView userId;
    private ImageView editbutton;
    private AutoCompleteTextView gameDropdown;
    private TextView gameTagText;
    private ArrayAdapter<String> gameAdapter;
    private List<String> gameNames = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_upload_info);

        videoView = findViewById(R.id.video_view);
        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        gameTagChipGroup = findViewById(R.id.gameTagChipGroup);
        btnConfirmUpload = findViewById(R.id.btnConfirmUpload);
        userAvatar = findViewById(R.id.userAvatar);
        userName = findViewById(R.id.userName);
        userId = findViewById(R.id.userId);
        editbutton=findViewById(R.id.editIcon);
        gameDropdown = findViewById(R.id.gameDropdown);
        gameTagText = findViewById(R.id.gameTagText);

        editbutton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                VideoUploadInfoActivity.super.onBackPressed();
            }
        });



        // Get video URI from intent
        String uriString = getIntent().getStringExtra("video_uri");
        if (uriString == null) {
            finish();
            return;
        }
        videoUri = Uri.parse(uriString);

        // Setup video preview
        videoView.setVideoURI(videoUri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f); // Mute the video
            videoView.start();
        });

        // Setup game tag chips
        String[] gameTags = {"#Action", "#Adventure", "#Puzzle", "#Strategy", "#Sports","#Others"};
        for (String tag : gameTags) {
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setTextAppearanceResource(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            gameTagChipGroup.addView(chip);
        }
        gameTagChipGroup.setSingleSelection(true);

        // Example user info (replace with real user data)
        userName.setText("INDIAN CHESS");
        userId.setText("@bhartiyachess");
        userAvatar.setImageResource(R.drawable.ic_launcher_foreground); // Replace with real avatar

        gameAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, gameNames);
        gameDropdown.setAdapter(gameAdapter);

        // Fetch game names from Firebase
        DatabaseReference gamesRef = FirebaseDatabase.getInstance().getReference("games");
        gamesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                gameNames.clear();
                for (DataSnapshot gameSnap : snapshot.getChildren()) {
                    String name = gameSnap.child("game_name").getValue(String.class);
                    if (name != null) gameNames.add(name);
                }
                gameAdapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        gameDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selected = gameAdapter.getItem(position);
            gameTagText.setText("@" + selected);
        });


        btnConfirmUpload.setOnClickListener(v -> {
            String title = inputTitle.getText() != null ? inputTitle.getText().toString().trim() : "";
            String description = inputDescription.getText() != null ? inputDescription.getText().toString().trim() : "";
            int checkedChipId = gameTagChipGroup.getCheckedChipId();
            String gameTag = null;
            if (checkedChipId != -1) {
                Chip selectedChip = gameTagChipGroup.findViewById(checkedChipId);
                gameTag = selectedChip.getText().toString();
            }
            if (title.isEmpty()) {
                inputTitle.setError("Title required");
                return;
            }
            if (gameTag == null) {
                Toast.makeText(this, "Please select a game tag", Toast.LENGTH_SHORT).show();
                return;
            }
            doUploadWithInfo(title, description, gameTag);
        });
    }

    private void doUploadWithInfo(String title, String description, String gameTag) {
        File file = FileUtils.getFileFromUri(this, videoUri);
        if (file == null) {
            Toast.makeText(this, "Unable to read file", Toast.LENGTH_SHORT).show();
            return;
        }
        btnConfirmUpload.setEnabled(false);
        btnConfirmUpload.setText("Uploading...");
        String videoId = UUID.randomUUID().toString();
        FileUploader.uploadFileToWorker(
                file,
                "video",
                Map.of(
                        "video_id", videoId,
                        "title", title,
                        "description", description,
                        "game_tag", gameTag
                ),
                (success, response) -> runOnUiThread(() -> {
                    btnConfirmUpload.setEnabled(true);
                    btnConfirmUpload.setText("Upload");
                    if (success) {
                        Toast.makeText(this, "Upload succeeded!", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Upload failed: " + response, Toast.LENGTH_LONG).show();
                    }
                })
        );
    }
} 