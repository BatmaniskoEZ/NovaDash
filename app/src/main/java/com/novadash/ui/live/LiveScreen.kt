package com.novadash.ui.live

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Live preview via libVLC (the camera's RTSP server is non-compliant enough that ExoPlayer
 * returns "PLAY 404"; VLC's tolerant engine plays it). Record/photo/lens controls go through
 * the HTTP API and are independent of the player.
 */
@Composable
fun LiveScreen(viewModel: LiveViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val lens by viewModel.lens.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Force RTSP-over-TCP and keep buffering low for a live feed.
    val libVlc = remember {
        LibVLC(context, arrayListOf("--rtsp-tcp", "--network-caching=200", "--no-audio"))
    }
    val player = remember { MediaPlayer(libVlc) }
    // Create the video surface once and keep it; the factory just returns it. Attaching in an
    // effect (not the factory) ensures the view is in the hierarchy first.
    val videoLayout = remember { VLCVideoLayout(context) }

    DisposableEffect(Unit) {
        player.attachViews(videoLayout, null, false, false)
        onDispose {
            player.stop()
            player.detachViews()
            player.release()
            libVlc.release()
        }
    }

    val scope = rememberCoroutineScope()

    // (Re)start the stream when the selected lens/URL changes. The short delay lets the
    // SurfaceView's surface get created before VLC builds its video output — without it,
    // re-entering the screen fails with "video output creation failed".
    LaunchedEffect(lens) {
        kotlinx.coroutines.delay(250)
        val media = Media(libVlc, Uri.parse(viewModel.streamUrl)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=200")
            addOption(":rtsp-tcp")
        }
        player.media = media
        media.release()
        player.play()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { videoLayout },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .align(Alignment.Center),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::switchLens) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch camera")
            }

            FilledIconButton(
                onClick = viewModel::toggleRecording,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (recording) Color.Red else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = if (recording) "Stop recording" else "Start recording",
                )
            }
            // Snapshot (cmd 2017) is designed to run during recording + preview, so no stream
            // juggling. Always tappable so tapping while stopped shows the "start recording
            // first" hint.
            IconButton(onClick = { scope.launch { viewModel.capturePhoto() } }) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Take photo")
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
