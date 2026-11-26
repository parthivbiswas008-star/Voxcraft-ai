package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.VoxCraftApp
import com.example.ui.theme.VoxCraftTheme
import com.example.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE Compose UI or ViewModels
        configureAppCheck(this, intent)

        // STEP 2: Dark obsidian edge-to-edge system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // STEP 3: Render VoxCraft AI Studio
        setContent {
            VoxCraftTheme {
                val studioViewModel: StudioViewModel = viewModel()
                VoxCraftApp(viewModel = studioViewModel)
            }
        }
    }
}
