package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.media.browse.MediaBrowser;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.genzopia.Instagame.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.List;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.ReelViewHolder> {

    private Context context;
    private List<ReelItem> reelItems;
    private GestureDetector gestureDetector;

    public ReelAdapter(Context context, List<ReelItem> reelItems) {
        this.context = context;
        this.reelItems = reelItems;
        this.gestureDetector = new GestureDetector(context, new DoubleTapListener());
    }

    @NonNull
    @Override
    public ReelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.reel_item, parent, false);
        return new ReelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReelViewHolder holder, int position) {
        ReelItem item = reelItems.get(position);
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return reelItems.size();
    }

    class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView tvTitle, tvLikes;
        SimpleExoPlayer player;

        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvLikes = itemView.findViewById(R.id.tv_likes);

            itemView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }

        void bind(ReelItem reelItem) {
            // Set text
            tvTitle.setText(reelItem.getTitle());
            tvLikes.setText(reelItem.getLikeCount() + " likes");

            // Initialize player
            releasePlayer();
            player = new SimpleExoPlayer.Builder(context).build();
            playerView.setPlayer(player);

            // Prepare media
            MediaItem mediaItem = MediaItem.fromUri(reelItem.getVideoUrl());
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);

            // Set double click listener
            itemView.setOnClickListener(v -> {
                // Store secret for double tap access
                v.setTag(R.id.secret_tag, reelItem.getSecret());
            });
        }

        void releasePlayer() {
            if (player != null) {
                player.release();
                player = null;
            }
        }
    }

    private class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            View view = ((RecyclerView) gestureDetector.getContext()).findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                String secret = (String) view.getTag(R.id.secret_tag);
                Toast.makeText(context, secret, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
    }
}