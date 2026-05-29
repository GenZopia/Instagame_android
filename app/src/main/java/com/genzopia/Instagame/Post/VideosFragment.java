package com.genzopia.Instagame.Post;

import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideosAdapter_gallery adapter;
    private final List<Uri> videoUris = new ArrayList<>();
    private Uri selectedVideoUri = null;
    private Button btnRequestStoragePermission;
    private static final int REQUEST_VIDEO_PERMISSION = 1001;
    private ViewGroup rootContainer;
    private View permissionPromptView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_videos_upload, container, false);
        rootContainer = (ViewGroup) root;
        recyclerView = root.findViewById(R.id.rv_videos);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        btnRequestStoragePermission = root.findViewById(R.id.btn_request_storage_permission);
        adapter = new VideosAdapter_gallery(requireContext(), videoUris, uri -> {
            Intent i = new Intent(requireContext(), VideoPreviewActivity.class);
            i.putExtra("video_uri", uri.toString());
            startActivity(i);
        });
        recyclerView.setAdapter(adapter);

        // X close button — closes the Post activity and goes back
        ImageView btnClose = root.findViewById(R.id.btn_close_picker);
        btnClose.setOnClickListener(v -> {
            requireActivity().finish();
            requireActivity().overridePendingTransition(0, 0);
        });

        updatePermissionUI();
        return root;
    }

    private void updatePermissionUI() {
        if (hasVideoPermission()) {
            showVideoList();
            loadAllVideos();
        } else {
            showPermissionPrompt();
        }
    }

    private boolean hasVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestVideoPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO}, REQUEST_VIDEO_PERMISSION);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_VIDEO_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_VIDEO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                updatePermissionUI();
            } else {
                Toast.makeText(requireContext(), getString(R.string.permission_storage_rationale), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadAllVideos() {
        Executors.newSingleThreadExecutor().execute(() -> {
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DATE_ADDED
            };
            String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = requireContext().getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection, null, null, sortOrder
            )) {
                if (cursor == null) return;
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);

                List<Uri> tempList = new ArrayList<>();
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    Uri contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                    tempList.add(contentUri);
                }

                requireActivity().runOnUiThread(() -> {
                    videoUris.clear();
                    videoUris.addAll(tempList);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void uploadToCloudflare(Uri uri) {
        new Thread(() -> {
            String videoId = UUID.randomUUID().toString(); // or use timestamp
            File file = FileUtils.getFileFromUri(requireContext(), uri);
            if (file == null) {
                requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "File conversion failed", Toast.LENGTH_SHORT).show());
                return;
            }
            FileUploader.uploadFileToWorker(file, "video", Map.of("video_id", videoId), (success, response) -> {
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(requireContext(), "Upload successful!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Upload failed: " + response, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }).start();
    }

    private void showVideoList() {
        recyclerView.setVisibility(View.VISIBLE);
        if (permissionPromptView != null) {
            rootContainer.removeView(permissionPromptView);
            permissionPromptView = null;
        }
    }

    private void showPermissionPrompt() {
        recyclerView.setVisibility(View.GONE);
        if (permissionPromptView == null) {
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            permissionPromptView = inflater.inflate(R.layout.permission_prompt, rootContainer, false);
            ImageView icon = permissionPromptView.findViewById(R.id.iv_permission_icon);
            TextView title = permissionPromptView.findViewById(R.id.tv_permission_title);
            TextView desc = permissionPromptView.findViewById(R.id.tv_permission_desc);
            Button btnSettings = permissionPromptView.findViewById(R.id.btn_open_settings);
            icon.setImageResource(R.drawable.ic_permission_storage);
            title.setText(getString(R.string.permission_storage_title, getString(R.string.app_name)));
            desc.setText(getString(R.string.permission_storage_desc));
            btnSettings.setOnClickListener(v -> requestVideoPermission());
            rootContainer.addView(permissionPromptView);
        }
    }
}

