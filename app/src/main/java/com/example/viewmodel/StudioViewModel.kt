package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiVoiceRepository
import com.example.audio.LiveRecordingState
import com.example.audio.LocalAudioEngine
import com.example.audio.SavedAudioClip
import com.example.data.StudioRepository
import com.example.data.local.ClonedVoiceEntity
import com.example.data.local.GenerationEntity
import com.example.data.local.StudioDatabase
import com.example.model.AudioPlaybackState
import com.example.model.CloneAnalysisState
import com.example.model.EmotionPreset
import com.example.model.HumanizationConfig
import com.example.model.StudioTab
import com.example.model.StudioVoice
import com.example.model.SynthesisUiState
import com.example.model.VoiceCatalog
import com.example.model.VoiceCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val TAG = "StudioViewModel"

data class PlaygroundUiState(
    val scriptText: String = "Hey there! [warmly] Welcome to VoxCraft Studio. Dial in my pacing, layer subtle emotions, or drop a quick [laughter] right into the line—and hear lifelike speech render in real time.",
    val previousScriptBeforeHumanize: String? = null,
    val selectedVoice: StudioVoice = VoiceCatalog.toStudioVoice(VoiceCatalog.presets.first()),
    val humanization: HumanizationConfig = HumanizationConfig(),
    val isHumanizingScript: Boolean = false,
    val synthesisState: SynthesisUiState = SynthesisUiState.Idle,
    val isHumanizationExpanded: Boolean = true,
    val lastGeneratedLatencyMs: Long? = null
)

data class CloneLabState(
    val inputModeRecord: Boolean = true,
    val capturedSample: SavedAudioClip? = null,
    val sampleSourceLabel: String = "",
    val analysisState: CloneAnalysisState = CloneAnalysisState.Idle,
    val editableCloneName: String = "",
    val editableAccent: String = "",
    val editableBaseVoice: String = "Kore",
    val editableTagsText: String = ""
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = LocalAudioEngine(application.applicationContext, viewModelScope)
    private val database = StudioDatabase.getInstance(application.applicationContext)
    private val studioRepository = StudioRepository(database, audioEngine)
    private val geminiVoiceRepository = GeminiVoiceRepository()

    private val _selectedTab = MutableStateFlow(StudioTab.STUDIO)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _playground = MutableStateFlow(PlaygroundUiState())
    val playground: StateFlow<PlaygroundUiState> = _playground.asStateFlow()

    private val _cloneLab = MutableStateFlow(CloneLabState())
    val cloneLab: StateFlow<CloneLabState> = _cloneLab.asStateFlow()

    private val _voiceSearchQuery = MutableStateFlow("")
    val voiceSearchQuery: StateFlow<String> = _voiceSearchQuery.asStateFlow()

    private val _selectedVoiceCategory = MutableStateFlow(VoiceCategory.ALL)
    val selectedVoiceCategory: StateFlow<VoiceCategory> = _selectedVoiceCategory.asStateFlow()

    private val _previewingVoiceId = MutableStateFlow<String?>(null)
    val previewingVoiceId: StateFlow<String?> = _previewingVoiceId.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    val playbackState: StateFlow<AudioPlaybackState> = audioEngine.playbackState
    val recordingState: StateFlow<LiveRecordingState> = audioEngine.recordingState

    val clonedVoices: StateFlow<List<ClonedVoiceEntity>> = studioRepository.clonedVoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val generations: StateFlow<List<GenerationEntity>> = studioRepository.generations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteVoiceIds: StateFlow<Set<String>> = studioRepository.favoriteVoiceIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val previewCache = mutableMapOf<String, SavedAudioClip>()
    private var latencyTickerJob: Job? = null

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    // --- Playground Actions ---

    fun updateScript(text: String) {
        _playground.update {
            it.copy(
                scriptText = text,
                synthesisState = if (it.synthesisState is SynthesisUiState.Error) SynthesisUiState.Idle else it.synthesisState
            )
        }
    }

    fun clearScript() {
        _playground.update {
            it.copy(
                previousScriptBeforeHumanize = it.scriptText.takeIf { s -> s.isNotBlank() },
                scriptText = ""
            )
        }
    }

    fun insertInlineCue(cueTag: String) {
        _playground.update { state ->
            val current = state.scriptText
            val separator = if (current.isEmpty() || current.endsWith(" ") || current.endsWith("\n")) "" else " "
            state.copy(scriptText = "$current$separator$cueTag ")
        }
    }

    fun undoScriptChange() {
        _playground.update { state ->
            val prev = state.previousScriptBeforeHumanize ?: return@update state
            state.copy(scriptText = prev, previousScriptBeforeHumanize = null)
        }
    }

    fun selectVoiceForStudio(voice: StudioVoice, switchToStudio: Boolean = true) {
        _playground.update { state ->
            state.copy(
                selectedVoice = voice,
                humanization = if (voice.isCloned) {
                    state.humanization.copy(speed = voice.defaultSpeed.coerceIn(0.6f, 1.5f))
                } else {
                    state.humanization
                }
            )
        }
        if (switchToStudio) {
            _selectedTab.value = StudioTab.STUDIO
        }
        _snackbarEvents.tryEmit("Active voice: ${voice.displayName}")
    }

    fun selectClonedVoiceForStudio(clone: ClonedVoiceEntity, switchToStudio: Boolean = true) {
        selectVoiceForStudio(studioRepository.toStudioVoice(clone), switchToStudio)
    }

    fun toggleHumanizationExpanded() {
        _playground.update { it.copy(isHumanizationExpanded = !it.isHumanizationExpanded) }
    }

    fun updateSpeed(speed: Float) {
        _playground.update {
            it.copy(humanization = it.humanization.copy(speed = speed.coerceIn(0.6f, 1.5f)))
        }
    }

    fun updateVolume(volume: Float) {
        _playground.update {
            it.copy(humanization = it.humanization.copy(volume = volume.coerceIn(0.5f, 2.0f)))
        }
    }

    fun selectEmotionPreset(preset: EmotionPreset) {
        _playground.update {
            it.copy(
                humanization = it.humanization.copy(
                    emotionPreset = preset,
                    positivity = preset.positivity,
                    curiosity = preset.curiosity,
                    surprise = preset.surprise,
                    sadness = preset.sadness,
                    intensity = preset.intensity
                )
            )
        }
    }

    fun updateEmotionAxis(
        positivity: Float? = null,
        curiosity: Float? = null,
        surprise: Float? = null,
        sadness: Float? = null,
        intensity: Float? = null
    ) {
        _playground.update { state ->
            val h = state.humanization
            state.copy(
                humanization = h.copy(
                    emotionPreset = EmotionPreset.CUSTOM,
                    positivity = positivity ?: h.positivity,
                    curiosity = curiosity ?: h.curiosity,
                    surprise = surprise ?: h.surprise,
                    sadness = sadness ?: h.sadness,
                    intensity = intensity ?: h.intensity
                )
            )
        }
    }

    fun toggleNaturalBreaths(enabled: Boolean) {
        _playground.update {
            it.copy(humanization = it.humanization.copy(naturalBreathsEnabled = enabled))
        }
    }

    fun updateLanguage(language: String) {
        _playground.update {
            it.copy(humanization = it.humanization.copy(language = language))
        }
    }

    fun dismissSynthesisError() {
        _playground.update {
            if (it.synthesisState is SynthesisUiState.Error) {
                it.copy(synthesisState = SynthesisUiState.Idle)
            } else {
                it
            }
        }
    }

    fun autoHumanizeScript() {
        val current = _playground.value
        val text = current.scriptText.trim()
        if (text.isBlank()) {
            _playground.update {
                it.copy(synthesisState = SynthesisUiState.Error("Enter some script text first to auto-humanize."))
            }
            return
        }
        if (current.isHumanizingScript) return

        viewModelScope.launch {
            _playground.update { it.copy(isHumanizingScript = true) }
            try {
                val enhanced = geminiVoiceRepository.humanizeScript(text, current.humanization)
                _playground.update {
                    it.copy(
                        previousScriptBeforeHumanize = text,
                        scriptText = enhanced,
                        isHumanizingScript = false
                    )
                }
                _snackbarEvents.tryEmit("Script humanized for natural delivery")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auto-humanize error", e)
                _playground.update {
                    it.copy(
                        isHumanizingScript = false,
                        synthesisState = SynthesisUiState.Error(e.message ?: "Failed to humanize script.")
                    )
                }
            }
        }
    }

    fun generateSpeech() {
        val state = _playground.value
        val script = state.scriptText.trim()
        if (script.isBlank()) {
            _playground.update {
                it.copy(synthesisState = SynthesisUiState.Error("Enter some text in the script editor before generating speech."))
            }
            return
        }
        if (state.synthesisState is SynthesisUiState.Generating) return

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _playground.update { it.copy(synthesisState = SynthesisUiState.Generating(0L)) }

            latencyTickerJob?.cancel()
            latencyTickerJob = viewModelScope.launch {
                while (isActive) {
                    delay(80L)
                    _playground.update { cur ->
                        if (cur.synthesisState is SynthesisUiState.Generating) {
                            cur.copy(synthesisState = SynthesisUiState.Generating(System.currentTimeMillis() - startTime))
                        } else cur
                    }
                }
            }

            try {
                val payload = geminiVoiceRepository.synthesizeSpeech(
                    script = script,
                    voice = state.selectedVoice,
                    config = state.humanization
                )
                val savedClip = audioEngine.saveSynthesizedAudio(
                    audioBytes = payload.bytes,
                    mimeType = payload.mimeType,
                    volumeGain = state.humanization.volume
                )

                val generationId = UUID.randomUUID().toString()
                val entity = GenerationEntity(
                    id = generationId,
                    scriptText = script,
                    voiceId = state.selectedVoice.id,
                    voiceName = state.selectedVoice.displayName,
                    isClonedVoice = state.selectedVoice.isCloned,
                    emotionPreset = state.humanization.emotionPreset.label,
                    speed = state.humanization.speed,
                    volume = state.humanization.volume,
                    language = state.humanization.language,
                    audioFilePath = savedClip.file.absolutePath,
                    durationMs = savedClip.durationMs,
                    latencyMs = payload.latencyMs,
                    waveformPeaksCsv = StudioRepository.encodePeaks(savedClip.waveformPeaks)
                )
                studioRepository.saveGeneration(entity)

                latencyTickerJob?.cancel()
                _playground.update {
                    it.copy(
                        synthesisState = SynthesisUiState.Idle,
                        lastGeneratedLatencyMs = payload.latencyMs
                    )
                }

                audioEngine.playTrack(
                    trackId = generationId,
                    title = script.take(64),
                    voiceName = state.selectedVoice.displayName,
                    isClonedVoice = state.selectedVoice.isCloned,
                    audioFilePath = savedClip.file.absolutePath,
                    waveformPeaks = savedClip.waveformPeaks,
                    latencyMs = payload.latencyMs
                )
            } catch (e: CancellationException) {
                latencyTickerJob?.cancel()
                throw e
            } catch (e: Exception) {
                latencyTickerJob?.cancel()
                Log.e(TAG, "Speech synthesis error", e)
                _playground.update {
                    it.copy(synthesisState = SynthesisUiState.Error(e.message ?: "Speech generation failed."))
                }
            }
        }
    }

    // --- Voice Library Actions ---

    fun updateVoiceSearch(query: String) {
        _voiceSearchQuery.value = query
    }

    fun selectVoiceCategory(category: VoiceCategory) {
        _selectedVoiceCategory.value = category
    }

    fun toggleFavoriteVoice(voiceId: String) {
        viewModelScope.launch {
            val isFav = favoriteVoiceIds.value.contains(voiceId)
            studioRepository.toggleFavorite(voiceId, isFav)
        }
    }

    fun previewVoice(voice: StudioVoice, sampleText: String) {
        if (_previewingVoiceId.value != null) return

        // Play cached preview immediately if available
        val cached = previewCache[voice.id]
        if (cached != null && cached.file.exists()) {
            audioEngine.playTrack(
                trackId = "preview_${voice.id}",
                title = "Voice Preview",
                voiceName = voice.displayName,
                isClonedVoice = voice.isCloned,
                audioFilePath = cached.file.absolutePath,
                waveformPeaks = cached.waveformPeaks
            )
            return
        }

        // For cloned voices, if original sample audio exists we still synthesize a fresh AI preview
        viewModelScope.launch {
            _previewingVoiceId.value = voice.id
            try {
                val payload = geminiVoiceRepository.synthesizeSpeech(
                    script = sampleText,
                    voice = voice,
                    config = HumanizationConfig(
                        speed = voice.defaultSpeed,
                        emotionPreset = EmotionPreset.CONVERSATIONAL
                    )
                )
                val saved = audioEngine.saveSynthesizedAudio(
                    audioBytes = payload.bytes,
                    mimeType = payload.mimeType
                )
                previewCache[voice.id] = saved
                audioEngine.playTrack(
                    trackId = "preview_${voice.id}",
                    title = "Voice Preview",
                    voiceName = voice.displayName,
                    isClonedVoice = voice.isCloned,
                    audioFilePath = saved.file.absolutePath,
                    waveformPeaks = saved.waveformPeaks,
                    latencyMs = payload.latencyMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Voice preview failed", e)
                _snackbarEvents.tryEmit(e.message ?: "Preview failed. Please try again.")
            } finally {
                _previewingVoiceId.value = null
            }
        }
    }

    fun deleteClonedVoice(entity: ClonedVoiceEntity) {
        viewModelScope.launch {
            studioRepository.deleteClonedVoice(entity)
            if (_playground.value.selectedVoice.id == entity.id) {
                _playground.update {
                    it.copy(selectedVoice = VoiceCatalog.toStudioVoice(VoiceCatalog.presets.first()))
                }
            }
            _snackbarEvents.tryEmit("Deleted clone \"${entity.name}\"")
        }
    }

    // --- Clone Lab Actions ---

    fun setCloneInputMode(recordMode: Boolean) {
        _cloneLab.update { it.copy(inputModeRecord = recordMode) }
    }

    fun startCloneRecording() {
        _cloneLab.update { it.copy(analysisState = CloneAnalysisState.Idle) }
        audioEngine.startRecording { errorMsg ->
            _cloneLab.update { it.copy(analysisState = CloneAnalysisState.Error(errorMsg)) }
        }
    }

    fun stopCloneRecording() {
        viewModelScope.launch {
            val saved = audioEngine.stopRecordingAndSave()
            if (saved == null) {
                _cloneLab.update {
                    it.copy(
                        analysisState = CloneAnalysisState.Error(
                            "Recording was too short. Please record at least 3 seconds of clear speech."
                        )
                    )
                }
            } else {
                _cloneLab.update {
                    it.copy(
                        capturedSample = saved,
                        sampleSourceLabel = "Mic Recording • ${formatDuration(saved.durationMs)}",
                        analysisState = CloneAnalysisState.Idle
                    )
                }
                _snackbarEvents.tryEmit("Voice sample captured (${formatDuration(saved.durationMs)})")
            }
        }
    }

    fun importCloneAudioUri(uri: Uri) {
        viewModelScope.launch {
            val result = audioEngine.importAudioUri(uri)
            result.onSuccess { saved ->
                _cloneLab.update {
                    it.copy(
                        capturedSample = saved,
                        sampleSourceLabel = "Imported Audio • ${formatDuration(saved.durationMs)}",
                        analysisState = CloneAnalysisState.Idle
                    )
                }
                _snackbarEvents.tryEmit("Audio sample imported")
            }.onFailure { err ->
                _cloneLab.update {
                    it.copy(
                        analysisState = CloneAnalysisState.Error(
                            err.message ?: "Unable to import selected audio file."
                        )
                    )
                }
            }
        }
    }

    fun playCapturedCloneSample() {
        val sample = _cloneLab.value.capturedSample ?: return
        audioEngine.playTrack(
            trackId = "clone_sample_${sample.file.name}",
            title = "Voice Sample Preview",
            voiceName = _cloneLab.value.editableCloneName.ifBlank { "Captured Sample" },
            isClonedVoice = true,
            audioFilePath = sample.file.absolutePath,
            waveformPeaks = sample.waveformPeaks
        )
    }

    fun analyzeCapturedSample() {
        val sample = _cloneLab.value.capturedSample
        if (sample == null || !sample.file.exists()) {
            _cloneLab.update {
                it.copy(analysisState = CloneAnalysisState.Error("Record or import a voice sample first."))
            }
            return
        }
        if (_cloneLab.value.analysisState is CloneAnalysisState.Analyzing) return

        viewModelScope.launch {
            _cloneLab.update { it.copy(analysisState = CloneAnalysisState.Analyzing) }
            try {
                val bytes = withContext(Dispatchers.IO) { sample.file.readBytes() }
                val dna = geminiVoiceRepository.analyzeVoiceSample(bytes, sample.mimeType)
                _cloneLab.update {
                    it.copy(
                        analysisState = CloneAnalysisState.Ready(dna),
                        editableCloneName = dna.suggestedName,
                        editableAccent = dna.accent,
                        editableBaseVoice = dna.closestBaseVoice,
                        editableTagsText = dna.tags.joinToString(", ")
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clone analysis error", e)
                _cloneLab.update {
                    it.copy(analysisState = CloneAnalysisState.Error(e.message ?: "Voice profiling failed."))
                }
            }
        }
    }

    fun updateEditableCloneFields(
        name: String? = null,
        accent: String? = null,
        baseVoice: String? = null,
        tagsText: String? = null
    ) {
        _cloneLab.update { state ->
            state.copy(
                editableCloneName = name ?: state.editableCloneName,
                editableAccent = accent ?: state.editableAccent,
                editableBaseVoice = baseVoice ?: state.editableBaseVoice,
                editableTagsText = tagsText ?: state.editableTagsText
            )
        }
    }

    fun saveClonedVoice() {
        val state = _cloneLab.value
        val sample = state.capturedSample ?: return
        val analysis = (state.analysisState as? CloneAnalysisState.Ready)?.analysis ?: return
        val name = state.editableCloneName.trim().ifBlank { analysis.suggestedName }

        viewModelScope.launch {
            val entity = ClonedVoiceEntity(
                id = "clone_${UUID.randomUUID()}",
                name = name,
                baseGeminiVoice = state.editableBaseVoice.ifBlank { analysis.closestBaseVoice },
                acousticDnaPrompt = analysis.acousticDnaPrompt,
                pitchDescriptor = analysis.pitchDescriptor,
                paceDescriptor = analysis.paceDescriptor,
                timbreSummary = analysis.timbreSummary,
                accent = state.editableAccent.trim().ifBlank { analysis.accent },
                tagsCsv = state.editableTagsText.trim().ifBlank { analysis.tags.joinToString(", ") },
                defaultSpeed = analysis.defaultSpeed,
                sampleAudioPath = sample.file.absolutePath,
                waveformPeaksCsv = StudioRepository.encodePeaks(sample.waveformPeaks)
            )
            studioRepository.saveClonedVoice(entity)
            selectClonedVoiceForStudio(entity, switchToStudio = true)

            // Reset lab for next unlimited clone
            _cloneLab.value = CloneLabState(inputModeRecord = state.inputModeRecord)
            _snackbarEvents.tryEmit("Saved & loaded clone \"$name\" in Studio")
        }
    }

    fun clearCapturedSample() {
        val sample = _cloneLab.value.capturedSample
        if (sample != null) {
            audioEngine.deleteAudioFile(sample.file.absolutePath)
        }
        _cloneLab.update {
            it.copy(
                capturedSample = null,
                sampleSourceLabel = "",
                analysisState = CloneAnalysisState.Idle
            )
        }
    }

    // --- History Actions ---

    fun updateHistorySearch(query: String) {
        _historySearchQuery.value = query
    }

    fun playGeneration(entity: GenerationEntity) {
        val file = File(entity.audioFilePath)
        if (!file.exists()) {
            _snackbarEvents.tryEmit("Audio file is no longer available on device.")
            return
        }
        audioEngine.playTrack(
            trackId = entity.id,
            title = entity.scriptText.take(64),
            voiceName = entity.voiceName,
            isClonedVoice = entity.isClonedVoice,
            audioFilePath = entity.audioFilePath,
            waveformPeaks = StudioRepository.parsePeaks(entity.waveformPeaksCsv),
            latencyMs = entity.latencyMs
        )
    }

    fun remixGenerationInStudio(entity: GenerationEntity) {
        val matchedClone = clonedVoices.value.find { it.id == entity.voiceId }
        val matchedPreset = VoiceCatalog.presets.find { it.id == entity.voiceId }
        val voice = when {
            matchedClone != null -> studioRepository.toStudioVoice(matchedClone)
            matchedPreset != null -> VoiceCatalog.toStudioVoice(matchedPreset)
            else -> _playground.value.selectedVoice
        }
        val preset = EmotionPreset.entries.find { it.label == entity.emotionPreset }
            ?: EmotionPreset.CONVERSATIONAL

        _playground.update { state ->
            state.copy(
                scriptText = entity.scriptText,
                selectedVoice = voice,
                humanization = state.humanization.copy(
                    speed = entity.speed.coerceIn(0.6f, 1.5f),
                    volume = entity.volume.coerceIn(0.5f, 2.0f),
                    emotionPreset = preset,
                    positivity = preset.positivity,
                    curiosity = preset.curiosity,
                    surprise = preset.surprise,
                    sadness = preset.sadness,
                    intensity = preset.intensity,
                    language = entity.language
                )
            )
        }
        _selectedTab.value = StudioTab.STUDIO
        _snackbarEvents.tryEmit("Loaded take into Studio Playground")
    }

    fun deleteGeneration(entity: GenerationEntity) {
        viewModelScope.launch {
            studioRepository.deleteGeneration(entity)
            _snackbarEvents.tryEmit("Deleted audio clip")
        }
    }

    // --- Docked Player Controls ---

    fun togglePlayPause() = audioEngine.togglePlayPause()

    fun seekPlayback(fraction: Float) = audioEngine.seekToFraction(fraction)

    fun toggleLoop() = audioEngine.toggleLoop()

    fun stopPlayback() = audioEngine.stopPlayback()

    fun createShareIntent(audioFilePath: String, title: String): Intent? =
        audioEngine.buildShareIntent(audioFilePath, title)

    fun toStudioVoice(clone: ClonedVoiceEntity): StudioVoice = studioRepository.toStudioVoice(clone)

    override fun onCleared() {
        super.onCleared()
        latencyTickerJob?.cancel()
        audioEngine.release()
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val mins = totalSeconds / 60L
            val secs = totalSeconds % 60L
            return String.format("%02d:%02d", mins, secs)
        }
    }
}
