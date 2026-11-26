package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxcraft_studio_settings"
)

enum class PerformanceMode(
    val label: String,
    val badge: String,
    val description: String
) {
    BALANCED(
        label = "Balanced",
        badge = "BALANCED",
        description = "Smooth animations and full prosody fidelity with low battery draw."
    ),
    HIGH_PERFORMANCE(
        label = "High Performance",
        badge = "PERF+",
        description = "Higher-rate live audio metering and snappier studio UI responsiveness."
    ),
    ULTRA_LOW_LATENCY(
        label = "Ultra Low-Latency",
        badge = "TURBO",
        description = "Streamlined synthesis directives and high-frequency audio polling for fastest turnaround."
    )
}

data class AppSettings(
    val performanceMode: PerformanceMode = PerformanceMode.HIGH_PERFORMANCE,
    val force144Hz: Boolean = true,
    val amoledDark: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val glassBlurRadiusDp: Float = 16f
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val FORCE_144HZ = booleanPreferencesKey("force_144hz")
        val AMOLED_DARK = booleanPreferencesKey("amoled_dark")
        val LIQUID_GLASS = booleanPreferencesKey("liquid_glass_enabled")
        val GLASS_BLUR_DP = floatPreferencesKey("glass_blur_dp")
    }

    val settings: Flow<AppSettings> = context.studioSettingsStore.data.map { prefs ->
        val modeName = prefs[Keys.PERFORMANCE_MODE]
        val mode = PerformanceMode.entries.find { it.name == modeName }
            ?: PerformanceMode.HIGH_PERFORMANCE
        AppSettings(
            performanceMode = mode,
            force144Hz = prefs[Keys.FORCE_144HZ] ?: true,
            amoledDark = prefs[Keys.AMOLED_DARK] ?: false,
            liquidGlassEnabled = prefs[Keys.LIQUID_GLASS] ?: true,
            glassBlurRadiusDp = (prefs[Keys.GLASS_BLUR_DP] ?: 16f).coerceIn(0f, 28f)
        )
    }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        context.studioSettingsStore.edit { it[Keys.PERFORMANCE_MODE] = mode.name }
    }

    suspend fun setForce144Hz(enabled: Boolean) {
        context.studioSettingsStore.edit { it[Keys.FORCE_144HZ] = enabled }
    }

    suspend fun setAmoledDark(enabled: Boolean) {
        context.studioSettingsStore.edit { it[Keys.AMOLED_DARK] = enabled }
    }

    suspend fun setLiquidGlass(enabled: Boolean) {
        context.studioSettingsStore.edit { it[Keys.LIQUID_GLASS] = enabled }
    }

    suspend fun setGlassBlurRadius(radiusDp: Float) {
        context.studioSettingsStore.edit { it[Keys.GLASS_BLUR_DP] = radiusDp.coerceIn(0f, 28f) }
    }
}
