package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.StudioTab
import com.example.ui.components.DockedWaveformPlayer
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.StudioPlaygroundScreen
import com.example.ui.screens.VoiceCloneLabScreen
import com.example.ui.screens.VoiceLibraryScreen
import com.example.viewmodel.StudioViewModel

private data class NavItemSpec(
    val tab: StudioTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val StudioNavItems = listOf(
    NavItemSpec(StudioTab.STUDIO, "Studio", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    NavItemSpec(StudioTab.VOICES, "Voices", Icons.Filled.RecordVoiceOver, Icons.Outlined.RecordVoiceOver),
    NavItemSpec(StudioTab.CLONE, "Clone", Icons.Filled.Mic, Icons.Outlined.Mic),
    NavItemSpec(StudioTab.HISTORY, "History", Icons.Filled.History, Icons.Outlined.History)
)

@Composable
fun VoxCraftApp(
    viewModel: StudioViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val playgroundState by viewModel.playground.collectAsStateWithLifecycle()
    val cloneLabState by viewModel.cloneLab.collectAsStateWithLifecycle()
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val clonedVoices by viewModel.clonedVoices.collectAsStateWithLifecycle()
    val generations by viewModel.generations.collectAsStateWithLifecycle()
    val favoriteVoiceIds by viewModel.favoriteVoiceIds.collectAsStateWithLifecycle()
    val voiceSearch by viewModel.voiceSearchQuery.collectAsStateWithLifecycle()
    val voiceCategory by viewModel.selectedVoiceCategory.collectAsStateWithLifecycle()
    val previewingVoiceId by viewModel.previewingVoiceId.collectAsStateWithLifecycle()
    val historySearch by viewModel.historySearchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Back press on secondary tabs returns gracefully to Studio Playground
    BackHandler(enabled = selectedTab != StudioTab.STUDIO) {
        viewModel.selectTab(StudioTab.STUDIO)
    }

    fun launchShareAudio(path: String, title: String) {
        val shareIntent = viewModel.createShareIntent(path, title)
        if (shareIntent != null) {
            context.startActivity(Intent.createChooser(shareIntent, "Share WAV Audio"))
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AnimatedVisibility(
                            visible = playbackState.audioFilePath != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            DockedWaveformPlayer(
                                playback = playbackState,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeek = viewModel::seekPlayback,
                                onToggleLoop = viewModel::toggleLoop,
                                onShare = {
                                    playbackState.audioFilePath?.let { path ->
                                        launchShareAudio(path, playbackState.voiceName)
                                    }
                                },
                                onClose = viewModel::stopPlayback
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            StudioNavItems.forEach { item ->
                                val isSelected = selectedTab == item.tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { viewModel.selectTab(item.tab) },
                                    modifier = Modifier.testTag("nav_tab_${item.tab.name.lowercase()}"),
                                    icon = {
                                        Icon(
                                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        StudioNavItems.forEach { item ->
                            val isSelected = selectedTab == item.tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(item.tab) },
                                modifier = Modifier.testTag("nav_rail_${item.tab.name.lowercase()}"),
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            StudioTab.STUDIO -> StudioPlaygroundScreen(
                                state = playgroundState,
                                clonedVoicesCount = clonedVoices.size,
                                onScriptChange = viewModel::updateScript,
                                onInsertCue = viewModel::insertInlineCue,
                                onClearScript = viewModel::clearScript,
                                onUndoScript = viewModel::undoScriptChange,
                                onAutoHumanize = viewModel::autoHumanizeScript,
                                onOpenVoicePicker = { viewModel.selectTab(StudioTab.VOICES) },
                                onOpenCloneLab = { viewModel.selectTab(StudioTab.CLONE) },
                                onToggleHumanizationExpanded = viewModel::toggleHumanizationExpanded,
                                onSpeedChange = viewModel::updateSpeed,
                                onVolumeChange = viewModel::updateVolume,
                                onSelectEmotionPreset = viewModel::selectEmotionPreset,
                                onEmotionAxisChange = viewModel::updateEmotionAxis,
                                onToggleNaturalBreaths = viewModel::toggleNaturalBreaths,
                                onLanguageChange = viewModel::updateLanguage,
                                onGenerateSpeech = viewModel::generateSpeech,
                                onDismissError = viewModel::dismissSynthesisError
                            )

                            StudioTab.VOICES -> VoiceLibraryScreen(
                                selectedVoiceId = playgroundState.selectedVoice.id,
                                clonedVoices = clonedVoices,
                                favoriteVoiceIds = favoriteVoiceIds,
                                searchQuery = voiceSearch,
                                selectedCategory = voiceCategory,
                                previewingVoiceId = previewingVoiceId,
                                toStudioVoice = viewModel::toStudioVoice,
                                onSearchChange = viewModel::updateVoiceSearch,
                                onCategoryChange = viewModel::selectVoiceCategory,
                                onToggleFavorite = viewModel::toggleFavoriteVoice,
                                onPreviewVoice = viewModel::previewVoice,
                                onUseVoice = { voice -> viewModel.selectVoiceForStudio(voice, switchToStudio = true) },
                                onDeleteClone = viewModel::deleteClonedVoice,
                                onOpenCloneLab = { viewModel.selectTab(StudioTab.CLONE) }
                            )

                            StudioTab.CLONE -> VoiceCloneLabScreen(
                                state = cloneLabState,
                                recordingState = recordingState,
                                savedClones = clonedVoices,
                                onSelectInputMode = viewModel::setCloneInputMode,
                                onStartRecording = viewModel::startCloneRecording,
                                onStopRecording = viewModel::stopCloneRecording,
                                onImportAudioUri = viewModel::importCloneAudioUri,
                                onPlayCapturedSample = viewModel::playCapturedCloneSample,
                                onClearSample = viewModel::clearCapturedSample,
                                onAnalyzeSample = viewModel::analyzeCapturedSample,
                                onUpdateCloneFields = viewModel::updateEditableCloneFields,
                                onSaveClone = viewModel::saveClonedVoice,
                                onUseSavedClone = { clone -> viewModel.selectClonedVoiceForStudio(clone, switchToStudio = true) }
                            )

                            StudioTab.HISTORY -> HistoryScreen(
                                generations = generations,
                                playbackState = playbackState,
                                searchQuery = historySearch,
                                onSearchChange = viewModel::updateHistorySearch,
                                onPlayGeneration = viewModel::playGeneration,
                                onRemixGeneration = viewModel::remixGenerationInStudio,
                                onShareGeneration = { gen ->
                                    launchShareAudio(gen.audioFilePath, gen.voiceName)
                                },
                                onDeleteGeneration = viewModel::deleteGeneration,
                                onGoToStudio = { viewModel.selectTab(StudioTab.STUDIO) }
                            )
                        }
                    }

                    if (useNavRail) {
                        AnimatedVisibility(
                            visible = playbackState.audioFilePath != null,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut()
                        ) {
                            DockedWaveformPlayer(
                                playback = playbackState,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSeek = viewModel::seekPlayback,
                                onToggleLoop = viewModel::toggleLoop,
                                onShare = {
                                    playbackState.audioFilePath?.let { path ->
                                        launchShareAudio(path, playbackState.voiceName)
                                    }
                                },
                                onClose = viewModel::stopPlayback
                            )
                        }
                    }
                }
            }
        }
    }
}
