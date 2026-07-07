package com.novadash.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.novadash.data.CameraChoice
import com.novadash.data.DownloadState
import com.novadash.data.RecordingGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onPlay: (List<RecordingGroup>, Int) -> Unit = { _, _ -> },
    scrollToKey: String? = null,
    onScrolled: () -> Unit = {},
    viewModel: FilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<List<RecordingGroup>?>(null) }
    val gridState = rememberLazyGridState()
    var highlightKey by remember { mutableStateOf<String?>(null) }

    // Scroll to and briefly highlight a clip requested from the Moments tab.
    androidx.compose.runtime.LaunchedEffect(scrollToKey, state.sections) {
        val key = scrollToKey ?: return@LaunchedEffect
        var index = 0
        var found = -1
        for ((_, groups) in state.sections) {
            index++ // day header item
            val pos = groups.indexOfFirst { it.key == key }
            if (pos >= 0) { found = index + pos; break }
            index += groups.size
        }
        if (found >= 0) {
            gridState.animateScrollToItem(found)
            highlightKey = key
            kotlinx.coroutines.delay(2500)
            highlightKey = null
        }
        onScrolled()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.selectionMode) {
            SelectionBar(
                count = state.selected.size,
                onDownload = viewModel::downloadSelected,
                onDelete = { confirmDelete = state.groups.filter { it.key in state.selected } },
                onClear = viewModel::clearSelection,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }

        DownloadsBar(
            downloads = downloads,
            nameFor = { path -> path.substringAfterLast('\\').substringAfterLast('/') },
            onCancel = viewModel::cancelPath,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.visibleGroups.isEmpty() -> Text("No files", Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sections.forEach { (day, groups) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hdr-$day") {
                            Text(
                                day,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(groups, key = { it.key }) { group ->
                            GroupCard(
                                group = group,
                                download = downloads[group.primary.cameraPath],
                                selected = group.key in state.selected,
                                selectionMode = state.selectionMode,
                                highlighted = group.key == highlightKey,
                                showActions = !viewModel.offline, // offline clips are already local
                                onTap = {
                                    if (state.selectionMode) viewModel.toggleSelected(group)
                                    else onPlay(state.visibleGroups, state.visibleGroups.indexOf(group))
                                },
                                onLongPress = { if (!viewModel.offline) viewModel.toggleSelected(group) },
                                onDownload = { viewModel.download(group.primary) },
                                onCancel = { viewModel.cancelDownload(group) },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${targets.size} recording(s)?") },
            text = { Text("This permanently removes them (front and rear) from the SD card.") },
            confirmButton = {
                TextButton(onClick = {
                    if (state.selectionMode) viewModel.deleteSelected()
                    else targets.firstOrNull()?.let(viewModel::delete)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/** Compact status strip listing active/queued downloads with progress, speed, and cancel. */
@Composable
private fun DownloadsBar(
    downloads: Map<String, DownloadState>,
    nameFor: (String) -> String,
    onCancel: (String) -> Unit,
) {
    val active = downloads.entries.filter {
        it.value is DownloadState.InProgress || it.value is DownloadState.Queued
    }
    if (active.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        active.forEach { (path, state) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val label = when (state) {
                    is DownloadState.InProgress ->
                        "${(state.fraction * 100).toInt()}%  ·  ${formatSpeed(state.bytesPerSec)}  ·  " +
                            "${formatSize(state.totalBytes - state.downloadedBytes)} left"
                    DownloadState.Queued -> "Queued"
                    else -> ""
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(nameFor(path), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { onCancel(path) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel download")
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String = when {
    bytesPerSec <= 0 -> "—"
    bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    else -> "%d KB/s".format(bytesPerSec / 1000)
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    bytes >= 1000 -> "%d KB".format(bytes / 1000)
    else -> "$bytes B"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    onDownload: (CameraChoice) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Clear selection") }
        },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Download, "Download selected")
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Front only") },
                        onClick = { onDownload(CameraChoice.FRONT); menuOpen = false },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Rear only") },
                        onClick = { onDownload(CameraChoice.REAR); menuOpen = false },
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Both") },
                        onClick = { onDownload(CameraChoice.BOTH); menuOpen = false },
                    )
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete selected") }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(
    group: RecordingGroup,
    download: DownloadState?,
    selected: Boolean,
    selectionMode: Boolean,
    highlighted: Boolean,
    showActions: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(
                    if (highlighted) Modifier.border(
                        3.dp, MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                    ) else Modifier
                )
                .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        ) {
            AsyncImage(
                model = group.primary.thumbnailUrl,
                contentDescription = group.primary.name,
                modifier = Modifier.fillMaxSize(),
            )
            if (!selectionMode) {
                Icon(
                    if (group.primary.isVideo) Icons.Filled.PlayCircle else Icons.Filled.Photo,
                    contentDescription = if (group.primary.isVideo) "Play" else "Photo",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
            if (group.isEvent) {
                Icon(
                    Icons.Filled.Lock, "Event/locked",
                    tint = Color.Red,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
                )
            }
            if (group.hasRear) {
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Videocam, "Has rear camera",
                        tint = Color.White, modifier = Modifier.size(14.dp),
                    )
                    Text("F+R", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (selectionMode) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(22.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.primary.time ?: group.primary.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (showActions) when (download) {
                is DownloadState.Queued ->
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, "Cancel (queued)", modifier = Modifier.size(18.dp))
                    }
                is DownloadState.InProgress ->
                    // Tap the ring to cancel the in-flight download.
                    IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                        CircularProgressIndicator(progress = { download.fraction }, modifier = Modifier.size(18.dp))
                    }
                is DownloadState.Done ->
                    Text("✓", color = MaterialTheme.colorScheme.primary)
                else ->
                    IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Download, "Download", modifier = Modifier.size(18.dp))
                    }
            }
        }
    }
}
