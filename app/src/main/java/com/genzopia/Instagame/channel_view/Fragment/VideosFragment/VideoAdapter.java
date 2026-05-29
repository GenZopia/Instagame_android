package com.genzopia.Instagame.channel_view.Fragment.VideosFragment;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.genzopia.Instagame.R;

import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import android.content.Intent;
import com.genzopia.Instagame.MainActivity;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    private Context context;
    private List<VideoItem_channel> videoList;
    private OkHttpClient httpClient;
    private ExecutorService executorService;
    private Handler mainHandler;

    public VideoAdapter(Context context, List<VideoItem_channel> videoList) {
        this.context = context;
        this.videoList = videoList;
        this.httpClient = new OkHttpClient();
        this.executorService = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView viewCount;
        TextView videoTitle;
        TextView verificationBadge;

        public VideoViewHolder(View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumb);
            viewCount = itemView.findViewById(R.id.viewCount);
            videoTitle = itemView.findViewById(R.id.videoTitle);
            verificationBadge = itemView.findViewById(R.id.verificationBadge);
        }
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video_channel, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem_channel video = videoList.get(position);
        
        // Set video title
        holder.videoTitle.setText(video.getVideoTitle());
        
        // Set view count
        holder.viewCount.setText(video.getViews());
        
        // Handle verification status display
        if (video.isOwnChannel()) {
            // Show verification status for own channel
            if (video.getIsVerified() != null && video.getIsVerified()) {
                // Verified video - Professional green styling
                holder.verificationBadge.setVisibility(View.VISIBLE);
                holder.verificationBadge.setText("✓ VERIFIED");
                holder.verificationBadge.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
                holder.verificationBadge.setBackground(context.getResources().getDrawable(R.drawable.verified_badge_background));
            } else {
                // Unverified video - Professional orange styling
                holder.verificationBadge.setVisibility(View.VISIBLE);
                holder.verificationBadge.setText("⏳ PENDING");
                holder.verificationBadge.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
                holder.verificationBadge.setBackground(context.getResources().getDrawable(R.drawable.pending_badge_background));
            }
        } else {
            // Hide verification badge for other users' channels (only verified videos are shown)
            holder.verificationBadge.setVisibility(View.GONE);
        }
        
        // Load thumbnail from video URL
        loadVideoThumbnail(video.getVideoId(), holder);
        
        // Set click listener to open video detail activity or play in reel view
        holder.itemView.setOnClickListener(v -> {
            // Check if user owns this video
            if (video.isOwnChannel()) {
                // User owns the video - open detail activity for editing
                com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                    .openVideoForEditing(context, video.getVideoId());
            } else {
                // User doesn't own the video - play in reel view
                com.genzopia.Instagame.utils.VideoNavigationManager.getInstance()
                    .playVideoInReelView(context, video.getVideoId());
            }
        });
    }

    private void loadVideoThumbnail(String videoId, VideoViewHolder holder) {
        if (videoId == null || videoId.isEmpty()) {
            Log.e("VideoAdapter", "Video ID is null or empty");
            return;
        }

        // Build direct R2 URL — no signing needed
        String basePath = resolveVideoBasePath(videoId);
        String videoUrl = "https://pub-0caba249d019456b9181ce1575ef825e.r2.dev/" + basePath + ".mp4";
        Log.d("VideoAdapter", "Loading thumbnail from: " + videoUrl);
        generateThumbnailFromVideo(videoUrl, holder);
    }

    private String resolveVideoBasePath(String videoId) {
        String id = videoId;
        if (id.startsWith("video/")) return id.replaceAll("\\.[^.]+$", "");
        id = id.replace(".mp4", "").replace(".m3u8", "");
        return id.startsWith("video_") ? "video/" + id : "video/video_" + id;
    }

    private void generateThumbnailFromVideo(String videoUrl, VideoViewHolder holder) {
        // Use Glide's built-in video thumbnail feature - much faster than ExoPlayer
        holder.itemView.post(() -> {
            Glide.with(context)
                    .load(videoUrl)
                    .frame(1000000) // 1 second in microseconds
                    .placeholder(R.drawable.demo_user) // Show placeholder while loading
                    .error(R.drawable.demo_user) // Show error image if failed
                    .centerCrop() // Crop to fit the thumbnail
                    .into(holder.thumbnail);
        });
    }

    @Override
    public int getItemCount() {
        return videoList.size();
    }

    public void releaseResources() {
        if (executorService != null) {
            executorService.shutdown();
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }
    
    // Remove the unused method since we're using VideoNavigationManager directly
}

