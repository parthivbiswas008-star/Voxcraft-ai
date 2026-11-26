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

data class StudioLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val flag: String
) {
    val displayWithFlag: String get() = "$flag  $name"
    val fullLabel: String get() = "$flag  $name ($nativeName)"
}

val SupportedStudioLanguages: List<StudioLanguage> = listOf(
    StudioLanguage("en-US", "English (US)", "English", "🇺🇸"),
    StudioLanguage("en-GB", "English (UK)", "British English", "🇬🇧"),
    StudioLanguage("en-IN", "English (India)", "Indian English", "🇮🇳"),
    StudioLanguage("hi-IN", "Hindi", "हिन्दी", "🇮🇳"),
    StudioLanguage("bn-IN", "Bengali (India)", "বাংলা — ভারত", "🇮🇳"),
    StudioLanguage("bn-BD", "Bengali (Bangladesh)", "বাংলা — বাংলাদেশ", "🇧🇩"),
    StudioLanguage("ta-IN", "Tamil", "தமிழ்", "🇮🇳"),
    StudioLanguage("te-IN", "Telugu", "తెలుగు", "🇮🇳"),
    StudioLanguage("mr-IN", "Marathi", "मराठी", "🇮🇳"),
    StudioLanguage("gu-IN", "Gujarati", "ગુજરાતી", "🇮🇳"),
    StudioLanguage("ur-PK", "Urdu", "اردو", "🇵🇰"),
    StudioLanguage("es-ES", "Spanish", "Español", "🇪🇸"),
    StudioLanguage("es-MX", "Spanish (Mexico)", "Español MX", "🇲🇽"),
    StudioLanguage("fr-FR", "French", "Français", "🇫🇷"),
    StudioLanguage("de-DE", "German", "Deutsch", "🇩🇪"),
    StudioLanguage("ja-JP", "Japanese", "日本語", "🇯🇵"),
    StudioLanguage("ko-KR", "Korean", "한국어", "🇰🇷"),
    StudioLanguage("zh-CN", "Mandarin Chinese", "中文", "🇨🇳"),
    StudioLanguage("ar-SA", "Arabic", "العربية", "🇸🇦"),
    StudioLanguage("pt-BR", "Portuguese (Brazil)", "Português", "🇧🇷"),
    StudioLanguage("it-IT", "Italian", "Italiano", "🇮🇹"),
    StudioLanguage("ru-RU", "Russian", "Русский", "🇷🇺"),
    StudioLanguage("tr-TR", "Turkish", "Türkçe", "🇹🇷"),
    StudioLanguage("id-ID", "Indonesian", "Bahasa Indonesia", "🇮🇩"),
    StudioLanguage("vi-VN", "Vietnamese", "Tiếng Việt", "🇻🇳"),
    StudioLanguage("th-TH", "Thai", "ไทย", "🇹🇭")
)

val AutoDetectLanguageOption = StudioLanguage(
    code = "auto",
    name = "Auto-Detect from Voice",
    nativeName = "Match sample accent & tongue",
    flag = "🌐"
)

fun findLanguageByNameOrCode(query: String): StudioLanguage {
    val clean = query.trim().lowercase()
    return SupportedStudioLanguages.find {
        it.code.lowercase() == clean ||
            it.name.lowercase() == clean ||
            it.displayWithFlag.lowercase() == clean ||
            clean.contains(it.name.lowercase())
    } ?: SupportedStudioLanguages.first()
}

data class VoicePreset(
    val id: String,
    val displayName: String,
    val geminiVoiceName: String,
    val category: VoiceCategory,
    val languageCode: String,
    val languageName: String,
    val countryFlag: String,
    val accent: String,
    val tone: String,
    val description: String,
    val tags: List<String>,
    val previewSampleText: String,
    val characterDirective: String = ""
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
    val languageCode: String = "en-US",
    val languageName: String = "English (US)",
    val countryFlag: String = "🇺🇸",
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
    val languageCode: String = "en-US",
    val language: String = "English (US)",
    val countryFlag: String = "🇺🇸"
)

data class InlineCue(
    val tag: String,
    val label: String
)

val StudioInlineCues = listOf(
    InlineCue("[happy]", "Happy"),
    InlineCue("[sad]", "Sad"),
    InlineCue("[angry]", "Angry"),
    InlineCue("[excited]", "Excited"),
    InlineCue("[whisper]", "Whisper"),
    InlineCue("[laughter]", "Laughter"),
    InlineCue("[sigh]", "Sigh"),
    InlineCue("[crying]", "Crying"),
    InlineCue("[romantic]", "Romantic"),
    InlineCue("[sarcastic]", "Sarcastic"),
    InlineCue("[scared]", "Scared"),
    InlineCue("[confident]", "Confident"),
    InlineCue("[pause 0.5s]", "Pause 0.5s"),
    InlineCue("[warmly]", "Warmly"),
    InlineCue("[slowly]", "Slowly")
)

private val BracketEmotionRegex = Regex("\\[([^\\[\\]]{1,40})\\]")

fun extractBracketedEmotions(script: String): List<String> =
    BracketEmotionRegex.findAll(script)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

data class AcousticDnaAnalysis(
    val suggestedName: String,
    val closestBaseVoice: String,
    val detectedLanguage: String,
    val detectedLanguageCode: String,
    val detectedAccent: String,
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
    data class Analyzing(val stage: String = "Scanning audio & extracting clearest speech…") : CloneAnalysisState
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
        // --- Flagship English ---
        VoicePreset(
            id = "preset_kore",
            displayName = "Kore — Sonic Anchor",
            geminiVoiceName = "Kore",
            category = VoiceCategory.ASSISTANT,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "US Studio",
            tone = "Firm & Crisp",
            description = "Ultra-clear flagship studio voice tuned for real-time AI assistants and crisp narration.",
            tags = listOf("Flagship", "Crisp", "Natural"),
            previewSampleText = "Welcome to VoxCraft Studio. [warmly] Every breath, pause, and emotion shifts naturally in real time."
        ),
        VoicePreset(
            id = "preset_puck",
            displayName = "Puck — Upbeat Creator",
            geminiVoiceName = "Puck",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "US Casual",
            tone = "Lively & Warm",
            description = "Expressive, upbeat conversational speaker ideal for reels, podcasts, and banter.",
            tags = listOf("Upbeat", "Expressive", "Podcast"),
            previewSampleText = "[excited] Hey there! Drop any emotion inside brackets and hear it come alive immediately!"
        ),
        VoicePreset(
            id = "preset_aoede_uk",
            displayName = "Aoede — London Storyteller",
            geminiVoiceName = "Aoede",
            category = VoiceCategory.STORYTELLING,
            languageCode = "en-GB",
            languageName = "English (UK)",
            countryFlag = "🇬🇧",
            accent = "British RP",
            tone = "Velvet & Melodic",
            description = "Warm British narrator with effortless rhythmic flow for audiobooks and cinema films.",
            tags = listOf("British", "Audiobook", "Melodic"),
            previewSampleText = "Under the midnight glass dome of London, the first acoustic wave began to glow.",
            characterDirective = "Speak with a refined, warm British London accent."
        ),
        VoicePreset(
            id = "preset_fenrir",
            displayName = "Fenrir — Cinema Baritone",
            geminiVoiceName = "Fenrir",
            category = VoiceCategory.CHARACTER,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "US Deep",
            tone = "Bold & Dramatic",
            description = "Commanding vocal presence built for trailers, gaming characters, and bold hooks.",
            tags = listOf("Trailer", "Bold", "Dynamic"),
            previewSampleText = "Some voices merely read words. [dramatic] Others command the room from the very first breath."
        ),
        VoicePreset(
            id = "preset_charon",
            displayName = "Charon — Broadcast Pro",
            geminiVoiceName = "Charon",
            category = VoiceCategory.BROADCAST,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "US Broadcast",
            tone = "Grounded & Clear",
            description = "Authoritative documentary and newsroom voice with composed, trustworthy pacing.",
            tags = listOf("Documentary", "News", "Grounded"),
            previewSampleText = "Real-time neural speech synthesis is transforming how creators produce broadcast audio."
        ),
        VoicePreset(
            id = "preset_zephyr_in",
            displayName = "Rohan — Indian English Host",
            geminiVoiceName = "Zephyr",
            category = VoiceCategory.ASSISTANT,
            languageCode = "en-IN",
            languageName = "English (India)",
            countryFlag = "🇮🇳",
            accent = "Indian English",
            tone = "Bright & Smart",
            description = "Polished modern Indian English voice tailored for tech explainers and AI agents.",
            tags = listOf("Indian English", "Tech", "Friendly"),
            previewSampleText = "Hi! Welcome to VoxCraft AI—ultra-smooth voice generation with complete creative control.",
            characterDirective = "Speak in natural, modern urban Indian English accent."
        ),

        // --- Bengali (বাংলা) Dedicated Characters ---
        VoicePreset(
            id = "preset_bn_ananya",
            displayName = "Ananya — Kolkata Storyteller",
            geminiVoiceName = "Sulafat",
            category = VoiceCategory.STORYTELLING,
            languageCode = "bn-IN",
            languageName = "Bengali (India)",
            countryFlag = "🇮🇳",
            accent = "Kolkata Bengali",
            tone = "Warm & Poetic",
            description = "Expressive Kolkata Bengali voice with natural musicality for stories, poetry, and narration.",
            tags = listOf("বাংলা", "Kolkata", "Storyteller"),
            previewSampleText = "নমস্কার! ভক্সক্রাফট স্টুডিওতে আপনাকে স্বাগতম। [warmly] আজকের গল্পটা একদম হৃদয় ছুঁয়ে যাবে।",
            characterDirective = "Speak fluently in natural Kolkata Bengali (বাংলা) with warm melodic intonation."
        ),
        VoicePreset(
            id = "preset_bn_aritro",
            displayName = "Aritro — Bengali Podcast Host",
            geminiVoiceName = "Orus",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "bn-IN",
            languageName = "Bengali (India)",
            countryFlag = "🇮🇳",
            accent = "Modern Bengali",
            tone = "Crisp & Friendly",
            description = "Natural, lively Bengali conversational voice built for YouTube, podcasts, and daily assistants.",
            tags = listOf("বাংলা", "Podcast", "Conversational"),
            previewSampleText = "[happy] দারুণ ব্যাপার! এখন বাংলায় যেকোনো ইমোশন দিয়ে একদম মানুষের মতো ভয়েস বানানো যাবে।",
            characterDirective = "Speak naturally in lively conversational Kolkata Bengali (বাংলা)."
        ),
        VoicePreset(
            id = "preset_bn_tanvir",
            displayName = "Tanvir — Dhaka Anchor",
            geminiVoiceName = "Charon",
            category = VoiceCategory.BROADCAST,
            languageCode = "bn-BD",
            languageName = "Bengali (Bangladesh)",
            countryFlag = "🇧🇩",
            accent = "Dhaka Bengali",
            tone = "Resonant & Clear",
            description = "Confident Dhaka Bengali presenter voice crafted for news, documentaries, and reels.",
            tags = listOf("বাংলা", "Dhaka", "Broadcast"),
            previewSampleText = "আসসালামু আলাইকুম! ভক্সক্রাফট এআই-এর রিয়েল-টাইম বাংলা ভয়েস স্টুডিওতে স্বাগতম।",
            characterDirective = "Speak fluently in standard Dhaka Bangladeshi Bengali (বাংলা) with broadcast clarity."
        ),
        VoicePreset(
            id = "preset_bn_nabila",
            displayName = "Nabila — Soft Bengali Calm",
            geminiVoiceName = "Vindemiatrix",
            category = VoiceCategory.CALM,
            languageCode = "bn-BD",
            languageName = "Bengali (Bangladesh)",
            countryFlag = "🇧🇩",
            accent = "Soft Dhaka Bengali",
            tone = "Gentle & Intimate",
            description = "Soothing, soft-spoken Bengali voice ideal for calm guidance, bedtime tales, and recitations.",
            tags = listOf("বাংলা", "Calm", "Recitation"),
            previewSampleText = "[whisper] শান্ত হয়ে চোখ বন্ধ করুন, আর শুনুন নিঃশব্দ রাতের সুর।",
            characterDirective = "Speak softly in gentle, soothing Bangladeshi Bengali (বাংলা)."
        ),

        // --- Hindi (हिन्दी) Dedicated Characters ---
        VoicePreset(
            id = "preset_hi_meera",
            displayName = "Meera — Mumbai Conversational",
            geminiVoiceName = "Leda",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "hi-IN",
            languageName = "Hindi",
            countryFlag = "🇮🇳",
            accent = "Modern Hindi",
            tone = "Warm & Agile",
            description = "Natural, contemporary Hindi voice with effortless everyday warmth for creators and assistants.",
            tags = listOf("हिन्दी", "Conversational", "Warm"),
            previewSampleText = "नमस्ते! [happy] VoxCraft स्टूडियो में आपका स्वागत है—अब हर जज़्बात बिल्कुल असली लगेगा।",
            characterDirective = "Speak naturally in authentic contemporary Mumbai/Delhi Hindi (हिन्दी)."
        ),
        VoicePreset(
            id = "preset_hi_kabir",
            displayName = "Kabir — Hindi Cinema Baritone",
            geminiVoiceName = "Fenrir",
            category = VoiceCategory.CHARACTER,
            languageCode = "hi-IN",
            languageName = "Hindi",
            countryFlag = "🇮🇳",
            accent = "Deep North Indian",
            tone = "Rich & Cinematic",
            description = "Deep, magnetic Hindi narrator crafted for dramatic stories, promos, and trailers.",
            tags = listOf("हिन्दी", "Cinema", "Baritone"),
            previewSampleText = "[dramatic] कुछ कहानियाँ सिर्फ़ सुनी नहीं जातीं... उन्हें महसूस किया जाता है।",
            characterDirective = "Speak in deep, cinematic, resonant Hindi (हिन्दी) with dramatic storytelling weight."
        ),
        VoicePreset(
            id = "preset_hi_aarav",
            displayName = "Aarav — Hindi News & Tech",
            geminiVoiceName = "Rasalgethi",
            category = VoiceCategory.BROADCAST,
            languageCode = "hi-IN",
            languageName = "Hindi",
            countryFlag = "🇮🇳",
            accent = "Shuddh Hindi Studio",
            tone = "Sharp & Informative",
            description = "Articulate Hindi broadcast and tech-review voice with crisp diction.",
            tags = listOf("हिन्दी", "Tech", "Broadcast"),
            previewSampleText = "आज हम देखेंगे कि रीयल-टाइम वॉइस एआई किस तरह कंटेंट क्रिएशन बदल रहा है।",
            characterDirective = "Speak clearly in articulate broadcast Hindi (हिन्दी)."
        ),

        // --- More South Asian Languages ---
        VoicePreset(
            id = "preset_ta_kavya",
            displayName = "Kavya — Chennai Expressive",
            geminiVoiceName = "Despina",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "ta-IN",
            languageName = "Tamil",
            countryFlag = "🇮🇳",
            accent = "Chennai Tamil",
            tone = "Smooth & Bright",
            description = "Clear, expressive Tamil voice tuned for storytelling, explainers, and friendly chat.",
            tags = listOf("தமிழ்", "Expressive", "Clear"),
            previewSampleText = "வணக்கம்! VoxCraft AI ஸ்டுடியோவில் உங்கள் குரலை உயிர்ப்பிக்க தயாராகுங்கள்.",
            characterDirective = "Speak naturally in fluent Chennai Tamil (தமிழ்)."
        ),
        VoicePreset(
            id = "preset_te_vihan",
            displayName = "Vihan — Hyderabad Narrator",
            geminiVoiceName = "Gacrux",
            category = VoiceCategory.STORYTELLING,
            languageCode = "te-IN",
            languageName = "Telugu",
            countryFlag = "🇮🇳",
            accent = "Hyderabad Telugu",
            tone = "Warm & Mature",
            description = "Expressive Telugu narrator with natural rhythmic charm for videos and stories.",
            tags = listOf("తెలుగు", "Narrator", "Warm"),
            previewSampleText = "నమస్కారం! VoxCraft AI లో అద్భుతమైన సహజమైన వాయిస్‌లను సృష్టించండి.",
            characterDirective = "Speak naturally in fluent Telugu (తెలుగు)."
        ),
        VoicePreset(
            id = "preset_mr_sai",
            displayName = "Saee — Pune Studio Voice",
            geminiVoiceName = "Callirrhoe",
            category = VoiceCategory.ASSISTANT,
            languageCode = "mr-IN",
            languageName = "Marathi",
            countryFlag = "🇮🇳",
            accent = "Pune Marathi",
            tone = "Clear & Friendly",
            description = "Natural Marathi voice with crisp pronunciation for narration and modern assistants.",
            tags = listOf("मराठी", "Friendly", "Studio"),
            previewSampleText = "नमस्कार! VoxCraft स्टुडिओमध्ये तुमचं मनापासून स्वागत आहे.",
            characterDirective = "Speak naturally in crisp Pune Marathi (मराठी)."
        ),
        VoicePreset(
            id = "preset_ur_zoya",
            displayName = "Zoya — Lahore Poetic",
            geminiVoiceName = "Aoede",
            category = VoiceCategory.STORYTELLING,
            languageCode = "ur-PK",
            languageName = "Urdu",
            countryFlag = "🇵🇰",
            accent = "Classic Urdu",
            tone = "Graceful & Warm",
            description = "Refined, graceful Urdu voice crafted for poetry, drama, and expressive storytelling.",
            tags = listOf("اردو", "Poetic", "Warm"),
            previewSampleText = "السلام علیکم! آج ہم لفظوں کو احساس اور آواز کی نئی زندگی دیں گے۔",
            characterDirective = "Speak gracefully in fluent, poetic Urdu (اردو)."
        ),

        // --- Global Multilingual Voice Characters ---
        VoicePreset(
            id = "preset_es_sofia",
            displayName = "Sofía — Madrid Storyteller",
            geminiVoiceName = "Laomedeia",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "es-ES",
            languageName = "Spanish",
            countryFlag = "🇪🇸",
            accent = "Castilian Spanish",
            tone = "Radiant & Lively",
            description = "Vibrant Spanish voice with expressive warmth for podcasts, reels, and assistants.",
            tags = listOf("Español", "Lively", "Podcast"),
            previewSampleText = "¡Hola! [happy] Bienvenido a VoxCraft AI, donde cada emoción suena completamente real.",
            characterDirective = "Speak fluently in natural Spanish (Español)."
        ),
        VoicePreset(
            id = "preset_es_mateo",
            displayName = "Mateo — CDMX Creator",
            geminiVoiceName = "Puck",
            category = VoiceCategory.CHARACTER,
            languageCode = "es-MX",
            languageName = "Spanish (Mexico)",
            countryFlag = "🇲🇽",
            accent = "Mexican Spanish",
            tone = "Friendly & Dynamic",
            description = "Charismatic Latin American Spanish speaker ideal for entertainment and social media.",
            tags = listOf("Español MX", "Creator", "Upbeat"),
            previewSampleText = "¡Qué tal! [excited] Prueba escribir cualquier emoción entre corchetes y escucha la magia.",
            characterDirective = "Speak naturally in charismatic Mexican Spanish."
        ),
        VoicePreset(
            id = "preset_fr_camille",
            displayName = "Camille — Paris Cinema",
            geminiVoiceName = "Erinome",
            category = VoiceCategory.STORYTELLING,
            languageCode = "fr-FR",
            languageName = "French",
            countryFlag = "🇫🇷",
            accent = "Parisian French",
            tone = "Refined & Velvety",
            description = "Elegant Parisian voice built for luxury narration, cinema essays, and podcasts.",
            tags = listOf("Français", "Paris", "Elegant"),
            previewSampleText = "Bonjour. [warmly] Découvrez une synthèse vocale fluide, nuancée et profondément humaine.",
            characterDirective = "Speak fluently in elegant Parisian French (Français)."
        ),
        VoicePreset(
            id = "preset_de_lukas",
            displayName = "Lukas — Berlin Tech Anchor",
            geminiVoiceName = "Alnilam",
            category = VoiceCategory.BROADCAST,
            languageCode = "de-DE",
            languageName = "German",
            countryFlag = "🇩🇪",
            accent = "Standard German",
            tone = "Precise & Firm",
            description = "Crisp German studio voice tailored for product demos, tech documentaries, and narration.",
            tags = listOf("Deutsch", "Precise", "Tech"),
            previewSampleText = "Willkommen bei VoxCraft AI. Präzise Sprachsynthese mit natürlicher menschlicher Dynamik.",
            characterDirective = "Speak crisply in standard German (Deutsch)."
        ),
        VoicePreset(
            id = "preset_ja_sakura",
            displayName = "Sakura — Tokyo Anime & Studio",
            geminiVoiceName = "Sadachbia",
            category = VoiceCategory.CHARACTER,
            languageCode = "ja-JP",
            languageName = "Japanese",
            countryFlag = "🇯🇵",
            accent = "Tokyo Japanese",
            tone = "Expressive & Clear",
            description = "Nimble, expressive Japanese voice perfect for character acting, narration, and companions.",
            tags = listOf("日本語", "Character", "Tokyo"),
            previewSampleText = "こんにちは！[happy] VoxCraftスタジオへようこそ。感情たっぷりに話せますよ！",
            characterDirective = "Speak naturally in expressive standard Tokyo Japanese (日本語)."
        ),
        VoicePreset(
            id = "preset_ja_haruto",
            displayName = "Haruto — Kyoto Calm Narrator",
            geminiVoiceName = "Algieba",
            category = VoiceCategory.CALM,
            languageCode = "ja-JP",
            languageName = "Japanese",
            countryFlag = "🇯🇵",
            accent = "Calm Japanese",
            tone = "Smooth & Poised",
            description = "Smooth, unhurried Japanese narrator crafted for essays, audiobooks, and calm videos.",
            tags = listOf("日本語", "Calm", "Narrator"),
            previewSampleText = "静かな夜のスタジオで、言葉のひとつひとつに命が吹き込まれていきます。",
            characterDirective = "Speak smoothly in calm, poised Japanese (日本語)."
        ),
        VoicePreset(
            id = "preset_ko_minji",
            displayName = "Min-ji — Seoul Conversational",
            geminiVoiceName = "Autonoe",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "ko-KR",
            languageName = "Korean",
            countryFlag = "🇰🇷",
            accent = "Seoul Korean",
            tone = "Bright & Natural",
            description = "Modern Seoul Korean speaker with warm natural rhythm for vlogs and AI companions.",
            tags = listOf("한국어", "Seoul", "Modern"),
            previewSampleText = "안녕하세요! [warmly] VoxCraft AI에서 생생하고 자연스러운 음성을 만들어 보세요.",
            characterDirective = "Speak naturally in modern Seoul Korean (한국어)."
        ),
        VoicePreset(
            id = "preset_zh_lin",
            displayName = "Lin — Mandarin Broadcast",
            geminiVoiceName = "Iapetus",
            category = VoiceCategory.BROADCAST,
            languageCode = "zh-CN",
            languageName = "Mandarin Chinese",
            countryFlag = "🇨🇳",
            accent = "Standard Mandarin",
            tone = "Clear & Direct",
            description = "Crisp Mandarin studio voice designed for explainers, narration, and global media.",
            tags = listOf("中文", "Clear", "Studio"),
            previewSampleText = "你好！欢迎来到 VoxCraft 语音工作室，体验逼真的情感语音合成。",
            characterDirective = "Speak clearly in standard Mandarin Chinese (中文)."
        ),
        VoicePreset(
            id = "preset_ar_layla",
            displayName = "Layla — Arabic Storyteller",
            geminiVoiceName = "Sadaltager",
            category = VoiceCategory.STORYTELLING,
            languageCode = "ar-SA",
            languageName = "Arabic",
            countryFlag = "🇸🇦",
            accent = "Modern Standard Arabic",
            tone = "Resonant & Warm",
            description = "Eloquent Arabic narrator tuned for storytelling, podcasts, and documentaries.",
            tags = listOf("العربية", "Story", "Warm"),
            previewSampleText = "أهلاً بك في استوديو فوكس كرافت الصوتي، حيث تتحول الكلمات إلى صوت نابض بالحياة.",
            characterDirective = "Speak eloquently in natural Modern Standard Arabic (العربية)."
        ),
        VoicePreset(
            id = "preset_pt_rafael",
            displayName = "Rafael — São Paulo Host",
            geminiVoiceName = "Pulcherrima",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "pt-BR",
            languageName = "Portuguese (Brazil)",
            countryFlag = "🇧🇷",
            accent = "Brazilian Portuguese",
            tone = "Confident & Warm",
            description = "Charismatic Brazilian Portuguese host ideal for podcasts, videos, and launches.",
            tags = listOf("Português", "Brazil", "Host"),
            previewSampleText = "Olá! [excited] Bem-vindo ao VoxCraft AI—crie vozes incrivelmente humanas em segundos.",
            characterDirective = "Speak naturally in warm Brazilian Portuguese."
        ),
        VoicePreset(
            id = "preset_it_elena",
            displayName = "Elena — Milan Expressive",
            geminiVoiceName = "Achird",
            category = VoiceCategory.CONVERSATIONAL,
            languageCode = "it-IT",
            languageName = "Italian",
            countryFlag = "🇮🇹",
            accent = "Italian Studio",
            tone = "Warm & Expressive",
            description = "Expressive Italian voice with natural musical cadence for narration and lifestyle media.",
            tags = listOf("Italiano", "Warm", "Expressive"),
            previewSampleText = "Ciao! [warmly] Benvenuto in VoxCraft Studio, pronto a dare anima a ogni parola.",
            characterDirective = "Speak warmly in natural Italian (Italiano)."
        ),
        VoicePreset(
            id = "preset_enceladus",
            displayName = "Enceladus — Midnight Whisper",
            geminiVoiceName = "Enceladus",
            category = VoiceCategory.CALM,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "Close-Mic Soft",
            tone = "Breathy & Gentle",
            description = "Intimate, soft-spoken breathy tone tailored for mindfulness, ASMR, and late-night narration.",
            tags = listOf("Breathy", "Mindfulness", "ASMR"),
            previewSampleText = "[whisper] Take a slow breath in, let your shoulders drop, and feel the stillness."
        ),
        VoicePreset(
            id = "preset_umbriel",
            displayName = "Umbriel — Noir Detective",
            geminiVoiceName = "Umbriel",
            category = VoiceCategory.CHARACTER,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "Velvet Low",
            tone = "Deep & Cinematic",
            description = "Smooth low-register character voice ideal for mystery audio dramas and noir thrillers.",
            tags = listOf("Noir", "Deep", "Character"),
            previewSampleText = "Rain tapped against the studio glass while the tape reels spun quietly into the dark."
        ),
        VoicePreset(
            id = "preset_algenib",
            displayName = "Algenib — Gravelly Legend",
            geminiVoiceName = "Algenib",
            category = VoiceCategory.CHARACTER,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "Textured Low",
            tone = "Gravelly & Iconic",
            description = "Textured, weathered character voice packed with grit for RPG quests and dramatic promos.",
            tags = listOf("Gravelly", "RPG", "Bold"),
            previewSampleText = "[confident] We've walked through louder storms than this—turn the gain up."
        ),
        VoicePreset(
            id = "preset_schedar",
            displayName = "Schedar — Studio Concierge",
            geminiVoiceName = "Schedar",
            category = VoiceCategory.ASSISTANT,
            languageCode = "en-US",
            languageName = "English (US)",
            countryFlag = "🇺🇸",
            accent = "Neutral Studio",
            tone = "Even & Dependable",
            description = "Dependable, even-keeled AI assistant voice that stays crystal clear across long scripts.",
            tags = listOf("Assistant", "Clean", "Technical"),
            previewSampleText = "Every audio clip can be saved directly to your device or shared in one tap."
        )
    )

    fun toStudioVoice(preset: VoicePreset): StudioVoice = StudioVoice(
        id = preset.id,
        displayName = preset.displayName,
        geminiVoiceName = preset.geminiVoiceName,
        isCloned = false,
        categoryLabel = preset.category.label,
        languageCode = preset.languageCode,
        languageName = preset.languageName,
        countryFlag = preset.countryFlag,
        accent = preset.accent,
        description = preset.description,
        tags = preset.tags,
        acousticDnaPrompt = preset.characterDirective,
        waveformSignature = preset.waveformSignature
    )
}
