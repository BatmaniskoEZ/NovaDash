package com.novadash.ui.moments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novadash.data.CameraChoice
import com.novadash.data.SavedMoment
import java.text.SimpleDateFormat
import java.util.Locale

private val TIME_FMT = SimpleDateFormat("EEE d MMM, HH:mm:ss", Locale.getDefault())

@Composable
fun MomentsScreen(
    onJumpToClip: (String) -> Unit = {},
    viewModel: MomentsViewModel = hiltViewModel(),
) {
    val moments by viewModel.moments.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SavedMoment?>(null) }
    var windowFor by remember { mutableStateOf<SavedMoment?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveNow, modifier = Modifier.weight(1f)) {
                Text("Save moment now")
            }
            IconButton(onClick = viewModel::loadGroups) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh clips")
            }
        }
        Text(
            "Saved moments are matched to your recorded clips by time. Tap one to jump to its " +
                "clip or download footage around it.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (moments.isEmpty()) {
            Text("No saved moments yet.", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        // Recompute matches whenever the clip list changes.
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(moments, key = { it.id }) { m ->
                val match = remember(m, groups) { viewModel.matchingGroup(m) }
                MomentCard(
                    moment = m,
                    matchLabel = match?.primary?.name ?: "no clip (may be overwritten)",
                    canJump = match != null,
                    canDownload = !viewModel.offline,
                    onJump = { match?.let { onJumpToClip(it.key) } },
                    onDownload = { windowFor = m },
                    onEditTag = { editing = m },
                    onDelete = { viewModel.delete(m.id) },
                )
            }
        }
    }

    editing?.let { m ->
        TagDialog(
            initial = m.tag,
            onDismiss = { editing = null },
            onSave = { viewModel.updateTag(m.id, it); editing = null },
        )
    }
    windowFor?.let { m ->
        DownloadWindowSheet(moment = m, viewModel = viewModel, onDismiss = { windowFor = null })
    }
}

@Composable
private fun MomentCard(
    moment: SavedMoment,
    matchLabel: String,
    canJump: Boolean,
    canDownload: Boolean,
    onJump: () -> Unit,
    onDownload: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEditTag)) {
                Text(TIME_FMT.format(moment.epochMillis), style = MaterialTheme.typography.bodyMedium)
                Text(
                    moment.tag.ifBlank { "Tap to add tag" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (moment.tag.isBlank()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                )
                if (moment.source.isNotBlank()) {
                    Text(
                        "#${moment.source}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(matchLabel, style = MaterialTheme.typography.labelSmall)
            }
            if (canJump) IconButton(onClick = onJump) {
                Icon(Icons.Filled.PlayCircle, contentDescription = "Jump to clip")
            }
            if (canDownload) IconButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = "Download around moment")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete moment")
            }
        }
    }
}

@Composable
private fun TagDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadWindowSheet(
    moment: SavedMoment,
    viewModel: MomentsViewModel,
    onDismiss: () -> Unit,
) {
    var back by remember { mutableStateOf(1f) }
    var fwd by remember { mutableStateOf(1f) }
    val inWindow = viewModel.groupsInWindow(moment, back.toInt(), fwd.toInt())
    val totalMb = inWindow.sumOf { (it.front?.size ?: 0) + (it.rear?.size ?: 0) } / 1_000_000.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Download around ${TIME_FMT.format(moment.epochMillis)}", style = MaterialTheme.typography.titleMedium)
            Text("Minutes before: ${back.toInt()}")
            Slider(value = back, onValueChange = { back = it }, valueRange = 0f..5f, steps = 4)
            Text("Minutes after: ${fwd.toInt()}")
            Slider(value = fwd, onValueChange = { fwd = it }, valueRange = 0f..5f, steps = 4)
            Text("%d recording(s) · ~%.0f MB".format(inWindow.size, totalMb))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.download(inWindow, CameraChoice.FRONT); onDismiss() }) { Text("Front") }
                OutlinedButton(onClick = { viewModel.download(inWindow, CameraChoice.REAR); onDismiss() }) { Text("Rear") }
                Button(onClick = { viewModel.download(inWindow, CameraChoice.BOTH); onDismiss() }) { Text("Both") }
            }
        }
    }
}
