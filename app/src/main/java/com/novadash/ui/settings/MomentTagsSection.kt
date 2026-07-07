package com.novadash.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novadash.data.TagPresetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MomentTagsViewModel @Inject constructor(
    private val repository: TagPresetsRepository,
) : ViewModel() {
    val tags = repository.tags
    fun add(tag: String) = repository.add(tag)
    fun remove(tag: String) = repository.remove(tag)
    fun reset() = repository.resetToDefaults()
}

/**
 * Card to manage the quick tags offered when saving a moment (used on the Android Auto screen).
 * Self-contained (local repository) so it works whether or not the camera is connected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MomentTagsSection(viewModel: MomentTagsViewModel = hiltViewModel()) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf("") }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Moment tags (Android Auto)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a tag on the car screen to save a moment with it. The first few (as many " +
                    "as the car allows) are shown while driving.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (tags.isEmpty()) {
                Text("No tags — add one below.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.remove(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $tag",
                                    modifier = Modifier.padding(start = 2.dp),
                                )
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("New tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        viewModel.add(newTag)
                        newTag = ""
                    },
                    enabled = newTag.isNotBlank(),
                ) { Text("Add") }
            }

            TextButton(onClick = viewModel::reset) { Text("Reset to defaults") }
        }
    }
}

/** Offline Settings: camera controls need a connection, but tags are local and stay editable. */
@Composable
fun OfflineTagSettings() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card {
            Text(
                "Connect to the camera to change camera settings.",
                modifier = Modifier.padding(12.dp),
            )
        }
        MomentTagsSection()
    }
}
