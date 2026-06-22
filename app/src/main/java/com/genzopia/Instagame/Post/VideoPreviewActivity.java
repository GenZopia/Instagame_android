package com.genzopia.Instagame.Post;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.genzopia.Instagame.R;
import com.genzopia.Instagame.common.BaseActivity;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class VideoPreviewActivity extends BaseActivity {

    private ExoPlayer player;
    private SeekBar seekBar;
    private TextView tvCurrent, tvTotal;
    private ImageView icPlayPause;
    private View topBar, bottomBar, scrimTop, scrimBottom;
    private ImageButton btnMute;

    private boolean isMuted = false;
    private boolean controlsVisible = true;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable seekUpdater = new Runnable() {
        @Override public void run() {
            if (player != null) {
                long pos = player.getCurrentPosition();
                seekBar.setProgress((int) pos);
                tvCurrent.setText(fmt(pos));
            }
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable autoHide = () -> setControlsVisible(false);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                .hide(WindowInsetsCompat.Type.systemBars());

        setContentView(R.layout.activity_video_preview);

        String uriStr = getIntent().getStringExtra("video_uri");
        if (uriStr == null) { finish(); return; }

        seekBar     = findViewById(R.id.seek_bar);
        tvCurrent   = findViewById(R.id.tv_current);
        tvTotal     = findViewById(R.id.tv_total);
        icPlayPause = findViewById(R.id.ic_play_pause);
        topBar      = findViewById(R.id.top_bar);
        bottomBar   = findViewById(R.id.bottom_bar);
        scrimTop    = findViewById(R.id.scrim_top);
        scrimBottom = findViewById(R.id.scrim_bottom);
        btnMute     = findViewById(R.id.btn_mute);

        setupPlayer(Uri.parse(uriStr));

        // Tap overlay: if paused → resume; if playing + controls hidden → show controls;
        // if playing + controls visible → pause
        findViewById(R.id.tap_overlay).setOnClickListener(v -> {
            if (!player.isPlaying()) {
                player.play();
                pulseIcon(R.drawable.ic_play_circle);
                setControlsVisible(true);
                scheduleAutoHide();
            } else if (!controlsVisible) {
                setControlsVisible(true);
                scheduleAutoHide();
            } else {
                player.pause();
                pulseIcon(R.drawable.ic_play_circle);
                handler.removeCallbacks(autoHide);
                setControlsVisible(true);
            }
        });

        // Back
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        // Mute
        btnMute.setOnClickListener(v -> {
            isMuted = !isMuted;
            player.setVolume(isMuted ? 0f : 1f);
            btnMute.setImageResource(isMuted ? R.drawable.ic_volume_off : R.drawable.ic_volume_on);
        });

        // Seek
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean user) {
                if (user) { player.seekTo(p); tvCurrent.setText(fmt(p)); }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { handler.removeCallbacks(autoHide); }
            @Override public void onStopTrackingTouch(SeekBar sb) { scheduleAutoHide(); }
        });

        // Next
        ((MaterialButton) findViewById(R.id.btn_next)).setOnClickListener(v -> {
            Intent i = new Intent(this, VideoUploadInfoActivity.class);
            i.putExtra("video_uri", uriStr);
            startActivity(i);
            finish();
        });
    }

    private void setupPlayer(Uri uri) {
        PlayerView playerView = findViewById(R.id.player_view);
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(uri));
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.prepare();
        player.play();

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    long dur = player.getDuration();
                    seekBar.setMax((int) dur);
                    tvTotal.setText(fmt(dur));
                    handler.post(seekUpdater);
                    scheduleAutoHide();
                }
            }
        });
    }


    private void setControlsVisible(boolean show) {
        controlsVisible = show;
        float alpha = show ? 1f : 0f;
        topBar.animate().alpha(alpha).setDuration(200).start();
        bottomBar.animate().alpha(alpha).setDuration(200).start();
        scrimTop.animate().alpha(alpha).setDuration(200).start();
        scrimBottom.animate().alpha(alpha).setDuration(200).start();
    }

    private void pulseIcon(int resId) {
        icPlayPause.setImageResource(resId);
        icPlayPause.animate().cancel();
        icPlayPause.setAlpha(1f);
        icPlayPause.animate().alpha(0f).setStartDelay(600).setDuration(300).start();
    }

    private void scheduleAutoHide() {
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, 3000);
    }

    private String fmt(long ms) {
        long m = TimeUnit.MILLISECONDS.toMinutes(ms);
        long s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        return String.format(Locale.US, "%d:%02d", m, s);
    }

    @Override protected void onPause() {
        super.onPause();
        player.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        player.release();
    }
}
