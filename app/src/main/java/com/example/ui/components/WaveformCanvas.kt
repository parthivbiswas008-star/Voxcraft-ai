package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun InteractiveWaveformVisualizer(
    peaks: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    secondaryAccent: Color = MaterialTheme.colorScheme.secondary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val safePeaks = if (peaks.isEmpty()) List(44) { 0.3f } else peaks

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = "Interactive audio waveform scrubber" }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
    ) {
        val count = safePeaks.size
        if (count == 0 || size.width <= 0f) return@Canvas

        val spacing = size.width / count.toFloat()
        val barWidth = (spacing * 0.56f).coerceIn(2.5f, 8f)
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.90f
        val playheadX = (progress.coerceIn(0f, 1f)) * size.width

        val activeBrush = Brush.verticalGradient(
            colors = listOf(activeColor, secondaryAccent.copy(alpha = 0.85f), activeColor)
        )

        safePeaks.forEachIndexed { index, rawPeak ->
            val x = index * spacing + (spacing - barWidth) / 2f
            val isPlayed = (x + barWidth / 2f) <= playheadX
            val shimmer = if (isPlaying && isPlayed) {
                0.88f + 0.14f * sin(pulsePhase + index * 0.45f)
            } else {
                1f
            }
            val barH = (rawPeak.coerceIn(0.10f, 1f) * maxBarHeight * shimmer)
                .coerceIn(6f, maxBarHeight)
            val top = centerY - barH / 2f

            if (isPlayed) {
                drawRoundRect(
                    brush = activeBrush,
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            } else {
                drawRoundRect(
                    color = inactiveColor.copy(alpha = 0.75f),
                    topLeft = Offset(x, top),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        // Crisp electric cyan playhead needle
        drawLine(
            color = activeColor,
            start = Offset(playheadX, 2f),
            end = Offset(playheadX, size.height - 2f),
            strokeWidth = 2.5f
        )
    }
}

@Composable
fun LiveOscilloscopeWaveform(
    amplitudes: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp
) {
    val activeStart = if (isRecording) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val activeMid = MaterialTheme.colorScheme.primary
    val activeEnd = MaterialTheme.colorScheme.secondary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = if (isRecording) {
                    "Live microphone recording waveform"
                } else {
                    "Captured voice sample waveform"
                }
            }
    ) {
        val count = amplitudes.size
        if (count == 0 || size.width <= 0f) return@Canvas

        val centerY = size.height / 2f

        // Subtle studio center reference line
        drawLine(
            color = baselineColor.copy(alpha = 0.45f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.2f
        )

        val spacing = size.width / count.toFloat()
        val barWidth = (spacing * 0.54f).coerceIn(2.5f, 8f)
        val maxH = size.height * 0.88f

        val gradient = Brush.horizontalGradient(
            colors = listOf(activeStart, activeMid, activeEnd)
        )

        amplitudes.forEachIndexed { i, amp ->
            val h = (amp.coerceIn(0.06f, 1f) * maxH).coerceAtLeast(6f)
            val x = i * spacing + (spacing - barWidth) / 2f
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun MiniVoiceSignature(
    peaks: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val bars = if (peaks.isEmpty()) List(20) { 0.35f } else peaks.take(24)
    Canvas(
        modifier = modifier
            .height(26.dp)
            .semantics { contentDescription = "Voice acoustic signature" }
    ) {
        val count = bars.size
        if (count == 0 || size.width <= 0f) return@Canvas
        val spacing = size.width / count.toFloat()
        val barW = (spacing * 0.55f).coerceIn(2f, 5f)
        val centerY = size.height / 2f
        bars.forEachIndexed { idx, value ->
            val h = (value.coerceIn(0.14f, 1f) * size.height * 0.92f).coerceAtLeast(4f)
            val x = idx * spacing + (spacing - barW) / 2f
            drawRoundRect(
                color = accentColor.copy(alpha = 0.45f + value * 0.55f),
                topLeft = Offset(x, centerY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}
