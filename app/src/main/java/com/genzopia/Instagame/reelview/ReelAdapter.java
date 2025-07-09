package com.genzopia.Instagame.reelview;

import android.content.Context;
import android.content.Intent;
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
import com.genzopia.Instagame.webgl_gameloading.Game_mode;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReelAdapter extends RecyclerView.Adapter<ReelAdapter.ReelViewHolder> {

    private Context context;
    private List<ReelItem> reelItems;
    private RecyclerView recyclerView;
    private Map<String, SimpleExoPlayer> playerMap = new HashMap<>();
    private String currentlyPlayingVideoId = null;

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
        holder.pausePlayer();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ReelViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.resumePlayer();
    }

    public void pausePlayers() {
        for (SimpleExoPlayer player : playerMap.values()) {
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        }
    }

    public void resumePlayers() {
        for (SimpleExoPlayer player : playerMap.values()) {
            if (player != null) {
                player.setPlayWhenReady(true);
            }
        }
    }

    public void releaseAllPlayers() {
        for (SimpleExoPlayer player : playerMap.values()) {
            if (player != null) {
                player.release();
            }
        }
        playerMap.clear();
        currentlyPlayingVideoId = null;
    }

    private SimpleExoPlayer getOrCreatePlayer(String videoId, String videoUrl) {
        if (playerMap.containsKey(videoId)) {
            return playerMap.get(videoId);
        }

        SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
        player.setMediaItem(MediaItem.fromUri(videoUrl));
        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);

        playerMap.put(videoId, player);
        return player;
    }

    class ReelViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView tvTitle, tvLikes;
        SimpleExoPlayer player;
        GestureDetector gestureDetector;
        String currentVideoId;

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

            currentVideoId = reelItem.getVideoId();
            
            // Release previous player if different video
            if (player != null && player.getMediaItemCount() > 0) {
                String currentMediaId = player.getMediaItemAt(0).mediaId;
                if (!currentVideoId.equals(currentMediaId)) {
                    releasePlayer();
                }
            }

            // Get or create player
            player = getOrCreatePlayer(currentVideoId, reelItem.getVideoId());
            
            playerView.setUseController(false);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
            playerView.setPlayer(player);

            itemView.setTag(R.id.gameid_tag, reelItem.getGameId());
            itemView.setTag(R.id.developerid_tag, reelItem.getDeveloperId());
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
                if (currentVideoId != null) {
                    playerMap.remove(currentVideoId);
                }
                player = null;
            }
            playerView.setPlayer(null);
        }

        class DoubleTapListener extends GestureDetector.SimpleOnGestureListener {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                String gameid = (String) itemView.getTag(R.id.gameid_tag);
                String developerId = (String) itemView.getTag(R.id.developerid_tag);
                
                // Pause current player before launching activity
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                
                // Launch Game_mode activity with intent extras
                Intent intent = new Intent(context, Game_mode.class);
                intent.putExtra("developer_id", developerId);
                intent.putExtra("game_id", gameid);
                context.startActivity(intent);
                
                return true;
            }
        }
    }
}

