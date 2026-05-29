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
    private static final String TAG = "PlayerManager";

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

        try {
            ExoPlayer player = new ExoPlayer.Builder(context).build();
            MediaItem mediaItem = MediaItem.fromUri(videoUrl);

            // Use progressive media source (works for both MP4 and HLS)
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setRepeatMode(Player.REPEAT_MODE_ONE);

            // Add error listener with recovery
            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    android.util.Log.e(TAG, "Player error: " + error.getMessage() + " for video: " + videoId);
                    // Attempt recovery — release and remove from map so next call recreates
                    releasePlayer(videoId);
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_IDLE) {
                        android.util.Log.w(TAG, "Player went IDLE for video: " + videoId + " — releasing");
                        releasePlayer(videoId);
                    }
                }
            });

            playerMap.put(videoId, player);
            return player;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create player for video: " + videoId + " — " + e.getMessage());
            return null;
        }
    }

    public void playVideo(String videoId) {
        try {
            ExoPlayer player = playerMap.get(videoId);
            if (player != null) {
                // Reset to beginning if near end
                if (player.getDuration() > 0 &&
                        player.getCurrentPosition() >= player.getDuration() - 1000) {
                    player.seekTo(0);
                }
                player.setPlayWhenReady(true);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error playing video " + videoId + " — " + e.getMessage());
            releasePlayer(videoId);
        }
    }

    public void pauseVideo(String videoId) {
        try {
            ExoPlayer player = playerMap.get(videoId);
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error pausing video " + videoId + " — " + e.getMessage());
        }
    }

    public void preloadAndPause(Context context, String videoId, String videoUrl) {
        try {
            ExoPlayer player = getPlayer(context, videoId, videoUrl);
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error preloading video " + videoId + " — " + e.getMessage());
        }
    }

    public void releasePlayer(String videoId) {
        try {
            ExoPlayer player = playerMap.remove(videoId);
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception e) {
            android.util.Log.e(TAG, "Error releasing player for " + videoId + " — " + e.getMessage());
        }
    }

    public void releaseAll() {
        for (String videoId : playerMap.keySet().toArray(new String[0])) {
            releasePlayer(videoId);
        }
        playerMap.clear();
    }
}
