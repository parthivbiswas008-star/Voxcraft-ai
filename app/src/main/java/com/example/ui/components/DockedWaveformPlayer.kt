package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AudioPlaybackState
import com.example.ui.theme.LocalStudioAppearance
import com.example.viewmodel.StudioViewModel

@Composable
fun DockedWaveformPlayer(
    playback: AudioPlaybackState,
    onTogglePlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleLoop: () -> Unit,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = LocalStudioAppearance.current
    val playerBg = if (appearance.liquidGlassEnabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("docked_waveform_player"),
        shape = RoundedCornerShape(22.dp),
        color = playerBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (playback.isPlaying) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playback.voiceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playback.isClonedVoice) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "CLONE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    playback.latencyMs?.let { ms ->
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${ms}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Text(
                    text = "${StudioViewModel.formatDuration(playback.positionMs)} / ${StudioViewModel.formatDuration(playback.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close_player),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_play_pause_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (playback.isPlaying) R.string.cd_pause_audio else R.string.cd_play_audio
                        )
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                InteractiveWaveformVisualizer(
                    peaks = playback.waveformPeaks,
                    progress = playback.progress,
                    isPlaying = playback.isPlaying,
                    onSeek = onSeek,
                    height = 40.dp,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("player_loop_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = stringResource(R.string.cd_loop_audio),
                        tint = if (playback.isLooping) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Save directly to Internal Storage (Downloads/VoxCraft AI)
                IconButton(
                    onClick = onSaveToDevice,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_save_storage_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save audio to internal storage",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(21.dp)
                    )
                }

                // Share WAV file to any app
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share_audio),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (playback.title.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = playback.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
