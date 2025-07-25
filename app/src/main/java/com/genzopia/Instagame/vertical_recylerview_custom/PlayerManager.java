// PlayerManager.java
package com.genzopia.Instagame.vertical_recylerview_custom;

import android.content.Context;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.PlaybackException;
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

        SimpleExoPlayer player = new SimpleExoPlayer.Builder(context).build();
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(context, "instagame-agent");
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);

        // Try HLS first
        MediaSource hlsSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
        player.setMediaSource(hlsSource);
        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ONE);

        // Add fallback to MP4 if HLS fails
        player.addListener(new Player.Listener() {
            boolean triedFallback = false;
            @Override
            public void onPlayerError(PlaybackException error) {
                if (!triedFallback) {
                    triedFallback = true;
                    MediaSource mp4Source = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
                    player.setMediaSource(mp4Source);
                    player.prepare();
                    player.setPlayWhenReady(true);
                }
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