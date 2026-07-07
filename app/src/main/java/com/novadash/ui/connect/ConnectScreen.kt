package com.novadash.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novadash.data.CameraConnection

/**
 * M1 landing screen: connect to the camera and show its status/firmware. Later milestones
 * navigate onward to Live/Files/Settings once [CameraConnection.Connected].
 */
@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.connection.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "NovaDash",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Connect to your dashcam's Wi-Fi, then tap Connect.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
        )

        when (val s = state) {
            is CameraConnection.Disconnected -> {
                Button(onClick = viewModel::connect) { Text("Connect") }
                OutlinedButton(
                    onClick = { viewModel.goOffline(); onConnected() },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("View downloaded (offline)") }
            }

            is CameraConnection.Connecting -> {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                Text("Contacting camera…", modifier = Modifier.padding(top = 16.dp))
            }

            is CameraConnection.Connected -> {
                Text(
                    text = "Connected" + (s.firmware?.let { "\nFirmware: $it" } ?: ""),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = onConnected,
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Continue") }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Disconnect") }
            }

            is CameraConnection.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = viewModel::connect,
                    modifier = Modifier.padding(top = 24.dp),
                ) { Text("Retry") }
                OutlinedButton(
                    onClick = { viewModel.goOffline(); onConnected() },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("View downloaded (offline)") }
            }
        }
    }
}
