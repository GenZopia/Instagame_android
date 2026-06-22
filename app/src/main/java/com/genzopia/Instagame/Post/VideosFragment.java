package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.genzopia.Instagame.R;

public class VideosFragment extends Fragment {

    private final ActivityResultLauncher<String> pickVideo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    Intent i = new Intent(requireContext(), VideoPreviewActivity.class);
                    i.putExtra("video_uri", uri.toString());
                    startActivity(i);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_videos_upload, container, false);
        root.findViewById(R.id.tap_area).setOnClickListener(v -> pickVideo.launch("video/*"));
        return root;
    }
}
