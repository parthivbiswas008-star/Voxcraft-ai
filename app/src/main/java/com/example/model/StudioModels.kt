package com.example.model

import kotlin.math.abs
import kotlin.math.sin

enum class StudioTab(val label: String) {
    STUDIO("Studio"),
    VOICES("Voices"),
    CLONE("Clone"),
    HISTORY("History")
}

enum class VoiceCategory(val label: String) {
    ALL("All"),
    MY_CLONES("My Clones"),
    FAVORITES("Favorites"),
    STORYTELLING("Storytelling"),
    ASSISTANT("Assistant"),
    CONVERSATIONAL("Conversational"),
    BROADCAST("Broadcast"),
    CHARACTER("Character"),
    CALM("Calm")
}

data class VoicePreset(
    val id: String,
    val displayName: String,
    val geminiVoiceName: String,
    val category: VoiceCategory,
    val accent: String,
    val tone: String,
    val description: String,
    val tags: List<String>,
    val previewSampleText: String
) {
    val waveformSignature: List<Float> by lazy {
        val seed = id.hashCode()
        List(28) { index ->
            val phase = (index + abs(seed % 11)) * 0.42
            val harmonic = sin(phase) * 0.32 + sin(phase * 1.9 + (seed % 5)) * 0.22
            (0.42f + harmonic.toFloat()).coerceIn(0.14f, 0.96f)
        }
    }
}

data class StudioVoice(
    val id: String,
    val displayName: String,
    val geminiVoiceName: String,
    val isCloned: Boolean,
    val categoryLabel: String,
    val accent: String,
    val description: String,
    val tags: List<String>,
    val acousticDnaPrompt: String = "",
    val defaultSpeed: Float = 1.0f,
    val waveformSignature: List<Float> = emptyList(),
    val sampleAudioPath: String? = null
)

enum class EmotionPreset(
    val label: String,
    val directive: String,
    val positivity: Float,
    val curiosity: Float,
    val surprise: Float,
    val sadness: Float,
    val intensity: Float
) {
    CONVERSATIONAL(
        label = "Conversational",
        directive = "natural, relaxed, authentic conversational cadence with organic micro-pauses",
        positivity = 0.55f,
        curiosity = 0.45f,
        surprise = 0.15f,
        sadness = 0.0f,
        intensity = 0.45f
    ),
    NEUTRAL(
        label = "Studio Neutral",
        directive = "clean, balanced, crystal-clear studio articulation",
        positivity = 0.2f,
        curiosity = 0.2f,
        surprise = 0.0f,
        sadness = 0.0f,
        intensity = 0.3f
    ),
    WARM_STORYTELLER(
        label = "Warm Storyteller",
        directive = "warm, intimate narrative storytelling with expressive melody and gentle breaths",
        positivity = 0.7f,
        curiosity = 0.6f,
        surprise = 0.25f,
        sadness = 0.1f,
        intensity = 0.6f
    ),
    EXCITED(
        label = "Excited",
        directive = "energetic, bright, enthusiastic delivery with upbeat inflection",
        positivity = 0.9f,
        curiosity = 0.7f,
        surprise = 0.65f,
        sadness = 0.0f,
        intensity = 0.85f
    ),
    DRAMATIC(
        label = "Dramatic",
        directive = "cinematic, resonant, high-stakes dramatic gravity with deliberate pauses",
        positivity = 0.15f,
        curiosity = 0.4f,
        surprise = 0.5f,
        sadness = 0.35f,
        intensity = 0.9f
    ),
    CALM_WHISPER(
        label = "Calm Whisper",
        directive = "soft, soothing, close-mic hushed calm with gentle breathing",
        positivity = 0.4f,
        curiosity = 0.2f,
        surprise = 0.0f,
        sadness = 0.1f,
        intensity = 0.2f
    ),
    CUSTOM(
        label = "Custom Mix",
        directive = "custom nuanced emotional blend",
        positivity = 0.5f,
        curiosity = 0.5f,
        surprise = 0.2f,
        sadness = 0.0f,
        intensity = 0.5f
    )
}

data class HumanizationConfig(
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val emotionPreset: EmotionPreset = EmotionPreset.CONVERSATIONAL,
    val positivity: Float = EmotionPreset.CONVERSATIONAL.positivity,
    val curiosity: Float = EmotionPreset.CONVERSATIONAL.curiosity,
    val surprise: Float = EmotionPreset.CONVERSATIONAL.surprise,
    val sadness: Float = EmotionPreset.CONVERSATIONAL.sadness,
    val intensity: Float = EmotionPreset.CONVERSATIONAL.intensity,
    val naturalBreathsEnabled: Boolean = true,
    val language: String = "English (US)"
)

data class InlineCue(
    val tag: String,
    val label: String
)

val StudioInlineCues = listOf(
    InlineCue("[laughter]", "Laughter"),
    InlineCue("[sigh]", "Sigh"),
    InlineCue("[pause 0.5s]", "Pause 0.5s"),
    InlineCue("[whisper]", "Whisper"),
    InlineCue("[excited]", "Excited"),
    InlineCue("[warmly]", "Warmly"),
    InlineCue("[slowly]", "Slowly")
)

val SupportedStudioLanguages = listOf(
    "English (US)",
    "English (UK)",
    "English (India)",
    "Spanish",
    "French",
    "German",
    "Hindi",
    "Japanese",
    "Korean",
    "Portuguese",
    "Italian",
    "Arabic"
)

data class AcousticDnaAnalysis(
    val suggestedName: String,
    val closestBaseVoice: String,
    val accent: String,
    val pitchDescriptor: String,
    val paceDescriptor: String,
    val timbreSummary: String,
    val acousticDnaPrompt: String,
    val tags: List<String>,
    val defaultSpeed: Float
)

data class AudioPlaybackState(
    val trackId: String? = null,
    val title: String = "",
    val voiceName: String = "",
    val isClonedVoice: Boolean = false,
    val audioFilePath: String? = null,
    val waveformPeaks: List<Float> = emptyList(),
    val isPlaying: Boolean = false,
    val isLooping: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val latencyMs: Long? = null
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed interface SynthesisUiState {
    data object Idle : SynthesisUiState
    data class Generating(val elapsedMs: Long = 0L) : SynthesisUiState
    data class Error(val message: String) : SynthesisUiState
}

sealed interface CloneAnalysisState {
    data object Idle : CloneAnalysisState
    data object Analyzing : CloneAnalysisState
    data class Ready(val analysis: AcousticDnaAnalysis) : CloneAnalysisState
    data class Error(val message: String) : CloneAnalysisState
}

object VoiceCatalog {
    val geminiVoiceNames: List<String> = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Zephyr",
        "Leda", "Orus", "Callirrhoe", "Autonoe", "Enceladus", "Iapetus",
        "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi",
        "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima",
        "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )

    val presets: List<VoicePreset> = listOf(
        VoicePreset(
            id = "preset_kore",
            displayName = "Kore — Sonic Anchor",
            geminiVoiceName = "Kore",
            category = VoiceCategory.ASSISTANT,
            accent = "US Studio",
            tone = "Firm & Crisp",
            description = "Ultra-clear flagship studio voice tuned for real-time AI assistants and crisp narration.",
            tags = listOf("Flagship", "Crisp", "Natural"),
            previewSampleText = "Welcome to VoxCraft Studio. Every breath, pause, and inflection is tuned for lifelike realism."
        ),
        VoicePreset(
            id = "preset_puck",
            displayName = "Puck — Upbeat Creator",
            geminiVoiceName = "Puck",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "US Casual",
            tone = "Lively & Warm",
            description = "Expressive, upbeat conversational speaker ideal for social videos, podcasts, and banter.",
            tags = listOf("Upbeat", "Expressive", "Podcast"),
            previewSampleText = "Hey there! Ready to turn plain text into speech that sounds genuinely human? Let's dive in."
        ),
        VoicePreset(
            id = "preset_aoede",
            displayName = "Aoede — Velvet Storyteller",
            geminiVoiceName = "Aoede",
            category = VoiceCategory.STORYTELLING,
            accent = "Mid-Atlantic",
            tone = "Breezy & Melodic",
            description = "Warm, nuanced narrator with effortless rhythmic flow for audiobooks and brand films.",
            tags = listOf("Audiobook", "Warm", "Melodic"),
            previewSampleText = "Under the neon glow of the midnight studio, the first soundwave began to breathe."
        ),
        VoicePreset(
            id = "preset_fenrir",
            displayName = "Fenrir — Cinema Baritone",
            geminiVoiceName = "Fenrir",
            category = VoiceCategory.CHARACTER,
            accent = "US Deep",
            tone = "Bold & Excitable",
            description = "Commanding, dynamic vocal presence built for trailers, gaming characters, and bold hooks.",
            tags = listOf("Trailer", "Bold", "Dynamic"),
            previewSampleText = "Some voices merely read words. Others command the room from the very first syllable."
        ),
        VoicePreset(
            id = "preset_charon",
            displayName = "Charon — Broadcast Pro",
            geminiVoiceName = "Charon",
            category = VoiceCategory.BROADCAST,
            accent = "US Broadcast",
            tone = "Informative & Grounded",
            description = "Authoritative documentary and newsroom voice with composed, trustworthy pacing.",
            tags = listOf("Documentary", "News", "Grounded"),
            previewSampleText = "Real-time neural audio synthesis is reshaping how creators produce studio-grade voiceovers."
        ),
        VoicePreset(
            id = "preset_zephyr",
            displayName = "Zephyr — Bright Concierge",
            geminiVoiceName = "Zephyr",
            category = VoiceCategory.ASSISTANT,
            accent = "US Bright",
            tone = "Bright & Friendly",
            description = "Polished, welcoming voice assistant designed for low-latency customer conversations.",
            tags = listOf("Concierge", "Friendly", "Clean"),
            previewSampleText = "Hi! I've got your schedule lined up and ready whenever you want to review the details."
        ),
        VoicePreset(
            id = "preset_enceladus",
            displayName = "Enceladus — Midnight Calm",
            geminiVoiceName = "Enceladus",
            category = VoiceCategory.CALM,
            accent = "Soft Neutral",
            tone = "Breathy & Gentle",
            description = "Intimate, soft-spoken breathy tone tailored for mindfulness, sleep guides, and ASMR.",
            tags = listOf("Breathy", "Mindfulness", "Soft"),
            previewSampleText = "Take a slow breath in, let your shoulders drop, and listen to the quiet rhythm of the room."
        ),
        VoicePreset(
            id = "preset_leda",
            displayName = "Leda — Youthful Host",
            geminiVoiceName = "Leda",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "US Modern",
            tone = "Youthful & Agile",
            description = "Agile, modern voice with natural smile intonation for explainers and mobile companions.",
            tags = listOf("Modern", "Explainer", "Agile"),
            previewSampleText = "This takes literally ten seconds—just drop your script in and dial up the emotion."
        ),
        VoicePreset(
            id = "preset_orus",
            displayName = "Orus — Executive Keynote",
            geminiVoiceName = "Orus",
            category = VoiceCategory.BROADCAST,
            accent = "US Executive",
            tone = "Firm & Poised",
            description = "Confident keynote speaker with measured cadence for product launches and fintech.",
            tags = listOf("Keynote", "Executive", "Poised"),
            previewSampleText = "Precision engineering means zero compromise between latency and vocal warmth."
        ),
        VoicePreset(
            id = "preset_sulafat",
            displayName = "Sulafat — Warm Fireside",
            geminiVoiceName = "Sulafat",
            category = VoiceCategory.STORYTELLING,
            accent = "Warm Global",
            tone = "Warm & Inviting",
            description = "Richly empathetic storytelling voice that wraps listeners in effortless warmth.",
            tags = listOf("Empathetic", "Story", "Rich"),
            previewSampleText = "Every great story begins with a voice that makes you lean in just a little closer."
        ),
        VoicePreset(
            id = "preset_callirrhoe",
            displayName = "Callirrhoe — Easygoing Guide",
            geminiVoiceName = "Callirrhoe",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "US West Coast",
            tone = "Easygoing & Natural",
            description = "Laid-back, friendly guide that sounds like talking to a sharp coworker over coffee.",
            tags = listOf("Easygoing", "Support", "Natural"),
            previewSampleText = "No rush at all—we can tweak the speed and warmth until it feels completely effortless."
        ),
        VoicePreset(
            id = "preset_umbriel",
            displayName = "Umbriel — Noir Narrator",
            geminiVoiceName = "Umbriel",
            category = VoiceCategory.CHARACTER,
            accent = "Velvet Low",
            tone = "Easygoing & Deep",
            description = "Smooth low-register voice ideal for mystery fiction, audio dramas, and late-night radio.",
            tags = listOf("Noir", "Deep", "Audio Drama"),
            previewSampleText = "Rain tapped against the studio glass while the tape reels spun quietly into the dark."
        ),
        VoicePreset(
            id = "preset_algieba",
            displayName = "Algieba — Smooth Lounge",
            geminiVoiceName = "Algieba",
            category = VoiceCategory.CALM,
            accent = "Smooth Neutral",
            tone = "Smooth & Silky",
            description = "Velvety, unhurried delivery that stays poised across long-form narrations.",
            tags = listOf("Smooth", "Long-Form", "Poised"),
            previewSampleText = "Seamless pacing keeps your audience immersed from the opening hook to the final note."
        ),
        VoicePreset(
            id = "preset_despina",
            displayName = "Despina — Polished Studio",
            geminiVoiceName = "Despina",
            category = VoiceCategory.BROADCAST,
            accent = "Clean Studio",
            tone = "Smooth & Clear",
            description = "Broadcast-ready smooth voice crafted for e-learning modules and premium commercials.",
            tags = listOf("Commercial", "E-Learning", "Polished"),
            previewSampleText = "Clarity and emotion work together when every syllable lands right on beat."
        ),
        VoicePreset(
            id = "preset_achird",
            displayName = "Achird — Friendly Companion",
            geminiVoiceName = "Achird",
            category = VoiceCategory.ASSISTANT,
            accent = "US Warm",
            tone = "Friendly & Approachable",
            description = "Approachable everyday assistant voice with genuine conversational warmth.",
            tags = listOf("Companion", "Approachable", "Warm"),
            previewSampleText = "Whenever you're ready, tap record and we'll map your vocal signature in seconds."
        ),
        VoicePreset(
            id = "preset_algenib",
            displayName = "Algenib — Gravelly Legend",
            geminiVoiceName = "Algenib",
            category = VoiceCategory.CHARACTER,
            accent = "Textured Low",
            tone = "Gravelly & Iconic",
            description = "Textured, weathered character voice packed with grit for RPG quests and gritty promos.",
            tags = listOf("Gravelly", "RPG", "Character"),
            previewSampleText = "We've walked through louder storms than this—turn the gain up and let them hear us."
        ),
        VoicePreset(
            id = "preset_vindemiatrix",
            displayName = "Vindemiatrix — Serene Coach",
            geminiVoiceName = "Vindemiatrix",
            category = VoiceCategory.CALM,
            accent = "Gentle Neutral",
            tone = "Gentle & Reassuring",
            description = "Gentle, reassuring cadence crafted for wellness coaching and calm tutorials.",
            tags = listOf("Wellness", "Gentle", "Calm"),
            previewSampleText = "You don't need to force perfection—just speak naturally and let the flow carry you."
        ),
        VoicePreset(
            id = "preset_gacrux",
            displayName = "Gacrux — Mature Narrator",
            geminiVoiceName = "Gacrux",
            category = VoiceCategory.STORYTELLING,
            accent = "Classic Studio",
            tone = "Mature & Wise",
            description = "Seasoned, thoughtful narrator with gravitas for biographies and historical storytelling.",
            tags = listOf("Biography", "Wise", "Classic"),
            previewSampleText = "History remembers not only what was written, but the cadence of the voice that spoke it."
        ),
        VoicePreset(
            id = "preset_laomedeia",
            displayName = "Laomedeia — Spark Creator",
            geminiVoiceName = "Laomedeia",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "Bright US",
            tone = "Upbeat & Radiant",
            description = "Sparkling, energetic voice built for short-form hooks, Reels, and upbeat launches.",
            tags = listOf("Shorts", "Radiant", "Energetic"),
            previewSampleText = "Wait until you hear how realistic the humanization sliders sound on this take!"
        ),
        VoicePreset(
            id = "preset_rasalgethi",
            displayName = "Rasalgethi — Tech Analyst",
            geminiVoiceName = "Rasalgethi",
            category = VoiceCategory.BROADCAST,
            accent = "Sharp Studio",
            tone = "Informative & Sharp",
            description = "Analytical, articulate tech host built for deep-dive explainers and product demos.",
            tags = listOf("Tech", "Demo", "Articulate"),
            previewSampleText = "Notice how the emotion vector shifts prosody naturally without introducing robotic artifacts."
        ),
        VoicePreset(
            id = "preset_sadachbia",
            displayName = "Sadachbia — Lively Story",
            geminiVoiceName = "Sadachbia",
            category = VoiceCategory.CHARACTER,
            accent = "Expressive Global",
            tone = "Lively & Animated",
            description = "Playful, animated voice with nimble pitch range for interactive fiction and comedy.",
            tags = listOf("Animated", "Playful", "Story"),
            previewSampleText = "Hold on—did that synthesizer just laugh right on cue? Oh, this studio is dangerous!"
        ),
        VoicePreset(
            id = "preset_schedar",
            displayName = "Schedar — Balanced Pro",
            geminiVoiceName = "Schedar",
            category = VoiceCategory.ASSISTANT,
            accent = "Neutral Studio",
            tone = "Even & Dependable",
            description = "Dependable, even-keeled voice that stays consistent across long technical scripts.",
            tags = listOf("Consistent", "Technical", "Clean"),
            previewSampleText = "Your custom voice profiles and WAV renders are saved privately right on this device."
        ),
        VoicePreset(
            id = "preset_autonoe",
            displayName = "Autonoe — Radiant Host",
            geminiVoiceName = "Autonoe",
            category = VoiceCategory.BROADCAST,
            accent = "Bright Studio",
            tone = "Bright & Engaging",
            description = "Clear, magnetic host voice suited for morning shows, promos, and live announcements.",
            tags = listOf("Host", "Promo", "Bright"),
            previewSampleText = "Good morning creators—today we're crafting cinema-grade speech right from your pocket."
        ),
        VoicePreset(
            id = "preset_iapetus",
            displayName = "Iapetus — Studio Clarifier",
            geminiVoiceName = "Iapetus",
            category = VoiceCategory.BROADCAST,
            accent = "Crisp Neutral",
            tone = "Clear & Direct",
            description = "Direct, articulate voice engineered for crisp training videos and tutorials.",
            tags = listOf("Tutorial", "Direct", "Crisp"),
            previewSampleText = "Adjust speed from point six to one point five while preserving natural vocal resonance."
        ),
        VoicePreset(
            id = "preset_erinome",
            displayName = "Erinome — Pure Vocalist",
            geminiVoiceName = "Erinome",
            category = VoiceCategory.CALM,
            accent = "Clear Neutral",
            tone = "Clear & Poised",
            description = "Transparent, refined vocal tone with minimal coloration for pure studio clarity.",
            tags = listOf("Pure", "Refined", "Clear"),
            previewSampleText = "Listen closely to the subtle acoustic space between each phrase."
        ),
        VoicePreset(
            id = "preset_achernar",
            displayName = "Achernar — Soft Whisper",
            geminiVoiceName = "Achernar",
            category = VoiceCategory.CALM,
            accent = "Soft Studio",
            tone = "Soft & Intimate",
            description = "Delicate, soothing voice ideal for calm guidance, poetry, and gentle narration.",
            tags = listOf("Poetry", "Intimate", "Soothing"),
            previewSampleText = "Quiet words carry further than thunder when delivered with genuine calm."
        ),
        VoicePreset(
            id = "preset_alnilam",
            displayName = "Alnilam — Bold Presenter",
            geminiVoiceName = "Alnilam",
            category = VoiceCategory.BROADCAST,
            accent = "US Firm",
            tone = "Firm & Decisive",
            description = "Decisive, punchy presenter tone built for high-impact decks and commercials.",
            tags = listOf("Punchy", "Presenter", "Firm"),
            previewSampleText = "Zero paywalls, zero credit counters, and unlimited voice cloning—ready right now."
        ),
        VoicePreset(
            id = "preset_pulcherrima",
            displayName = "Pulcherrima — Forward Spark",
            geminiVoiceName = "Pulcherrima",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "Energetic US",
            tone = "Forward & Confident",
            description = "Confident, forward-leaning voice made for modern startup pitches and hooks.",
            tags = listOf("Startup", "Confident", "Modern"),
            previewSampleText = "Let's ship a voice experience that feels instantaneous, expressive, and unmistakably yours."
        ),
        VoicePreset(
            id = "preset_zubenelgenubi",
            displayName = "Zuben — Casual Buddy",
            geminiVoiceName = "Zubenelgenubi",
            category = VoiceCategory.CONVERSATIONAL,
            accent = "US Relaxed",
            tone = "Casual & Unscripted",
            description = "Unscripted, authentic casual voice that sounds like a natural voice memo.",
            tags = listOf("Voice Memo", "Casual", "Authentic"),
            previewSampleText = "Honestly, once you hear the humanize toggle kick in, it barely sounds synthetic at all."
        ),
        VoicePreset(
            id = "preset_sadaltager",
            displayName = "Sadaltager — Knowledge Mentor",
            geminiVoiceName = "Sadaltager",
            category = VoiceCategory.STORYTELLING,
            accent = "Warm Academic",
            tone = "Knowledgeable & Warm",
            description = "Thoughtful educator and documentary mentor who makes complex ideas effortless.",
            tags = listOf("Educator", "Mentor", "Documentary"),
            previewSampleText = "When cadence, breath, and emotional intent align, speech becomes truly memorable."
        )
    )

    fun toStudioVoice(preset: VoicePreset): StudioVoice = StudioVoice(
        id = preset.id,
        displayName = preset.displayName,
        geminiVoiceName = preset.geminiVoiceName,
        isCloned = false,
        categoryLabel = preset.category.label,
        accent = preset.accent,
        description = preset.description,
        tags = preset.tags,
        waveformSignature = preset.waveformSignature
    )
}
