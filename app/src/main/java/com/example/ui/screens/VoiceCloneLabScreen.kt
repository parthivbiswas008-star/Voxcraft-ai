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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.model.AutoDetectLanguageOption
import com.example.model.CloneAnalysisState
import com.example.model.StudioLanguage
import com.example.model.SupportedStudioLanguages
import com.example.model.VoiceCatalog
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.components.LiveOscilloscopeWaveform
import com.example.ui.components.StudioGlassCard
import com.example.viewmodel.CloneLabState
import com.example.viewmodel.StudioViewModel

@Composable
fun VoiceCloneLabScreen(
    state: CloneLabState,
    recordingState: LiveRecordingState,
    savedClones: List<ClonedVoiceEntity>,
    onSelectInputMode: (Boolean) -> Unit,
    onSelectTargetLanguage: (StudioLanguage) -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onImportAudioUri: (Uri) -> Unit,
    onPlayCapturedSample: () -> Unit,
    onClearSample: () -> Unit,
    onAnalyzeSample: () -> Unit,
    onUpdateCloneFields: (String?, String?, String?, StudioLanguage?, String?) -> Unit,
    onTestAnalyzedClone: () -> Unit,
    onSaveClone: () -> Unit,
    onUseSavedClone: (ClonedVoiceEntity) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var micDeniedMessage by remember { mutableStateOf(false) }
    var showTargetLangDialog by remember { mutableStateOf(false) }
    var showBlueprintLangDialog by remember { mutableStateOf(false) }

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
                    text = "Auto-detects accent & timbre • Set any target language",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "∞ FREE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Studio settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Target Language Selector (with country flags + Auto-Detect Accent/Language)
        StudioGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showTargetLangDialog = true }
                .testTag("clone_language_selector"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = state.targetLanguage.flag,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CLONE LANGUAGE & ACCENT LOCK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = state.targetLanguage.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Accent is auto-detected from your audio sample",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Choose clone language",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Mode switcher: Record Live vs Import Audio
        StudioGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
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
        StudioGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            accentBorder = if (recordingState.isRecording) MaterialTheme.colorScheme.tertiary else null
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
                                recordingState.isRecording -> "RECORDING LIVE PCM • UNLIMITED"
                                state.capturedSample != null -> state.sampleSourceLabel
                                state.inputModeRecord -> "READY TO CAPTURE • UNLIMITED DURATION"
                                else -> "ANY AUDIO FILE (WAV, MP3, M4A, OGG • ANY LENGTH)"
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
                    val calibrationPrompt = when {
                        state.targetLanguage.code.startsWith("bn") ->
                            "“নমস্কার! আমি VoxCraft Studio-তে আমার নিজের ভয়েস ক্লোন টেস্ট করছি। খেয়াল করুন আমার স্বাভাবিক কথা বলার ভঙ্গি, অ্যাকসেন্ট আর আবেগ—সবকিছু একদম আসল শোনাবে।”"
                        state.targetLanguage.code.startsWith("hi") ->
                            "“नमस्ते! मैं VoxCraft Studio में अपनी आवाज़ क्लोन कर रहा हूँ। मेरी प्राकृतिक लय, लहजे और अंदाज़ को ध्यान से सुनिए—हर शब्द बिल्कुल असली लगेगा।”"
                        else -> stringResource(R.string.clone_teleprompter_prompt)
                    }

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
                                text = "GUIDED VOICE CALIBRATION SCRIPT (${state.targetLanguage.flag})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = calibrationPrompt,
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
                                Text("Listen Sample", color = MaterialTheme.colorScheme.onSurface)
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

                        val analyzingState = state.analysisState as? CloneAnalysisState.Analyzing
                        val isAnalyzing = analyzingState != null
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
                            if (analyzingState != null) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = analyzingState.stage,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

                        AnimatedVisibility(visible = analyzingState != null) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "SMART SPEECH EXTRACTOR ACTIVE • AUTO-FALLBACK ENABLED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
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
                    selectedLanguage = state.editableLanguage,
                    tagsText = state.editableTagsText,
                    isTestingClone = state.isTestingClone,
                    onUpdateFields = onUpdateCloneFields,
                    onPickLanguage = { showBlueprintLangDialog = true },
                    onTestClone = onTestAnalyzedClone,
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
                    StudioGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onUseSavedClone(clone) }
                            .testTag("saved_clone_quick_${clone.id}"),
                        shape = RoundedCornerShape(14.dp),
                        accentBorder = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
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
                                    text = "${clone.countryFlag} ${clone.name}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${clone.languageName} • ${clone.accent} • ${clone.pitchDescriptor}",
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

    if (showTargetLangDialog) {
        LanguagePickerDialog(
            title = "Clone Target Language",
            languages = listOf(AutoDetectLanguageOption) + SupportedStudioLanguages,
            selectedCode = state.targetLanguage.code,
            onSelect = onSelectTargetLanguage,
            onDismiss = { showTargetLangDialog = false }
        )
    }

    if (showBlueprintLangDialog) {
        LanguagePickerDialog(
            title = "Voice Language",
            languages = SupportedStudioLanguages,
            selectedCode = state.editableLanguage.code,
            onSelect = { lang -> onUpdateCloneFields(null, null, null, lang, null) },
            onDismiss = { showBlueprintLangDialog = false }
        )
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    icon: ImageVector,
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
    selectedLanguage: StudioLanguage,
    tagsText: String,
    isTestingClone: Boolean,
    onUpdateFields: (String?, String?, String?, StudioLanguage?, String?) -> Unit,
    onPickLanguage: () -> Unit,
    onTestClone: () -> Unit,
    onSave: () -> Unit
) {
    var baseVoiceDropdownOpen by remember { mutableStateOf(false) }

    StudioGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("acoustic_dna_blueprint_card"),
        shape = RoundedCornerShape(20.dp),
        accentBorder = MaterialTheme.colorScheme.secondary
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
                    text = "ACCENT & ACOUSTIC DNA LOCKED",
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
                DnaTraitPill(label = "AUTO-DETECTED ACCENT", value = analysis.detectedAccent, modifier = Modifier.weight(1f))
                DnaTraitPill(label = "DETECTED TONGUE", value = analysis.detectedLanguage, modifier = Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DnaTraitPill(label = "REGISTER", value = analysis.pitchDescriptor, modifier = Modifier.weight(1f))
                DnaTraitPill(label = "PACE", value = analysis.paceDescriptor, modifier = Modifier.weight(1f))
            }

            OutlinedTextField(
                value = name,
                onValueChange = { onUpdateFields(it, null, null, null, null) },
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

            // Clone language selector with flag
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onPickLanguage),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedLanguage.flag, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "OUTPUT LANGUAGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedLanguage.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Change clone language",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = accent,
                    onValueChange = { onUpdateFields(null, it, null, null, null) },
                    label = { Text("Accent Lock") },
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
                                    onUpdateFields(null, null, vName, null, null)
                                    baseVoiceDropdownOpen = false
                                }
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = tagsText,
                onValueChange = { onUpdateFields(null, null, null, null, it) },
                label = { Text("Style Tags (comma-separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                OutlinedButton(
                    onClick = onTestClone,
                    enabled = !isTestingClone,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("clone_test_preview_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                ) {
                    if (isTestingClone) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing…", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Test cloned voice",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Clone", color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(50.dp)
                        .testTag("clone_save_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save cloned voice",
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_save_clone),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
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
