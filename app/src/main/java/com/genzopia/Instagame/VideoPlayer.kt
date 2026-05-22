package com.genzopia.Instagame

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    isPlaying: Boolean,
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    onPlayerReady: () -> Unit = {},
    onPlayerError: (Exception) -> Unit = {}
) {
    // Note: play/pause and volume are controlled by ReelItem's LaunchedEffect.
    // VideoPlayer only owns the surface (AndroidView) and event callbacks.

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onPlayerReady()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onPlayerReady()
            }
            override fun onPlayerError(error: PlaybackException) {
                onPlayerError(error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Attach the player immediately in the factory.
                    this.player = player
                }
            },
            update = { view ->
                // Re-attach if the player instance changed (e.g. after error recovery).
                // This is the critical fix: without this, a replaced player has no
                // surface and renders nothing — causing the frozen-frame / black-screen bug.
                if (view.player !== player) {
                    view.player = player
                }
            },
            onRelease = { view ->
                // Detach cleanly so the player doesn't hold a dead surface reference.
                view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
