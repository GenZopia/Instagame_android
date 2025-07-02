package com.genzopia.Instagame.Post;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;

import java.util.List;

public class VideosAdapter_gallery extends RecyclerView.Adapter<VideosAdapter_gallery.VideoViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(Uri videoUri);
    }

    private final Context context;
    private final List<Uri> videos;
    private final OnVideoClickListener listener;

    public VideosAdapter_gallery(Context context, List<Uri> videos, OnVideoClickListener listener) {
        this.context = context;
        this.videos = videos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_video_thumbnail, parent, false);
        return new VideoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Uri uri = videos.get(position);
        // load thumbnail
        Glide.with(context)
                .load(uri)
                .centerCrop()
                .into(holder.thumb);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onVideoClick(uri);
        });
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumb, playOverlay;
        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.img_thumb);
            playOverlay = itemView.findViewById(R.id.img_play_overlay);
        }
    }
}

