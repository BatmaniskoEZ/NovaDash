package com.novadash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novadash.ui.connect.ConnectScreen
import com.novadash.ui.home.HomeScreen
import com.novadash.ui.theme.NovaDashTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovaDashTheme {
                val navController = rememberNavController()
                // Surface supplies the themed background (previously from the outer Scaffold we
                // removed). No inset padding here so full-screen playback can draw edge-to-edge;
                // each destination handles its own insets.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = "connect",
                    ) {
                        composable("connect") {
                            ConnectScreen(onConnected = { navController.navigate("home") })
                        }
                        composable("home") {
                            HomeScreen()
                        }
                    }
                }
            }
        }
    }
}
