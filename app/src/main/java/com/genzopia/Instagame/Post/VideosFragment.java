package com.genzopia.Instagame.Post;

import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class VideosFragment extends Fragment {

    private RecyclerView recyclerView;
    private VideosAdapter_gallery adapter;
    private final List<Uri> videoUris = new ArrayList<>();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.fragment_videos_upload, container, false);

        recyclerView = root.findViewById(R.id.rv_videos);
        // 3 columns grid
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        adapter = new VideosAdapter_gallery(requireContext(), videoUris, uri -> {
            // handle video click — e.g. play or return to parent
            // Intent i = new Intent(Intent.ACTION_VIEW, uri);
            // i.setDataAndType(uri, "video/*");
            // startActivity(i);
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
                // update UI on main thread
                requireActivity().runOnUiThread(() -> {
                    videoUris.clear();
                    videoUris.addAll(tempList);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }
}
