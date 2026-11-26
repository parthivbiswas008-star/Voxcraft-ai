package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorErrorSoft = androidx.compose.ui.graphics.Color(0xFFFDA4AF)

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

val VoxCraftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxCraftTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoxCraftDarkColorScheme,
        typography = VoxCraftTypography,
        shapes = VoxCraftShapes,
        content = content
    )
}
