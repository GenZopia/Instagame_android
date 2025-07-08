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

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideosAdapter_gallery adapter;
    private final List<Uri> videoUris = new ArrayList<>();
    private Uri selectedVideoUri = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_videos_upload, container, false);

        recyclerView = root.findViewById(R.id.rv_videos);
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        adapter = new VideosAdapter_gallery(requireContext(), videoUris, uri -> {
            // Launch preview activity on tap
            Intent i = new Intent(requireContext(), VideoPreviewActivity.class);
            i.putExtra("video_uri", uri.toString());
            startActivity(i);
        });

        recyclerView.setAdapter(adapter);
        loadAllVideos();
        return root;
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
        String videoId = UUID.randomUUID().toString(); // or use timestamp
        File file = FileUtils.getFileFromUri(requireContext(), uri);
        if (file == null) {
            Toast.makeText(requireContext(), "File conversion failed", Toast.LENGTH_SHORT).show();
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
    }
}

