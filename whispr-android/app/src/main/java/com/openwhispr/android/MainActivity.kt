package com.openwhispr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.openwhispr.android.ui.screens.RecordingScreen
import com.openwhispr.android.ui.screens.SettingsScreen
import com.openwhispr.android.ui.theme.WhisprTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WhisprTheme {
                WhisprApp()
            }
        }
    }
}

@Composable
private fun WhisprApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "recording") {
        composable("recording") {
            RecordingScreen(onNavigateToSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
