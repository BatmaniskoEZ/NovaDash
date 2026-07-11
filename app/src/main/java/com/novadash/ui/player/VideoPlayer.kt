package com.novadash.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Playback surface (with transport controls) for a local or remote MP4. A single ExoPlayer is
 * kept for the composable's lifetime and its media item is swapped when [uri] changes — this
 * is what makes the front/rear toggle work (recreating the player would leave the PlayerView
 * bound to the old instance, yielding audio-only playback).
 */
@Composable
fun VideoPlayer(uri: String, modifier: Modifier = Modifier, keepPosition: Boolean = false) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }

    LaunchedEffect(uri) {
        // When [keepPosition] (front<->rear toggle of the same recording), resume at the same
        // moment; otherwise (a different clip) start from the beginning.
        val resumeAt = if (keepPosition) player.currentPosition else 0L
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        update = { it.player = player },
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * Renders an externally-owned ExoPlayer (so the caller can read position, seek, etc.).
 * [onControlsVisible] reports when the transport controls are shown, so overlays can move
 * out of their way. [momentMarkersMs] are drawn as bookmark ticks on the seekbar (via the
 * time bar's extra-ad-marker mechanism — the standard way to mark positions on DefaultTimeBar).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun ExoSurface(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    momentMarkersMs: LongArray = LongArray(0),
    onControlsVisible: (Boolean) -> Unit = {},
) {
    AndroidView(
        factory = { ctx ->
            // Inflated (not constructed) so the marker attrs in exo_surface.xml apply.
            val view = android.view.LayoutInflater.from(ctx)
                .inflate(com.novadash.R.layout.exo_surface, null) as PlayerView
            view.apply {
                this.player = player
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        onControlsVisible(visibility == android.view.View.VISIBLE)
                    },
                )
            }
        },
        update = {
            it.player = player
            it.setExtraAdGroupMarkers(momentMarkersMs, BooleanArray(momentMarkersMs.size))
        },
        modifier = modifier.fillMaxSize(),
    )
}
