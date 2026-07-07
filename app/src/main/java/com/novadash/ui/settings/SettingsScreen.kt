package com.novadash.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.novadash.data.RecordingSettings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdvanced by remember { mutableStateOf(false) }
    var showFormatConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        // Message surfaced inline below; no snackbar host at this level.
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.message?.let { msg ->
            Card { Text(msg, modifier = Modifier.padding(12.dp)) }
        }

        // --- Recording ---
        state.recording?.let { rec ->
            RecordingCard(
                rec = rec,
                onResolution = viewModel::setResolution,
                onHdr = viewModel::setHdr,
                onLoop = viewModel::setLoop,
                onGsensor = viewModel::setGsensor,
                onVolume = viewModel::setVolume,
                onDatetime = viewModel::setDatetime,
                onFrequency = viewModel::setFrequency,
            )
        }

        // --- Voice / sound ---
        SectionCard("Sound & Voice") {
            RowSwitch(
                label = "Record audio on clips",
                checked = state.recording?.audio == true,
                onCheckedChange = viewModel::setRecordAudio,
            )
            Text(
                "The camera's voice prompts and beeps use FL_BEEP, which no single Wi-Fi " +
                    "command exposes. \"Mute voice & beep\" turns off every reachable lever " +
                    "and saves. If prompts persist, a firmware patch is the only full fix.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = viewModel::muteVoice, modifier = Modifier.fillMaxWidth()) {
                Text("Mute voice & beep")
            }
        }

        // --- Wi-Fi ---
        WifiCard(
            ssid = state.wifi?.ssid.orEmpty(),
            password = state.wifi?.passphrase.orEmpty(),
            onApply = viewModel::setWifi,
        )

        // --- Persistence & storage ---
        SectionCard("Storage") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        state.recordSeconds?.let { "~${formatDuration(it)} of recording left" }
                            ?: "Recording time left: …",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "SD card free: " + (state.freeBytes?.let { formatBytes(it) } ?: "…"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                OutlinedButton(onClick = viewModel::loadStorage) { Text("Refresh") }
            }
            OutlinedButton(onClick = viewModel::saveSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Save settings to camera")
            }
            if (!showFormatConfirm) {
                OutlinedButton(
                    onClick = { showFormatConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Format SD card…") }
            } else {
                Text("This erases everything on the SD card.", color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        showFormatConfirm = false
                        viewModel.formatSd()
                    }) { Text("Erase") }
                    OutlinedButton(onClick = { showFormatConfirm = false }) { Text("Cancel") }
                }
            }
        }

        // --- Advanced / experimental ---
        SectionCard("Advanced (experimental)") {
            Text(
                "Undocumented commands found in the firmware dispatch table. Effects are " +
                    "unverified — try at your own risk while watching the camera.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) "Hide" else "Show probes")
            }
            if (showAdvanced) {
                CustomCommandInput(onSend = viewModel::sendCustom)
                Divider()
                viewModel.experimentalCommands.forEach { cmd ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("cmd $cmd (par=0)")
                        OutlinedButton(onClick = { viewModel.probe(cmd) }) { Text("Run") }
                    }
                }
                if (state.probes.isNotEmpty()) {
                    Divider()
                    state.probes.takeLast(10).forEach { p ->
                        Text(
                            "cmd ${p.cmd} → ${p.message}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // --- Moment tags (Android Auto) ---
        MomentTagsSection()
    }
}

@Composable
private fun RecordingCard(
    rec: RecordingSettings,
    onResolution: (Int) -> Unit,
    onHdr: (Boolean) -> Unit,
    onLoop: (Int) -> Unit,
    onGsensor: (Int) -> Unit,
    onVolume: (Int) -> Unit,
    onDatetime: (Boolean) -> Unit,
    onFrequency: (Int) -> Unit,
) {
    SectionCard("Recording") {
        ChipRow(
            title = "Resolution",
            options = rec.resolutions.map { it.index to it.label },
            selected = rec.currentResolution,
            onSelect = onResolution,
        )
        RowSwitch(label = "WDR / HDR", checked = rec.hdr, onCheckedChange = onHdr)
        ChipRow(
            title = "Loop recording",
            options = RecordingSettings.LOOP_INTERVALS,
            selected = rec.loop,
            onSelect = onLoop,
        )
        ChipRow(
            title = "G-sensor sensitivity",
            options = RecordingSettings.GSENSOR_LEVELS,
            selected = rec.gsensor,
            onSelect = onGsensor,
        )
        ChipRow(
            title = "Volume (voice/beep)",
            options = RecordingSettings.VOLUME_LEVELS,
            selected = rec.volume,
            onSelect = onVolume,
        )
        ChipRow(
            title = "Light frequency",
            options = RecordingSettings.FREQUENCIES,
            selected = rec.frequency,
            onSelect = onFrequency,
        )
        RowSwitch(label = "Date/time stamp", checked = rec.datetime, onCheckedChange = onDatetime)
    }
}

/** A titled row of single-select filter chips backed by (value, label) options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

/** Free-form command sender: cmd + optional par + optional str -> camera, result appended below. */
@Composable
private fun CustomCommandInput(onSend: (Int, Int?, String?) -> Unit) {
    var cmd by remember { mutableStateOf("") }
    var par by remember { mutableStateOf("") }
    var str by remember { mutableStateOf("") }
    Text("Custom command", style = MaterialTheme.typography.bodyMedium)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = cmd,
            onValueChange = { cmd = it.filter(Char::isDigit) },
            label = { Text("cmd") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = par,
            onValueChange = { par = it.filter { c -> c.isDigit() || c == '-' } },
            label = { Text("par") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        value = str,
        onValueChange = { str = it },
        label = { Text("str (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = {
            cmd.toIntOrNull()?.let { onSend(it, par.toIntOrNull(), str) }
        },
        enabled = cmd.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Send command") }
}

/** Bytes → a short human size (decimal, matching SD-card labelling): "83.6 GB", "512 MB". */
private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1_000_000.0)
}

/** Seconds → "5h 34m" / "42m". */
private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun WifiCard(ssid: String, password: String, onApply: (String, String) -> Unit) {
    var ssidField by remember(ssid) { mutableStateOf(ssid) }
    var pwField by remember(password) { mutableStateOf(password) }
    SectionCard("Wi-Fi") {
        OutlinedTextField(
            value = ssidField,
            onValueChange = { ssidField = it },
            label = { Text("SSID") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pwField,
            onValueChange = { pwField = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onApply(ssidField, pwField) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply & save") }
    }
}
