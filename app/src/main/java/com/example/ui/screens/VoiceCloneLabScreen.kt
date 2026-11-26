package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.audio.LiveRecordingState
import com.example.data.local.ClonedVoiceEntity
import com.example.model.AcousticDnaAnalysis
import com.example.model.CloneAnalysisState
import com.example.model.VoiceCatalog
import com.example.ui.components.LiveOscilloscopeWaveform
import com.example.viewmodel.CloneLabState
import com.example.viewmodel.StudioViewModel

@Composable
fun VoiceCloneLabScreen(
    state: CloneLabState,
    recordingState: LiveRecordingState,
    savedClones: List<ClonedVoiceEntity>,
    onSelectInputMode: (Boolean) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onImportAudioUri: (Uri) -> Unit,
    onPlayCapturedSample: () -> Unit,
    onClearSample: () -> Unit,
    onAnalyzeSample: () -> Unit,
    onUpdateCloneFields: (String?, String?, String?, String?) -> Unit,
    onSaveClone: () -> Unit,
    onUseSavedClone: (ClonedVoiceEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var micDeniedMessage by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micDeniedMessage = false
            onStartRecording()
        } else {
            micDeniedMessage = true
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportAudioUri(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Instant Voice Clone",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "10–60s vocal sample • Unlimited custom voice slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "∞ UNLOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Mode switcher: Record Live vs Import Audio
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ModeSegmentButton(
                    label = "Record Live",
                    icon = Icons.Default.Mic,
                    selected = state.inputModeRecord,
                    onClick = { onSelectInputMode(true) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clone_mode_record")
                )
                ModeSegmentButton(
                    label = "Import File",
                    icon = Icons.Default.UploadFile,
                    selected = !state.inputModeRecord,
                    onClick = { onSelectInputMode(false) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clone_mode_import")
                )
            }
        }

        if (micDeniedMessage) {
            StudioErrorBanner(
                message = stringResource(R.string.mic_permission_rationale),
                onDismiss = { micDeniedMessage = false }
            )
        }

        if (state.analysisState is CloneAnalysisState.Error) {
            StudioErrorBanner(
                message = state.analysisState.message,
                onRetry = if (state.capturedSample != null) onAnalyzeSample else null,
                onDismiss = onClearSample
            )
        }

        // Capture / Import Console Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                1.dp,
                if (recordingState.isRecording) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        recordingState.isRecording -> MaterialTheme.colorScheme.tertiary
                                        state.capturedSample != null -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outline
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                recordingState.isRecording -> "RECORDING LIVE PCM • 16kHz"
                                state.capturedSample != null -> state.sampleSourceLabel
                                state.inputModeRecord -> "READY TO CAPTURE (10–30s RECOMMENDED)"
                                else -> "SELECT WAV, MP3, M4A, OR OGG (<18MB)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (recordingState.isRecording) {
                        Text(
                            text = StudioViewModel.formatDuration(recordingState.elapsedMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                // Live Oscilloscope / Captured Waveform
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                ) {
                    LiveOscilloscopeWaveform(
                        amplitudes = when {
                            recordingState.isRecording -> recordingState.liveWaveform
                            state.capturedSample != null -> state.capturedSample.waveformPeaks
                            else -> List(48) { 0.08f }
                        },
                        isRecording = recordingState.isRecording,
                        height = 92.dp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                if (state.inputModeRecord) {
                    // Guided Studio Teleprompter Script
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "GUIDED VOICE CALIBRATION SCRIPT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.clone_teleprompter_prompt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (recordingState.isRecording) {
                                onStopRecording()
                            } else {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    onStartRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("clone_record_toggle_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (recordingState.isRecording) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            contentColor = if (recordingState.isRecording) {
                                MaterialTheme.colorScheme.onError
                            } else {
                                MaterialTheme.colorScheme.onPrimary
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (recordingState.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Record toggle",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (recordingState.isRecording) {
                                stringResource(R.string.action_stop_recording)
                            } else {
                                stringResource(R.string.action_record_sample)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("clone_import_file_button"),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AudioFile,
                            contentDescription = "Import audio file",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_import_audio),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Captured sample action row
                AnimatedVisibility(visible = state.capturedSample != null && !recordingState.isRecording) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onPlayCapturedSample,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("clone_play_sample_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play captured sample",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Listen", color = MaterialTheme.colorScheme.onSurface)
                            }

                            IconButton(
                                onClick = onClearSample,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("clone_clear_sample_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Discard captured sample",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        val isAnalyzing = state.analysisState is CloneAnalysisState.Analyzing
                        Button(
                            onClick = onAnalyzeSample,
                            enabled = !isAnalyzing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("clone_analyze_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Extracting Acoustic DNA…", style = MaterialTheme.typography.labelLarge)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Analyze and clone voice",
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.action_analyze_clone),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        // Extracted Acoustic DNA Blueprint & Save Form
        val readyAnalysis = (state.analysisState as? CloneAnalysisState.Ready)?.analysis
        AnimatedVisibility(visible = readyAnalysis != null) {
            readyAnalysis?.let { dna ->
                AcousticDnaBlueprintCard(
                    analysis = dna,
                    name = state.editableCloneName,
                    accent = state.editableAccent,
                    baseVoice = state.editableBaseVoice,
                    tagsText = state.editableTagsText,
                    onUpdateFields = onUpdateCloneFields,
                    onSave = onSaveClone
                )
            }
        }

        // Saved Clones Quick Roster
        if (savedClones.isNotEmpty()) {
            Text(
                text = "YOUR CLONED VOICES (${savedClones.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                savedClones.forEach { clone ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onUseSavedClone(clone) }
                            .testTag("saved_clone_quick_${clone.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Cloned voice",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = clone.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${clone.pitchDescriptor} • ${clone.accent} • Anchor: ${clone.baseGeminiVoice}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Load",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AcousticDnaBlueprintCard(
    analysis: AcousticDnaAnalysis,
    name: String,
    accent: String,
    baseVoice: String,
    tagsText: String,
    onUpdateFields: (String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit
) {
    var baseVoiceDropdownOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("acoustic_dna_blueprint_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACOUSTIC DNA EXTRACTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "${String.format("%.2fx", analysis.defaultSpeed)} cadence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = analysis.timbreSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnaTraitPill(label = "REGISTER", value = analysis.pitchDescriptor, modifier = Modifier.weight(1f))
                DnaTraitPill(label = "PACE", value = analysis.paceDescriptor, modifier = Modifier.weight(1f))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { onUpdateFields(it, null, null, null) },
                label = { Text("Clone Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clone_name_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = accent,
                    onValueChange = { onUpdateFields(null, it, null, null) },
                    label = { Text("Accent") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.secondary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = baseVoice,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Neural Anchor") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { baseVoiceDropdownOpen = true }) {
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Select neural anchor voice"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { baseVoiceDropdownOpen = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    DropdownMenu(
                        expanded = baseVoiceDropdownOpen,
                        onDismissRequest = { baseVoiceDropdownOpen = false }
                    ) {
                        VoiceCatalog.geminiVoiceNames.forEach { vName ->
                            DropdownMenuItem(
                                text = { Text(vName) },
                                onClick = {
                                    onUpdateFields(null, null, vName, null)
                                    baseVoiceDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = tagsText,
                onValueChange = { onUpdateFields(null, null, null, it) },
                label = { Text("Style Tags (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("clone_save_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Save cloned voice",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_save_clone),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DnaTraitPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
