package com.novadash.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novadash.data.DownloadState
import com.novadash.data.MediaFile
import com.novadash.data.RecordingGroup
import com.novadash.data.Segment

/**
 * Full-screen, edge-to-edge playback. Swipe up/down to move to the previous/next clip and
 * left/right to toggle front/rear (when a rear exists). Plays the local copy if downloaded,
 * else streams from the camera. Rendered above the hub so it isn't pushed down by insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    groups: List<RecordingGroup>,
    startIndex: Int,
    onBack: () -> Unit,
    viewModel: PlaybackViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    BackHandler { onBack() }
    var index by remember { mutableStateOf(startIndex.coerceIn(0, (groups.size - 1).coerceAtLeast(0))) }
    val group = groups[index]
    // Reset to front and to a fresh position whenever the clip changes.
    var showRear by remember(group) { mutableStateOf(!group.hasFront) }
    // True only across a front<->rear toggle, so VideoPlayer keeps the position then (not on clip change).
    var keepPosition by remember { mutableStateOf(false) }

    val file = if (showRear) group.rear ?: group.primary else group.front ?: group.primary
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val downloadState = downloads[file.cameraPath]
    val playUri = (downloadState as? DownloadState.Done)?.uri ?: file.downloadUrl

    // Markers are per recording (keyed by the front/primary name) and shared front<->rear.
    val clipKey = group.primary.name
    val allMarkers by viewModel.markers.collectAsStateWithLifecycle()
    val segments = allMarkers[clipKey].orEmpty()
    var markersVisible by remember { mutableStateOf(false) }
    var lastExport by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // Screen owns the player so the marker buttons can read the current position and seek.
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply { playWhenReady = true } }
    androidx.compose.runtime.LaunchedEffect(playUri, file.isVideo) {
        if (!file.isVideo) return@LaunchedEffect // photos are shown as an image, not played
        val resumeAt = if (keepPosition) player.currentPosition else 0L
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(playUri))
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { player.release() } }

    fun toggleCamera() {
        if (group.hasFront && group.hasRear) {
            keepPosition = true
            showRear = !showRear
        }
    }
    fun goToClip(newIndex: Int) {
        if (newIndex in groups.indices) {
            keepPosition = false
            index = newIndex
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Vertical swipe = prev/next clip; horizontal swipe = front/rear.
            .pointerInput(index, group) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                            if (kotlin.math.abs(dx) > 120f) toggleCamera()
                        } else if (kotlin.math.abs(dy) > 120f) {
                            if (dy < 0) goToClip(index + 1) else goToClip(index - 1)
                        }
                    },
                ) { _, drag -> dx += drag.x; dy += drag.y }
            },
    ) {
        // Inset the player by the nav bar so its transport controls (scrubber + gear) sit
        // above the system back/home/recents buttons.
        if (file.isVideo) {
            ExoSurface(
                player = player,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                onControlsVisible = { controlsVisible = it },
            )
        } else {
            coil.compose.AsyncImage(
                model = playUri,
                contentDescription = file.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                // Top + horizontal insets so the Front/Rear toggle clears the status bar and
                // the side navigation bar / cutout in landscape.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Back to library", tint = Color.White)
            }
            Text(
                file.name,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            if (file.isVideo) IconButton(onClick = { markersVisible = !markersVisible }) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = "Cut markers",
                    tint = if (segments.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White,
                )
            }
            // Local (offline) clips are already on the phone — no download control for them.
            if (file.downloadUrl.startsWith("http", ignoreCase = true)) {
                DownloadControl(
                    group, downloads,
                    onDownload = { viewModel.download(*it.toTypedArray()) },
                    onCancel = { viewModel.cancel(*listOfNotNull(group.front, group.rear).toTypedArray()) },
                )
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete recording", tint = Color.White)
            }
            if (group.hasFront && group.hasRear) {
                Spacer(Modifier.width(4.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = !showRear,
                        onClick = { keepPosition = true; showRear = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Front") }
                    SegmentedButton(
                        selected = showRear,
                        onClick = { keepPosition = true; showRear = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Rear") }
                }
            }
        }

        if (markersVisible) {
            MarkersPanel(
                segments = segments,
                message = lastExport,
                // Lift above the transport controls (scrubber + buttons) when they're shown.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (controlsVisible) 84.dp else 0.dp),
                onMarkIn = { viewModel.markIn(clipKey, player.currentPosition / 1000.0) },
                onMarkOut = { viewModel.markOut(clipKey, player.currentPosition / 1000.0) },
                onSeek = { player.seekTo((it * 1000).toLong()) },
                onDelete = { viewModel.deleteSegment(clipKey, it) },
                onExport = {
                    val uri = viewModel.exportLlc(clipKey, file.name)
                    lastExport = if (uri != null) "Saved ${file.name.substringBeforeLast('.')}.llc to Download/NovaDash"
                    else "No complete segments to export"
                },
            )
        }

        if (confirmDelete) {
            val isCamera = file.downloadUrl.startsWith("http", ignoreCase = true)
            val what = if (group.hasFront && group.hasRear) "front + rear" else "this file"
            val where = if (isCamera) "from the camera SD card" else "from this phone"
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete recording?") },
                text = { Text("This removes $what $where. This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.deleteRecording(group) { err ->
                            if (err == null) onBack()
                            else android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                },
            )
        }
    }
}

/** Bottom panel for setting cut in/out points and exporting a LosslessCut project. */
@Composable
private fun MarkersPanel(
    segments: List<Segment>,
    message: String?,
    modifier: Modifier = Modifier,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onSeek: (Double) -> Unit,
    onDelete: (Int) -> Unit,
    onExport: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = onMarkIn) { Text("Set In") }
            androidx.compose.material3.OutlinedButton(onClick = onMarkOut) { Text("Set Out") }
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Button(onClick = onExport) { Text("Export .llc") }
        }
        segments.forEachIndexed { i, seg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "%d.  %s  →  %s".format(
                        i + 1, formatTime(seg.start), seg.end?.let { formatTime(it) } ?: "…",
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).clickable { onSeek(seg.start) },
                )
                IconButton(onClick = { onDelete(i) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete segment", tint = Color.White)
                }
            }
        }
        message?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt()
    val m = total / 60
    val s = total % 60
    val ms = ((seconds - total) * 10).toInt()
    return "%d:%02d.%d".format(m, s, ms)
}

/** Download button that offers Front / Rear / Both when a rear clip exists, and reflects the
 *  aggregate download progress of the recording. */
@Composable
private fun DownloadControl(
    group: RecordingGroup,
    downloads: Map<String, DownloadState>,
    onDownload: (List<MediaFile>) -> Unit,
    onCancel: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val front = group.front
    val rear = group.rear
    val states = listOfNotNull(front, rear).map { downloads[it.cameraPath] }
    val inProgress = states.filterIsInstance<DownloadState.InProgress>().maxByOrNull { it.fraction }
    val queued = states.any { it is DownloadState.Queued }
    val allDone = states.isNotEmpty() && states.all { it is DownloadState.Done }

    Box {
        when {
            inProgress != null || queued -> IconButton(onClick = onCancel) {
                if (inProgress != null) {
                    CircularProgressIndicator(
                        progress = { inProgress.fraction }, color = Color.White, modifier = Modifier.size(24.dp),
                    )
                } else {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel download", tint = Color.White)
                }
            }
            allDone -> Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = Color.White)
            else -> IconButton(onClick = {
                if (front != null && rear != null) menuOpen = true
                else onDownload(listOfNotNull(front ?: rear))
            }) {
                Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            front?.let { f ->
                DropdownMenuItem(text = { Text("Front") }, onClick = { onDownload(listOf(f)); menuOpen = false })
            }
            rear?.let { r ->
                DropdownMenuItem(text = { Text("Rear") }, onClick = { onDownload(listOf(r)); menuOpen = false })
            }
            if (front != null && rear != null) {
                DropdownMenuItem(
                    text = { Text("Both") },
                    onClick = { onDownload(listOf(front, rear)); menuOpen = false },
                )
            }
        }
    }
}
