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
import com.example.data.AppSettings
import com.example.data.PerformanceMode
import com.example.data.SettingsRepository
import com.example.data.StudioRepository
import com.example.data.local.ClonedVoiceEntity
import com.example.data.local.GenerationEntity
import com.example.data.local.StudioDatabase
import com.example.model.AudioPlaybackState
import com.example.model.AutoDetectLanguageOption
import com.example.model.CloneAnalysisState
import com.example.model.EmotionPreset
import com.example.model.HumanizationConfig
import com.example.model.StudioLanguage
import com.example.model.StudioTab
import com.example.model.StudioVoice
import com.example.model.SupportedStudioLanguages
import com.example.model.SynthesisUiState
import com.example.model.VoiceCatalog
import com.example.model.VoiceCategory
import com.example.model.findLanguageByNameOrCode
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
    val scriptText: String = "[happy] Hey there! Welcome to VoxCraft Studio. [whisper] Try typing any emotion inside brackets, or switch to Hindi or Bengali—[excited] and hear lifelike speech render in real time!",
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
    val targetLanguage: StudioLanguage = AutoDetectLanguageOption,
    val capturedSample: SavedAudioClip? = null,
    val sampleSourceLabel: String = "",
    val analysisState: CloneAnalysisState = CloneAnalysisState.Idle,
    val editableCloneName: String = "",
    val editableAccent: String = "",
    val editableBaseVoice: String = "Kore",
    val editableLanguage: StudioLanguage = SupportedStudioLanguages.first(),
    val editableTagsText: String = "",
    val isTestingClone: Boolean = false
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioEngine = LocalAudioEngine(application.applicationContext, viewModelScope)
    private val database = StudioDatabase.getInstance(application.applicationContext)
    private val studioRepository = StudioRepository(database, audioEngine)
    private val settingsRepository = SettingsRepository(application.applicationContext)
    private val geminiVoiceRepository = GeminiVoiceRepository()

    val appSettings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _selectedTab = MutableStateFlow(StudioTab.STUDIO)
    val selectedTab: StateFlow<StudioTab> = _selectedTab.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _playground = MutableStateFlow(PlaygroundUiState())
    val playground: StateFlow<PlaygroundUiState> = _playground.asStateFlow()

    private val _cloneLab = MutableStateFlow(CloneLabState())
    val cloneLab: StateFlow<CloneLabState> = _cloneLab.asStateFlow()

    private val _voiceSearchQuery = MutableStateFlow("")
    val voiceSearchQuery: StateFlow<String> = _voiceSearchQuery.asStateFlow()

    private val _selectedVoiceCategory = MutableStateFlow(VoiceCategory.ALL)
    val selectedVoiceCategory: StateFlow<VoiceCategory> = _selectedVoiceCategory.asStateFlow()

    private val _selectedVoiceLanguageCode = MutableStateFlow("ALL")
    val selectedVoiceLanguageCode: StateFlow<String> = _selectedVoiceLanguageCode.asStateFlow()

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

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 6)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val previewCache = mutableMapOf<String, SavedAudioClip>()
    private var latencyTickerJob: Job? = null

    init {
        viewModelScope.launch {
            appSettings.collect { settings ->
                val pollMs = when (settings.performanceMode) {
                    PerformanceMode.ULTRA_LOW_LATENCY -> 28L
                    PerformanceMode.HIGH_PERFORMANCE -> 40L
                    PerformanceMode.BALANCED -> 60L
                }
                audioEngine.setPollingIntervalMs(pollMs)
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        _selectedTab.value = tab
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    // --- Settings Actions ---

    fun setPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch {
            settingsRepository.setPerformanceMode(mode)
        }
    }

    fun setForce144Hz(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setForce144Hz(enabled)
        }
    }

    fun setAmoledDark(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAmoledDark(enabled)
        }
    }

    fun setLiquidGlass(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setLiquidGlass(enabled)
        }
    }

    fun setGlassBlurRadius(radiusDp: Float) {
        viewModelScope.launch {
            settingsRepository.setGlassBlurRadius(radiusDp)
        }
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
        val voiceLang = findLanguageByNameOrCode(voice.languageCode)
        _playground.update { state ->
            state.copy(
                selectedVoice = voice,
                humanization = state.humanization.copy(
                    speed = if (voice.isCloned) voice.defaultSpeed.coerceIn(0.6f, 1.5f) else state.humanization.speed,
                    languageCode = voiceLang.code,
                    language = voiceLang.name,
                    countryFlag = voiceLang.flag
                )
            )
        }
        if (switchToStudio) {
            _selectedTab.value = StudioTab.STUDIO
        }
        _snackbarEvents.tryEmit("Active voice: ${voice.countryFlag} ${voice.displayName}")
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

    fun updateLanguage(language: StudioLanguage) {
        _playground.update {
            it.copy(
                humanization = it.humanization.copy(
                    languageCode = language.code,
                    language = language.name,
                    countryFlag = language.flag
                )
            )
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
                _snackbarEvents.tryEmit("Script humanized with expressive cadence")
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
                    delay(60L)
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
                    config = state.humanization,
                    performanceMode = appSettings.value.performanceMode
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
                    countryFlag = state.humanization.countryFlag,
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
                    voiceName = "${state.humanization.countryFlag} ${state.selectedVoice.displayName}",
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

    fun selectVoiceLanguageFilter(languageCode: String) {
        _selectedVoiceLanguageCode.value = languageCode
    }

    fun toggleFavoriteVoice(voiceId: String) {
        viewModelScope.launch {
            val isFav = favoriteVoiceIds.value.contains(voiceId)
            studioRepository.toggleFavorite(voiceId, isFav)
        }
    }

    fun previewVoice(voice: StudioVoice, sampleText: String) {
        if (_previewingVoiceId.value != null) return

        val cached = previewCache[voice.id]
        if (cached != null && cached.file.exists()) {
            audioEngine.playTrack(
                trackId = "preview_${voice.id}",
                title = "Voice Preview • ${voice.countryFlag} ${voice.languageName}",
                voiceName = voice.displayName,
                isClonedVoice = voice.isCloned,
                audioFilePath = cached.file.absolutePath,
                waveformPeaks = cached.waveformPeaks
            )
            return
        }

        viewModelScope.launch {
            _previewingVoiceId.value = voice.id
            try {
                val payload = geminiVoiceRepository.synthesizeSpeech(
                    script = sampleText,
                    voice = voice,
                    config = HumanizationConfig(
                        speed = voice.defaultSpeed,
                        emotionPreset = EmotionPreset.CONVERSATIONAL,
                        languageCode = voice.languageCode,
                        language = voice.languageName,
                        countryFlag = voice.countryFlag
                    ),
                    performanceMode = appSettings.value.performanceMode
                )
                val saved = audioEngine.saveSynthesizedAudio(
                    audioBytes = payload.bytes,
                    mimeType = payload.mimeType
                )
                previewCache[voice.id] = saved
                audioEngine.playTrack(
                    trackId = "preview_${voice.id}",
                    title = "Voice Preview • ${voice.countryFlag} ${voice.languageName}",
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

    fun setCloneTargetLanguage(language: StudioLanguage) {
        _cloneLab.update { it.copy(targetLanguage = language) }
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
                            "Recording was too short. Record at least 3 seconds of clear speech."
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
                _snackbarEvents.tryEmit("Audio sample imported for cloning")
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
            title = "Original Voice Sample",
            voiceName = _cloneLab.value.editableCloneName.ifBlank { "Captured Sample" },
            isClonedVoice = true,
            audioFilePath = sample.file.absolutePath,
            waveformPeaks = sample.waveformPeaks
        )
    }

    fun analyzeCapturedSample() {
        val state = _cloneLab.value
        val sample = state.capturedSample
        if (sample == null || !sample.file.exists()) {
            _cloneLab.update {
                it.copy(analysisState = CloneAnalysisState.Error("Record or import a voice sample first."))
            }
            return
        }
        if (state.analysisState is CloneAnalysisState.Analyzing) return

        viewModelScope.launch {
            _cloneLab.update { it.copy(analysisState = CloneAnalysisState.Analyzing) }
            try {
                val bytes = withContext(Dispatchers.IO) { sample.file.readBytes() }
                val dna = geminiVoiceRepository.analyzeVoiceSample(
                    audioBytes = bytes,
                    mimeType = sample.mimeType,
                    preferredLanguage = state.targetLanguage
                )
                val resolvedLang = findLanguageByNameOrCode(dna.detectedLanguageCode)
                _cloneLab.update {
                    it.copy(
                        analysisState = CloneAnalysisState.Ready(dna),
                        editableCloneName = dna.suggestedName,
                        editableAccent = dna.detectedAccent,
                        editableBaseVoice = dna.closestBaseVoice,
                        editableLanguage = resolvedLang,
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
        language: StudioLanguage? = null,
        tagsText: String? = null
    ) {
        _cloneLab.update { state ->
            state.copy(
                editableCloneName = name ?: state.editableCloneName,
                editableAccent = accent ?: state.editableAccent,
                editableBaseVoice = baseVoice ?: state.editableBaseVoice,
                editableLanguage = language ?: state.editableLanguage,
                editableTagsText = tagsText ?: state.editableTagsText
            )
        }
    }

    fun testAnalyzedClone() {
        val state = _cloneLab.value
        val analysis = (state.analysisState as? CloneAnalysisState.Ready)?.analysis ?: return
        if (state.isTestingClone) return

        val lang = state.editableLanguage
        val testLine = when {
            lang.code.startsWith("bn") -> "নমস্কার! [warmly] এটা আমার ক্লোন করা ভয়েস—একদম আসল অ্যাকসেন্ট আর অনুভূতি নিয়ে কথা বলছি।"
            lang.code.startsWith("hi") -> "नमस्ते! [warmly] यह मेरी क्लोन की गई आवाज़ है—बिल्कुल असली लहजे और अंदाज़ में।"
            lang.code.startsWith("ta") -> "வணக்கம்! இது என் குளோன் செய்யப்பட்ட குரல், இயல்பான உச்சரிப்புடன் பேசுகிறேன்."
            lang.code.startsWith("es") -> "¡Hola! [warmly] Esta es mi voz clonada con mi acento natural en VoxCraft Studio."
            lang.code.startsWith("fr") -> "Bonjour ! [warmly] Voici ma voix clonée avec mon accent naturel."
            lang.code.startsWith("ja") -> "こんにちは！[warmly] これは私のクローン音声です。自然なアクセントで話しています。"
            else -> "Hello! [warmly] This is my cloned voice inside VoxCraft Studio, preserving my exact accent and natural rhythm."
        }

        val tempVoice = StudioVoice(
            id = "clone_test_preview",
            displayName = state.editableCloneName.ifBlank { analysis.suggestedName },
            geminiVoiceName = state.editableBaseVoice.ifBlank { analysis.closestBaseVoice },
            isCloned = true,
            categoryLabel = "Clone Test",
            languageCode = lang.code,
            languageName = lang.name,
            countryFlag = lang.flag,
            accent = state.editableAccent.ifBlank { analysis.detectedAccent },
            description = analysis.timbreSummary,
            tags = analysis.tags,
            acousticDnaPrompt = analysis.acousticDnaPrompt,
            defaultSpeed = analysis.defaultSpeed
        )

        viewModelScope.launch {
            _cloneLab.update { it.copy(isTestingClone = true) }
            try {
                val payload = geminiVoiceRepository.synthesizeSpeech(
                    script = testLine,
                    voice = tempVoice,
                    config = HumanizationConfig(
                        speed = tempVoice.defaultSpeed,
                        languageCode = lang.code,
                        language = lang.name,
                        countryFlag = lang.flag
                    ),
                    performanceMode = appSettings.value.performanceMode
                )
                val saved = audioEngine.saveSynthesizedAudio(
                    audioBytes = payload.bytes,
                    mimeType = payload.mimeType
                )
                audioEngine.playTrack(
                    trackId = "clone_test_${System.currentTimeMillis()}",
                    title = "Clone Test • ${lang.flag} ${lang.name}",
                    voiceName = tempVoice.displayName,
                    isClonedVoice = true,
                    audioFilePath = saved.file.absolutePath,
                    waveformPeaks = saved.waveformPeaks,
                    latencyMs = payload.latencyMs
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clone test synthesis failed", e)
                _snackbarEvents.tryEmit(e.message ?: "Clone test failed.")
            } finally {
                _cloneLab.update { it.copy(isTestingClone = false) }
            }
        }
    }

    fun saveClonedVoice() {
        val state = _cloneLab.value
        val sample = state.capturedSample ?: return
        val analysis = (state.analysisState as? CloneAnalysisState.Ready)?.analysis ?: return
        val name = state.editableCloneName.trim().ifBlank { analysis.suggestedName }
        val lang = state.editableLanguage

        viewModelScope.launch {
            val entity = ClonedVoiceEntity(
                id = "clone_${UUID.randomUUID()}",
                name = name,
                baseGeminiVoice = state.editableBaseVoice.ifBlank { analysis.closestBaseVoice },
                acousticDnaPrompt = analysis.acousticDnaPrompt,
                pitchDescriptor = analysis.pitchDescriptor,
                paceDescriptor = analysis.paceDescriptor,
                timbreSummary = analysis.timbreSummary,
                accent = state.editableAccent.trim().ifBlank { analysis.detectedAccent },
                languageCode = lang.code,
                languageName = lang.name,
                countryFlag = lang.flag,
                tagsCsv = state.editableTagsText.trim().ifBlank { analysis.tags.joinToString(", ") },
                defaultSpeed = analysis.defaultSpeed,
                sampleAudioPath = sample.file.absolutePath,
                waveformPeaksCsv = StudioRepository.encodePeaks(sample.waveformPeaks)
            )
            studioRepository.saveClonedVoice(entity)
            selectClonedVoiceForStudio(entity, switchToStudio = true)

            _cloneLab.value = CloneLabState(
                inputModeRecord = state.inputModeRecord,
                targetLanguage = state.targetLanguage
            )
            _snackbarEvents.tryEmit("Saved ${lang.flag} clone \"$name\" and loaded in Studio")
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

    // --- History & Export Actions ---

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
            voiceName = "${entity.countryFlag} ${entity.voiceName}",
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
        val lang = findLanguageByNameOrCode(entity.language)

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
                    languageCode = lang.code,
                    language = lang.name,
                    countryFlag = lang.flag
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

    fun saveAudioToDevice(audioFilePath: String, voiceName: String) {
        viewModelScope.launch {
            val result = audioEngine.saveToDeviceStorage(audioFilePath, voiceName)
            result.onSuccess { savedLocation ->
                _snackbarEvents.tryEmit("Saved to $savedLocation")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Failed to save audio to internal storage.")
            }
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
