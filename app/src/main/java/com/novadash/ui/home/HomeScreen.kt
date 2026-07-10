package com.novadash.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.novadash.ui.files.FilesScreen
import androidx.compose.material.icons.filled.Flag
import com.novadash.ui.live.LiveScreen
import com.novadash.ui.moments.MomentsScreen
import com.novadash.ui.settings.SettingsScreen

private enum class HubTab(val label: String, val icon: ImageVector) {
    LIVE("Live", Icons.Filled.Videocam),
    FILES("Files", Icons.Filled.VideoLibrary),
    MOMENTS("Moments", Icons.Filled.Flag),
    SETTINGS("Settings", Icons.Filled.Settings),
}

/** Main hub shown once the camera is connected: bottom-nav across the four feature areas. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel()) {
    val offline = viewModel.offline
    // Offline has no live camera, so land on Files.
    var tab by remember { mutableStateOf(if (offline) HubTab.FILES else HubTab.LIVE) }

    // A clip playing full-screen is drawn over the hub (edge-to-edge) and hides the nav bar.
    // Holds the ordered clip list + starting index so the player can swipe between clips.
    var playing by remember {
        mutableStateOf<Pair<List<com.novadash.data.RecordingGroup>, Int>?>(null)
    }
    // Set when a moment asks Files to scroll to a specific clip.
    var pendingScrollKey by remember { mutableStateOf<String?>(null) }

    // Files/Settings/Moments need recording paused (they list the camera album, which this
    // firmware only serves reliably — and safely — with recording stopped); Live resumes it.
    androidx.compose.runtime.LaunchedEffect(tab) {
        viewModel.setBrowsing(
            tab == HubTab.FILES || tab == HubTab.SETTINGS || tab == HubTab.MOMENTS,
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Confine the hub to the safe area so the nav bar sits above the system buttons;
            // the fullscreen playback overlay below is a sibling and stays edge-to-edge.
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (playing == null) {
                    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                        HubTab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    HubTab.LIVE -> if (offline) ComingSoon("Connect to camera for live view") else LiveScreen()
                    HubTab.FILES -> FilesScreen(
                        onPlay = { list, idx -> playing = list to idx },
                        scrollToKey = pendingScrollKey,
                        onScrolled = { pendingScrollKey = null },
                    )
                    HubTab.MOMENTS -> MomentsScreen(
                        onJumpToClip = { key -> pendingScrollKey = key; tab = HubTab.FILES },
                    )
                    HubTab.SETTINGS -> if (offline) {
                        com.novadash.ui.settings.OfflineTagSettings()
                    } else {
                        SettingsScreen()
                    }
                }
            }
        }

        // Full-screen playback overlay — outside the Scaffold so it ignores system-bar insets.
        playing?.let { (clips, index) ->
            com.novadash.ui.player.PlaybackScreen(
                groups = clips,
                startIndex = index,
                onBack = { playing = null },
            )
        }
    }
}

/** Simple centered message (used for camera-required tabs while offline). */
@Composable
fun ComingSoon(title: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
