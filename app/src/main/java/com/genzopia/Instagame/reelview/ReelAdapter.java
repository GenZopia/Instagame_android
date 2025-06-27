package com.genzopia.Instagame.reelview;

import android.content.Context;
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
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.List;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.ReelViewHolder> {

    private Context context;
    private List<ReelItem> reelItems;
    private RecyclerView recyclerView;

    public ReelAdapter(Context context, List<ReelItem> reelItems, RecyclerView recyclerView) {
        this.context = context;
        this.reelItems = reelItems;
        this.recyclerView = recyclerView;
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

    @Override
    public void onViewRecycled(@NonNull ReelViewHolder holder) {
        super.onViewRecycled(holder);
        holder.releasePlayer();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ReelViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        holder.releasePlayer();
    }

    public void pausePlayers() {
        for (int i = 0; i < getItemCount(); i++) {
            ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.pausePlayer();
            }
        }
    }

    public void resumePlayers() {
        for (int i = 0; i < getItemCount(); i++) {
            ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.resumePlayer();
            }
        }
    }

    public void releaseAllPlayers() {
        for (int i = 0; i < getItemCount(); i++) {
            ReelViewHolder holder = (ReelViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                holder.releasePlayer();
            }
        }
    }

    class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView tvTitle, tvLikes;
        SimpleExoPlayer player;
        GestureDetector gestureDetector;

        public ReelViewHolder(@NonNull View itemView) {
            super(itemView);
            playerView = itemView.findViewById(R.id.player_view);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvLikes = itemView.findViewById(R.id.tv_likes);

            playerView.setUseController(false);
            gestureDetector = new GestureDetector(context, new DoubleTapListener());

            itemView.setOnTouchListener((v, event) -> {
                gestureDetector.onTouchEvent(event);
                return true;
            });
        }

        void bind(ReelItem reelItem) {
            tvTitle.setText(reelItem.getTitle());
            tvLikes.setText(reelItem.getLikeCount() + " likes");

            releasePlayer();
            player = new SimpleExoPlayer.Builder(context).build();

            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            playerView.setUseController(false);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
            playerView.setPlayer(player);

            MediaItem mediaItem = MediaItem.fromUri(reelItem.getVideoUrl());
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);

            itemView.setTag(R.id.secret_tag, reelItem.getSecret());
        }

        void pausePlayer() {
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        }

        void resumePlayer() {
            if (player != null) {
                player.setPlayWhenReady(true);
            }
        }

        void releasePlayer() {
            if (player != null) {
                player.release();
                player = null;
            }
            playerView.setPlayer(null); // ✅ Important
        }

        class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                String secret = (String) itemView.getTag(R.id.secret_tag);
                Toast.makeText(context, secret, Toast.LENGTH_SHORT).show();
                return true;
            }
        }
    }
}
