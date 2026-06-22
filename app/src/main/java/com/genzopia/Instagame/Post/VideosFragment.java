package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VideosFragment extends Fragment {

    private View layoutPick, layoutSelected;
    private ImageView ivThumb;
    private TextView tvName, tvDuration;
    private Uri selectedUri;

    private final ActivityResultLauncher<String> pickVideo =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) showSelected(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_videos_upload, container, false);

        layoutPick     = root.findViewById(R.id.layout_pick_video);
        layoutSelected = root.findViewById(R.id.layout_video_selected);
        ivThumb        = root.findViewById(R.id.iv_video_thumb);
        tvName         = root.findViewById(R.id.tv_video_name);
        tvDuration     = root.findViewById(R.id.tv_video_duration);

        root.findViewById(R.id.tap_area).setOnClickListener(v -> pickVideo.launch("video/*"));
        root.findViewById(R.id.btn_pick_video).setOnClickListener(v -> pickVideo.launch("video/*"));
        root.findViewById(R.id.btn_change_video).setOnClickListener(v -> pickVideo.launch("video/*"));
        root.findViewById(R.id.btn_next).setOnClickListener(v -> openPreview());

        return root;
    }

    private void showSelected(Uri uri) {
        selectedUri = uri;

        // Thumbnail via Glide
        Glide.with(this).load(uri).centerCrop().into(ivThumb);

        // File name
        tvName.setText(queryFileName(uri));

        // Duration
        tvDuration.setText(getDuration(uri));

        layoutPick.setVisibility(View.GONE);
        layoutSelected.setVisibility(View.VISIBLE);
    }

    private void openPreview() {
        if (selectedUri == null) return;
        Intent i = new Intent(requireContext(), VideoPreviewActivity.class);
        i.putExtra("video_uri", selectedUri.toString());
        startActivity(i);
    }

    private String queryFileName(Uri uri) {
        try (Cursor c = requireContext().getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private String getDuration(Uri uri) {
        try {
            MediaMetadataRetriever r = new MediaMetadataRetriever();
            r.setDataSource(requireContext(), uri);
            String ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            r.release();
            if (ms != null) {
                long millis = Long.parseLong(ms);
                long min = TimeUnit.MILLISECONDS.toMinutes(millis);
                long sec = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
                return String.format(Locale.US, "%d:%02d", min, sec);
            }
        } catch (Exception ignored) {}
        return "";
    }
}
