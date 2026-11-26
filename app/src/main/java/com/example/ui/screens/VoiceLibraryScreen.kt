package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.data.local.ClonedVoiceEntity
import com.example.model.StudioVoice
import com.example.model.VoiceCatalog
import com.example.model.VoiceCategory
import com.example.ui.components.MiniVoiceSignature

private data class VoiceDisplayItem(
    val voice: StudioVoice,
    val previewSampleText: String,
    val cloneEntity: ClonedVoiceEntity? = null
)

@Composable
fun VoiceLibraryScreen(
    selectedVoiceId: String,
    clonedVoices: List<ClonedVoiceEntity>,
    favoriteVoiceIds: Set<String>,
    searchQuery: String,
    selectedCategory: VoiceCategory,
    previewingVoiceId: String?,
    toStudioVoice: (ClonedVoiceEntity) -> StudioVoice,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (VoiceCategory) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onPreviewVoice: (StudioVoice, String) -> Unit,
    onUseVoice: (StudioVoice) -> Unit,
    onDeleteClone: (ClonedVoiceEntity) -> Unit,
    onOpenCloneLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteClone by remember { mutableStateOf<ClonedVoiceEntity?>(null) }

    val mergedVoices = remember(clonedVoices, favoriteVoiceIds, searchQuery, selectedCategory) {
        val cloneItems = clonedVoices.map { entity ->
            VoiceDisplayItem(
                voice = toStudioVoice(entity),
                previewSampleText = "Hello! This is ${entity.name}, your custom cloned voice inside VoxCraft Studio.",
                cloneEntity = entity
            )
        }
        val presetItems = VoiceCatalog.presets.map { preset ->
            VoiceDisplayItem(
                voice = VoiceCatalog.toStudioVoice(preset),
                previewSampleText = preset.previewSampleText,
                cloneEntity = null
            )
        }
        val combined = cloneItems + presetItems
        val q = searchQuery.trim().lowercase()

        combined.filter { item ->
            val matchesCategory = when (selectedCategory) {
                VoiceCategory.ALL -> true
                VoiceCategory.MY_CLONES -> item.voice.isCloned
                VoiceCategory.FAVORITES -> favoriteVoiceIds.contains(item.voice.id)
                else -> !item.voice.isCloned && item.voice.categoryLabel == selectedCategory.label
            }
            val matchesQuery = q.isEmpty() ||
                item.voice.displayName.lowercase().contains(q) ||
                item.voice.accent.lowercase().contains(q) ||
                item.voice.description.lowercase().contains(q) ||
                item.voice.tags.any { it.lowercase().contains(q) }
            matchesCategory && matchesQuery
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
                    text = "Voice Library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${VoiceCatalog.presets.size} Neural Voices • ${clonedVoices.size} Clones",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onOpenCloneLab,
                modifier = Modifier.testTag("voices_new_clone_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New voice clone",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Clone", style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_search_field"),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = {
                Text(
                    text = "Search voices, accents, or moods…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search voices",
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

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceCategory.entries.forEach { category ->
                val selected = selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryChange(category) },
                    modifier = Modifier.testTag("voice_filter_${category.name.lowercase()}"),
                    label = {
                        Text(
                            text = if (category == VoiceCategory.MY_CLONES && clonedVoices.isNotEmpty()) {
                                "${category.label} (${clonedVoices.size})"
                            } else {
                                category.label
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (category == VoiceCategory.MY_CLONES) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        selectedLabelColor = if (category == VoiceCategory.MY_CLONES) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = if (category == VoiceCategory.MY_CLONES) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (mergedVoices.isEmpty()) {
            EmptyVoicesCard(
                isCloneTab = selectedCategory == VoiceCategory.MY_CLONES,
                onOpenCloneLab = onOpenCloneLab
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(mergedVoices, key = { it.voice.id }) { item ->
                    VoiceLibraryCard(
                        item = item,
                        isSelected = item.voice.id == selectedVoiceId,
                        isFavorite = favoriteVoiceIds.contains(item.voice.id),
                        isPreviewLoading = previewingVoiceId == item.voice.id,
                        onToggleFavorite = { onToggleFavorite(item.voice.id) },
                        onPreview = { onPreviewVoice(item.voice, item.previewSampleText) },
                        onUse = { onUseVoice(item.voice) },
                        onRequestDelete = item.cloneEntity?.let { clone ->
                            { pendingDeleteClone = clone }
                        }
                    )
                }
            }
        }
    }

    pendingDeleteClone?.let { clone ->
        AlertDialog(
            onDismissRequest = { pendingDeleteClone = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Delete Cloned Voice?") },
            text = {
                Text("Remove \"${clone.name}\" and its local voice sample from this device?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteClone(clone)
                        pendingDeleteClone = null
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
                TextButton(onClick = { pendingDeleteClone = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun VoiceLibraryCard(
    item: VoiceDisplayItem,
    isSelected: Boolean,
    isFavorite: Boolean,
    isPreviewLoading: Boolean,
    onToggleFavorite: () -> Unit,
    onPreview: () -> Unit,
    onUse: () -> Unit,
    onRequestDelete: (() -> Unit)?
) {
    val voice = item.voice
    val accentColor = if (voice.isCloned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_card_${voice.id}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
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
                MiniVoiceSignature(
                    peaks = voice.waveformSignature,
                    accentColor = accentColor,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = voice.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (voice.isCloned) {
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
                    }
                    Text(
                        text = "${voice.accent} • ${voice.categoryLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("favorite_voice_${voice.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_favorite_voice),
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onRequestDelete != null) {
                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_clone_${voice.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = stringResource(R.string.cd_delete_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = voice.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                voice.tags.take(3).forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !isPreviewLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("preview_voice_${voice.id}"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (isPreviewLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading…", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_preview_voice),
                            modifier = Modifier.size(18.dp),
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Button(
                    onClick = onUse,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("use_voice_${voice.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            accentColor
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else if (voice.isCloned) {
                            MaterialTheme.colorScheme.onSecondary
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active voice",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Active", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(
                            text = stringResource(R.string.action_use_in_studio),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVoicesCard(
    isCloneTab: Boolean,
    onOpenCloneLab: () -> Unit
) {
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
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Empty voices",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = if (isCloneTab) stringResource(R.string.empty_clones_title) else "No Matching Voices",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isCloneTab) {
                    stringResource(R.string.empty_clones_subtitle)
                } else {
                    "Try a different search keyword or switch category filters."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isCloneTab) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onOpenCloneLab,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Text("Open Voice Clone Lab")
                }
            }
        }
    }
}
