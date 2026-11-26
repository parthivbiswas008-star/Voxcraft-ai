package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StudioRepository
import com.example.data.local.GenerationEntity
import com.example.model.AudioPlaybackState
import com.example.ui.components.MiniVoiceSignature
import com.example.viewmodel.StudioViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    generations: List<GenerationEntity>,
    playbackState: AudioPlaybackState,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onPlayGeneration: (GenerationEntity) -> Unit,
    onRemixGeneration: (GenerationEntity) -> Unit,
    onShareGeneration: (GenerationEntity) -> Unit,
    onDeleteGeneration: (GenerationEntity) -> Unit,
    onGoToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<GenerationEntity?>(null) }

    val filtered = remember(generations, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) generations
        else generations.filter {
            it.scriptText.lowercase().contains(q) ||
                it.voiceName.lowercase().contains(q) ||
                it.emotionPreset.lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Local Audio Archive",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${generations.size} WAV renders saved privately on device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (generations.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_field"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = {
                    Text(
                        text = "Search transcripts, voices, or moods…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (filtered.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Empty history",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(42.dp)
                    )
                    Text(
                        text = if (generations.isEmpty()) {
                            stringResource(R.string.empty_history_title)
                        } else {
                            "No Matching Clips"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (generations.isEmpty()) {
                            stringResource(R.string.empty_history_subtitle)
                        } else {
                            "Try another keyword to locate your saved render."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (generations.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onGoToStudio,
                            modifier = Modifier.testTag("empty_history_go_studio_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Open Studio Playground")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    val isThisPlaying = playbackState.trackId == item.id && playbackState.isPlaying
                    HistoryGenerationCard(
                        entity = item,
                        isPlaying = isThisPlaying,
                        onPlayToggle = { onPlayGeneration(item) },
                        onRemix = { onRemixGeneration(item) },
                        onShare = { onShareGeneration(item) },
                        onRequestDelete = { pendingDelete = item }
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete Audio Render?") },
            text = { Text("Remove this WAV clip permanently from local device storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteGeneration(target)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HistoryGenerationCard(
    entity: GenerationEntity,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onRemix: () -> Unit,
    onShare: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val accent = if (entity.isClonedVoice) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val peaks = remember(entity.waveformPeaksCsv) {
        StudioRepository.parsePeaks(entity.waveformPeaksCsv)
    }
    val timeLabel = remember(entity.createdAt) {
        SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(entity.createdAt))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${entity.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isPlaying) 1.5.dp else 1.dp,
            color = if (isPlaying) accent else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onPlayToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("history_play_${entity.id}"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (isPlaying) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            accent
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.cd_pause_audio else R.string.cd_play_audio
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entity.voiceName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entity.isClonedVoice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "CLONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "$timeLabel • ${StudioViewModel.formatDuration(entity.durationMs)} • ${entity.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                MiniVoiceSignature(
                    peaks = peaks,
                    accentColor = accent,
                    modifier = Modifier.width(56.dp)
                )
            }

            Text(
                text = entity.scriptText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetadataPill(entity.emotionPreset)
                MetadataPill(String.format("%.2fx", entity.speed))
                MetadataPill(entity.language)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRemix,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("history_remix_${entity.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Remix in studio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_remix_in_studio),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("history_share_${entity.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.cd_share_audio),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share WAV",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onRequestDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("history_delete_${entity.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.cd_delete_item),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataPill(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
