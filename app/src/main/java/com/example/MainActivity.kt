package com.example

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        // STEP 2: Dark obsidian / AMOLED edge-to-edge system bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // STEP 3: Render VoxCraft AI Studio with reactive 144Hz Force Mode, AMOLED & Liquid Glass
        setContent {
            val studioViewModel: StudioViewModel = viewModel()
            val settings by studioViewModel.appSettings.collectAsStateWithLifecycle()

            var activeRefreshRateHz by remember { mutableFloatStateOf(queryCurrentRefreshRate()) }
            val maxSupportedHz = remember { queryMaxSupportedRefreshRate() }

            LaunchedEffect(settings.force144Hz) {
                activeRefreshRateHz = applyDisplayRefreshRate(force144Hz = settings.force144Hz)
            }

            VoxCraftTheme(
                amoledDark = settings.amoledDark,
                liquidGlassEnabled = settings.liquidGlassEnabled,
                glassBlurRadiusDp = settings.glassBlurRadiusDp
            ) {
                VoxCraftApp(
                    viewModel = studioViewModel,
                    activeRefreshRateHz = activeRefreshRateHz,
                    maxSupportedHz = maxSupportedHz
                )
            }
        }
    }

    private fun resolveDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

    private fun queryCurrentRefreshRate(): Float =
        resolveDisplay()?.refreshRate ?: 60f

    private fun queryMaxSupportedRefreshRate(): Float {
        val display = resolveDisplay() ?: return 60f
        return display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    }

    private fun applyDisplayRefreshRate(force144Hz: Boolean): Float {
        val display = resolveDisplay() ?: return 60f
        val params = window.attributes
        if (force144Hz) {
            val currentMode = display.mode
            val sameResModes = display.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }.ifEmpty { display.supportedModes.toList() }

            // Pick highest refresh rate up to 144Hz (or the panel's peak supported rate)
            val bestMode = sameResModes
                .filter { it.refreshRate <= 144.5f }
                .maxByOrNull { it.refreshRate }
                ?: sameResModes.maxByOrNull { it.refreshRate }

            if (bestMode != null) {
                params.preferredDisplayModeId = bestMode.modeId
                params.preferredRefreshRate = bestMode.refreshRate
                window.attributes = params
                return bestMode.refreshRate
            } else {
                params.preferredRefreshRate = 144f
                window.attributes = params
                return display.refreshRate
            }
        } else {
            params.preferredDisplayModeId = 0
            params.preferredRefreshRate = 0f
            window.attributes = params
            return display.refreshRate
        }
    }
}
