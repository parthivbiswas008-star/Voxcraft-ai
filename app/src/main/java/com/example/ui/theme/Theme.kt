package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorErrorSoft = Color(0xFFFDA4AF)

private val VoxCraftDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanDeep,
    onPrimaryContainer = ElectricCyanSoft,
    secondary = NeonViolet,
    onSecondary = OnNeonViolet,
    secondaryContainer = NeonVioletDeep,
    onSecondaryContainer = NeonVioletSoft,
    tertiary = SignalEmerald,
    onTertiary = OnElectricCyan,
    tertiaryContainer = SignalEmeraldDeep,
    onTertiaryContainer = SignalEmerald,
    background = ObsidianCanvas,
    onBackground = StudioInk,
    surface = StudioDeck,
    onSurface = StudioInk,
    surfaceVariant = StudioModule,
    onSurfaceVariant = StudioSlate,
    surfaceContainerLowest = ObsidianCanvas,
    surfaceContainerLow = StudioDeck,
    surfaceContainer = StudioModule,
    surfaceContainerHigh = StudioElevated,
    surfaceContainerHighest = StudioHighest,
    outline = StudioOutline,
    outlineVariant = StudioHairline,
    error = PeakRose,
    onError = OnPeakRose,
    errorContainer = PeakRoseDeep,
    onErrorContainer = ColorErrorSoft
)

private val VoxCraftAmoledColorScheme = VoxCraftDarkColorScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF05070A),
    surfaceContainer = Color(0xFF0A0D12),
    surfaceContainerHigh = Color(0xFF11161F),
    surfaceContainerHighest = Color(0xFF18202D),
    outlineVariant = Color(0xFF1E293B)
)

@Immutable
data class StudioAppearanceConfig(
    val amoledDark: Boolean = false,
    val liquidGlassEnabled: Boolean = true,
    val glassBlurRadiusDp: Float = 16f
)

val LocalStudioAppearance = staticCompositionLocalOf { StudioAppearanceConfig() }

val VoxCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxCraftTheme(
    amoledDark: Boolean = false,
    liquidGlassEnabled: Boolean = true,
    glassBlurRadiusDp: Float = 16f,
    content: @Composable () -> Unit
) {
    val scheme = if (amoledDark) VoxCraftAmoledColorScheme else VoxCraftDarkColorScheme
    val appearance = StudioAppearanceConfig(
        amoledDark = amoledDark,
        liquidGlassEnabled = liquidGlassEnabled,
        glassBlurRadiusDp = glassBlurRadiusDp
    )

    CompositionLocalProvider(LocalStudioAppearance provides appearance) {
        MaterialTheme(
            colorScheme = scheme,
            typography = VoxCraftTypography,
            shapes = VoxCraftShapes,
            content = content
        )
    }
}
