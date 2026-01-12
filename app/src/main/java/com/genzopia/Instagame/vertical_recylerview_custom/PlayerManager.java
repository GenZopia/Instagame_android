// PlayerManager.java
package com.genzopia.Instagame.vertical_recylerview_custom;

import android.content.Context;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.common.PlaybackException;
import java.util.HashMap;
import java.util.Map;

public class PlayerManager {
    private static PlayerManager instance;
    private final Map<String, ExoPlayer> playerMap = new HashMap<>();

    public static synchronized PlayerManager getInstance() {
        if (instance == null) {
            instance = new PlayerManager();
        }
        return instance;
    }

    public ExoPlayer getPlayer(Context context, String videoId, String videoUrl) {
        if (playerMap.containsKey(videoId)) {
            return playerMap.get(videoId);
        }

        ExoPlayer player = new ExoPlayer.Builder(context).build();
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        
        // Use progressive media source (works for both MP4 and HLS)
        player.setMediaItem(mediaItem);
        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);

        // Add error listener
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                // Log error but don't crash
                android.util.Log.e("PlayerManager", "Player error: " + error.getMessage());
            }
        });

        playerMap.put(videoId, player);
        return player;
    }

    public void playVideo(String videoId) {
        ExoPlayer player = playerMap.get(videoId);
        if (player != null) {
            // Reset to beginning if near end
            if (player.getDuration() > 0 &&
                    player.getCurrentPosition() >= player.getDuration() - 1000) {
                player.seekTo(0);
            }
            player.setPlayWhenReady(true);
        }
    }

    public void pauseVideo(String videoId) {
        ExoPlayer player = playerMap.get(videoId);
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public void preloadAndPause(Context context, String videoId, String videoUrl) {
        ExoPlayer player = getPlayer(context, videoId, videoUrl);
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public void releasePlayer(String videoId) {
        ExoPlayer player = playerMap.get(videoId);
        if (player != null) {
            player.release();
            playerMap.remove(videoId);
        }
    }

    public void releaseAll() {
        for (ExoPlayer player : playerMap.values()) {
            player.release();
        }
        playerMap.clear();
    }


}