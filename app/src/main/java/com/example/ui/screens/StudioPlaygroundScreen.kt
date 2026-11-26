package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EmotionPreset
import com.example.model.HumanizationConfig
import com.example.model.StudioInlineCues
import com.example.model.SupportedStudioLanguages
import com.example.model.SynthesisUiState
import com.example.ui.components.MiniVoiceSignature
import com.example.viewmodel.PlaygroundUiState

@Composable
fun StudioPlaygroundScreen(
    state: PlaygroundUiState,
    clonedVoicesCount: Int,
    onScriptChange: (String) -> Unit,
    onInsertCue: (String) -> Unit,
    onClearScript: () -> Unit,
    onUndoScript: () -> Unit,
    onAutoHumanize: () -> Unit,
    onOpenVoicePicker: () -> Unit,
    onOpenCloneLab: () -> Unit,
    onToggleHumanizationExpanded: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectEmotionPreset: (EmotionPreset) -> Unit,
    onEmotionAxisChange: (Float?, Float?, Float?, Float?, Float?) -> Unit,
    onToggleNaturalBreaths: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onGenerateSpeech: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wordCount by remember(state.scriptText) {
        derivedStateOf {
            state.scriptText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        }
    }
    val estDurationSec by remember(wordCount, state.humanization.speed) {
        derivedStateOf {
            if (wordCount == 0) 0 else ((wordCount / 2.5f) / state.humanization.speed).toInt().coerceAtLeast(1)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 820.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StudioHeroHeader(
                clonedVoicesCount = clonedVoicesCount,
                lastLatencyMs = state.lastGeneratedLatencyMs,
                onOpenCloneLab = onOpenCloneLab
            )

            // Transparent error surface
            if (state.synthesisState is SynthesisUiState.Error) {
                StudioErrorBanner(
                    message = state.synthesisState.message,
                    onRetry = onGenerateSpeech,
                    onDismiss = onDismissError
                )
            }

            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ActiveVoiceSelectorCard(
                            state = state,
                            onClick = onOpenVoicePicker
                        )
                        ScriptConsoleCard(
                            state = state,
                            wordCount = wordCount,
                            estDurationSec = estDurationSec,
                            onScriptChange = onScriptChange,
                            onInsertCue = onInsertCue,
                            onClearScript = onClearScript,
                            onUndoScript = onUndoScript,
                            onAutoHumanize = onAutoHumanize
                        )
                        GenerateSpeechCta(
                            synthesisState = state.synthesisState,
                            onGenerate = onGenerateSpeech
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.85f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HumanizationRackCard(
                            config = state.humanization,
                            expanded = true,
                            onToggleExpanded = onToggleHumanizationExpanded,
                            onSpeedChange = onSpeedChange,
                            onVolumeChange = onVolumeChange,
                            onSelectEmotionPreset = onSelectEmotionPreset,
                            onEmotionAxisChange = onEmotionAxisChange,
                            onToggleNaturalBreaths = onToggleNaturalBreaths,
                            onLanguageChange = onLanguageChange
                        )
                    }
                }
            } else {
                ActiveVoiceSelectorCard(
                    state = state,
                    onClick = onOpenVoicePicker
                )
                ScriptConsoleCard(
                    state = state,
                    wordCount = wordCount,
                    estDurationSec = estDurationSec,
                    onScriptChange = onScriptChange,
                    onInsertCue = onInsertCue,
                    onClearScript = onClearScript,
                    onUndoScript = onUndoScript,
                    onAutoHumanize = onAutoHumanize
                )
                GenerateSpeechCta(
                    synthesisState = state.synthesisState,
                    onGenerate = onGenerateSpeech
                )
                HumanizationRackCard(
                    config = state.humanization,
                    expanded = state.isHumanizationExpanded,
                    onToggleExpanded = onToggleHumanizationExpanded,
                    onSpeedChange = onSpeedChange,
                    onVolumeChange = onVolumeChange,
                    onSelectEmotionPreset = onSelectEmotionPreset,
                    onEmotionAxisChange = onEmotionAxisChange,
                    onToggleNaturalBreaths = onToggleNaturalBreaths,
                    onLanguageChange = onLanguageChange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StudioHeroHeader(
    clonedVoicesCount: Int,
    lastLatencyMs: Long?,
    onOpenCloneLab: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_studio_hero),
                contentDescription = stringResource(R.string.cd_studio_hero),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.42f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.80f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "VoxCraft emblem",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VoxCraft AI",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Unlocked Pro tier",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.studio_badge_pro),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryChip(
                            dotColor = MaterialTheme.colorScheme.tertiary,
                            text = lastLatencyMs?.let { "LAST ${it}ms" } ?: "NEURAL TTS READY"
                        )
                        TelemetryChip(
                            dotColor = MaterialTheme.colorScheme.secondary,
                            text = "$clonedVoicesCount CLONES • ∞ FREE"
                        )
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onOpenCloneLab)
                            .testTag("hero_quick_clone_button"),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = "+ Clone",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(dotColor: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveVoiceSelectorCard(
    state: PlaygroundUiState,
    onClick: () -> Unit
) {
    val voice = state.selectedVoice
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("active_voice_selector_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (voice.isCloned) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniVoiceSignature(
                peaks = voice.waveformSignature,
                accentColor = if (voice.isCloned) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voice.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (voice.isCloned) {
                        Spacer(modifier = Modifier.width(8.dp))
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${voice.accent} • ${voice.tags.take(3).joinToString(" · ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Choose voice",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScriptConsoleCard(
    state: PlaygroundUiState,
    wordCount: Int,
    estDurationSec: Int,
    onScriptChange: (String) -> Unit,
    onInsertCue: (String) -> Unit,
    onClearScript: () -> Unit,
    onUndoScript: () -> Unit,
    onAutoHumanize: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SCRIPT CONSOLE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (state.previousScriptBeforeHumanize != null) {
                        IconButton(
                            onClick = onUndoScript,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("undo_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo script rewrite",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (state.scriptText.isNotEmpty()) {
                        IconButton(
                            onClick = onClearScript,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("clear_script_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear script",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.scriptText,
                onValueChange = onScriptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 136.dp, max = 230.dp)
                    .testTag("script_input_field"),
                placeholder = {
                    Text(
                        text = stringResource(R.string.script_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )

            // Expressive inline cue chips (Cartesia-style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StudioInlineCues.forEach { cue ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onInsertCue(cue.tag) }
                            .testTag("cue_chip_${cue.label.lowercase().replace(" ", "_")}"),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "+ ${cue.tag}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.scriptText.length} chars • $wordCount words • ~${estDurationSec}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onAutoHumanize,
                    enabled = !state.isHumanizingScript && state.scriptText.isNotBlank(),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("auto_humanize_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (state.isHumanizingScript) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_humanizing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Auto-Humanize script",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_auto_humanize),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateSpeechCta(
    synthesisState: SynthesisUiState,
    onGenerate: () -> Unit
) {
    val isGenerating = synthesisState is SynthesisUiState.Generating
    val elapsedMs = (synthesisState as? SynthesisUiState.Generating)?.elapsedMs ?: 0L

    Button(
        onClick = onGenerate,
        enabled = !isGenerating,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("generate_speech_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        if (isGenerating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${stringResource(R.string.action_generating)}  ${elapsedMs}ms",
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Synthesize speech",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_generate_speech),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HumanizationRackCard(
    config: HumanizationConfig,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectEmotionPreset: (EmotionPreset) -> Unit,
    onEmotionAxisChange: (Float?, Float?, Float?, Float?, Float?) -> Unit,
    onToggleNaturalBreaths: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit
) {
    var languageMenuOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleExpanded)
                    .padding(vertical = 4.dp)
                    .testTag("humanization_rack_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Humanization controls",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HUMANIZATION & DELIVERY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%.2fx", config.speed)} • ${config.emotionPreset.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse controls" else "Expand controls",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Language selector & Natural Breathing toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { languageMenuOpen = true }
                                    .testTag("language_selector_button"),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Language",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = config.language,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = languageMenuOpen,
                                onDismissRequest = { languageMenuOpen = false }
                            ) {
                                SupportedStudioLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang) },
                                        onClick = {
                                            onLanguageChange(lang)
                                            languageMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Organic Breaths",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = config.naturalBreathsEnabled,
                                onCheckedChange = onToggleNaturalBreaths,
                                modifier = Modifier.testTag("natural_breaths_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // Speed & Volume gain sliders (Cartesia exact ranges: 0.6–1.5x speed, 0.5–2.0x volume)
                    StudioMetricSlider(
                        label = "Speed",
                        valueDisplay = String.format("%.2fx", config.speed),
                        value = config.speed,
                        valueRange = 0.6f..1.5f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onValueChange = onSpeedChange,
                        testTag = "speed_slider"
                    )

                    StudioMetricSlider(
                        label = "Volume Gain",
                        valueDisplay = "${(config.volume * 100).toInt()}%",
                        value = config.volume,
                        valueRange = 0.5f..2.0f,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onValueChange = onVolumeChange,
                        testTag = "volume_slider"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Text(
                        text = "EMOTION PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        EmotionPreset.entries.filter { it != EmotionPreset.CUSTOM }.forEach { preset ->
                            val selected = config.emotionPreset == preset
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectEmotionPreset(preset) },
                                modifier = Modifier.testTag("emotion_preset_${preset.name.lowercase()}"),
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                    Text(
                        text = "EMOTION MATRIX (FINE HUMANIZATION)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    StudioMetricSlider(
                        label = "Positivity / Warmth",
                        valueDisplay = "${(config.positivity * 100).toInt()}%",
                        value = config.positivity,
                        valueRange = 0f..1f,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onValueChange = { onEmotionAxisChange(it, null, null, null, null) },
                        testTag = "emotion_positivity_slider"
                    )
                    StudioMetricSlider(
                        label = "Curiosity",
                        valueDisplay = "${(config.curiosity * 100).toInt()}%",
                        value = config.curiosity,
                        valueRange = 0f..1f,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onValueChange = { onEmotionAxisChange(null, it, null, null, null) },
                        testTag = "emotion_curiosity_slider"
                    )
                    StudioMetricSlider(
                        label = "Surprise / Spark",
                        valueDisplay = "${(config.surprise * 100).toInt()}%",
                        value = config.surprise,
                        valueRange = 0f..1f,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onValueChange = { onEmotionAxisChange(null, null, it, null, null) },
                        testTag = "emotion_surprise_slider"
                    )
                    StudioMetricSlider(
                        label = "Melancholy / Softness",
                        valueDisplay = "${(config.sadness * 100).toInt()}%",
                        value = config.sadness,
                        valueRange = 0f..1f,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        onValueChange = { onEmotionAxisChange(null, null, null, it, null) },
                        testTag = "emotion_sadness_slider"
                    )
                    StudioMetricSlider(
                        label = "Expressive Intensity",
                        valueDisplay = "${(config.intensity * 100).toInt()}%",
                        value = config.intensity,
                        valueRange = 0f..1f,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onValueChange = { onEmotionAxisChange(null, null, null, null, it) },
                        testTag = "emotion_intensity_slider"
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioMetricSlider(
    label: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    testTag: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

@Composable
fun StudioErrorBanner(
    message: String,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("studio_error_banner"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .testTag("error_retry_button"),
                    color = MaterialTheme.colorScheme.error
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss error",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
