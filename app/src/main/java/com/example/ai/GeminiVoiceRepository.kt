package com.example.ai

import android.util.Log
import com.example.data.PerformanceMode
import com.example.model.AcousticDnaAnalysis
import com.example.model.AutoDetectLanguageOption
import com.example.model.EmotionPreset
import com.example.model.HumanizationConfig
import com.example.model.StudioLanguage
import com.example.model.StudioVoice
import com.example.model.SupportedStudioLanguages
import com.example.model.VoiceCatalog
import com.example.model.extractBracketedEmotions
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiVoiceRepo"
private const val TTS_MODEL_NAME = "gemini-3.1-flash-tts-preview"
private const val ANALYSIS_MODEL_NAME = "gemini-3.8-flash"

data class SynthesizedAudioPayload(
    val bytes: ByteArray,
    val mimeType: String,
    val latencyMs: Long
)

class GeminiVoiceRepository {

    private val humanizerModel by lazy {
        Firebase.ai.generativeModel(modelName = ANALYSIS_MODEL_NAME)
    }

    private val voiceAnalysisSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "suggestedName" to Schema.string(
                    description = "A sleek 2-3 word studio voice name capturing the speaker persona"
                ),
                "closestBaseVoice" to Schema.enumeration(
                    values = VoiceCatalog.geminiVoiceNames,
                    description = "Single closest matching Gemini neural voice anchor matching speaker gender, pitch register, warmth, and grain"
                ),
                "detectedLanguage" to Schema.string(
                    description = "The primary spoken language detected from the audio clip (e.g. Bengali (India), Bengali (Bangladesh), Hindi, English (India), English (US), Spanish)"
                ),
                "detectedLanguageCode" to Schema.enumeration(
                    values = SupportedStudioLanguages.map { it.code },
                    description = "Matching BCP-47 language code for the detected spoken language"
                ),
                "detectedAccent" to Schema.string(
                    description = "Exact regional accent & dialect detected from listening to the speaker's pronunciation, vowel coloration, and stress patterns (e.g. Kolkata Bengali accent, Dhaka Bengali accent, Mumbai Hindi accent, South Indian English, British RP, Southern US)"
                ),
                "pitchDescriptor" to Schema.string(
                    description = "Speaker vocal gender & register, e.g. Warm Male Baritone, Crisp Female Alto, Deep Bass-Baritone, Bright Tenor"
                ),
                "paceDescriptor" to Schema.string(
                    description = "Speaking rhythm and pause cadence, e.g. Measured Storyteller, Rapid Conversational, Relaxed Melodic"
                ),
                "timbreSummary" to Schema.string(
                    description = "Concise one-sentence biometric summary of vocal texture, resonance, nasal/chest balance, and speaker identity"
                ),
                "acousticDnaPrompt" to Schema.string(
                    description = "Hyper-detailed speaker imitation & accent preservation directive describing exact regional accent, consonant/vowel articulation, pitch contour, vocal weight, breath texture, micro-pauses, and cadence so synthesized speech faithfully mirrors this exact speaker"
                ),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 short descriptive style tags"
                ),
                "defaultSpeed" to Schema.double(
                    description = "Speaker's natural speaking speed multiplier between 0.75 and 1.35"
                )
            )
        )
    }

    private val voiceProfilerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ANALYSIS_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceAnalysisSchema
            }
        )
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun synthesizeSpeech(
        script: String,
        voice: StudioVoice,
        config: HumanizationConfig,
        performanceMode: PerformanceMode = PerformanceMode.HIGH_PERFORMANCE
    ): SynthesizedAudioPayload {
        val startMs = System.currentTimeMillis()
        try {
            val ttsModel = Firebase.ai.generativeModel(
                modelName = TTS_MODEL_NAME,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(
                        voice = Voice(voice.geminiVoiceName)
                    )
                }
            )

            val prompt = buildTtsDirectorPrompt(script, voice, config, performanceMode)
            val response = withTimeout(60_000L) {
                ttsModel.generateContent(prompt)
            }
            val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>().firstOrNull()
                ?: throw IllegalStateException(
                    "Voice synthesizer returned no audio stream. Please check connection and retry."
                )

            if (audioPart.inlineData.isEmpty()) {
                throw IllegalStateException("Synthesized audio buffer was empty.")
            }

            return SynthesizedAudioPayload(
                bytes = audioPart.inlineData,
                mimeType = audioPart.mimeType.ifBlank { "audio/L16;codec=pcm;rate=24000" },
                latencyMs = latencyMs
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Speech synthesis timed out", e)
            throw IllegalStateException("Speech synthesis timed out after 60 seconds. Try a shorter passage or retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Speech synthesis failed", e)
            throw IllegalStateException(formatFirebaseError(e, "Speech generation failed."))
        }
    }

    suspend fun humanizeScript(
        rawScript: String,
        config: HumanizationConfig
    ): String {
        try {
            val prompt = buildString {
                appendLine("You are a master multilingual voice director and speech script humanizer.")
                appendLine("Rewrite the script below so it sounds genuinely human, expressive, and natural when spoken in ${config.language}.")
                appendLine("Rules:")
                appendLine("- Keep the EXACT language, script alphabet (e.g., Bengali বাংলা, Hindi हिन्दी, Latin, etc.), and core meaning unchanged.")
                appendLine("- Preserve any existing [emotion] bracket tags the user wrote, and optionally add 1-2 expressive inline bracket tags like [warmly], [pause 0.5s], [happy], [whisper], or [laughter] where emotionally fitting.")
                appendLine("- Match target delivery mood: ${config.emotionPreset.label} (${config.emotionPreset.directive}).")
                appendLine("- Output ONLY the rewritten spoken script with zero quotes, headers, or explanation.")
                appendLine()
                appendLine("Original script:")
                append(rawScript.trim())
            }

            val response = withTimeout(45_000L) {
                humanizerModel.generateContent(prompt)
            }
            val rewritten = response.text?.trim()
            if (rewritten.isNullOrBlank()) {
                throw IllegalStateException("Humanizer returned an empty script.")
            }
            return rewritten
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Script humanization timed out", e)
            throw IllegalStateException("Script humanization timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Script humanization failed", e)
            throw IllegalStateException(formatFirebaseError(e, "Failed to humanize script."))
        }
    }

    suspend fun analyzeVoiceSample(
        audioBytes: ByteArray,
        mimeType: String,
        preferredLanguage: StudioLanguage = AutoDetectLanguageOption
    ): AcousticDnaAnalysis {
        try {
            val languageInstruction = if (preferredLanguage.code == AutoDetectLanguageOption.code) {
                "Auto-detect the exact spoken language AND regional accent directly from the speaker's audio."
            } else {
                "The user intends to use this cloned voice primarily in ${preferredLanguage.name} (${preferredLanguage.nativeName}), " +
                    "AND you MUST still auto-detect and strictly preserve the speaker's authentic accent, vocal timbre, gender register, pitch contour, and speech rhythm from the audio sample."
            }

            val response = withTimeout(60_000L) {
                voiceProfilerModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(
                            "Perform forensic acoustic voice-cloning profiling on this audio clip. " +
                                "$languageInstruction " +
                                "Carefully match speaker gender, vocal weight, resonance, pitch range, regional accent, vowel/consonant habits, " +
                                "breath texture, and speaking cadence so synthesized speech reproduces this exact speaker faithfully. " +
                                "Select the closest matching Gemini neural voice anchor from the enum."
                        )
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Voice profiler returned an empty analysis.")

            val json = JSONObject(jsonText)
            val rawTags = json.optJSONArray("tags")
            val tags = buildList {
                if (rawTags != null) {
                    for (i in 0 until rawTags.length()) {
                        val t = rawTags.optString(i).trim()
                        if (t.isNotEmpty()) add(t)
                    }
                }
            }.ifEmpty { listOf("Cloned", "Exact Accent", "Natural") }

            val baseVoice = json.optString("closestBaseVoice", "Kore").let { candidate ->
                if (candidate in VoiceCatalog.geminiVoiceNames) candidate else "Kore"
            }

            val detectedLangCode = json.optString("detectedLanguageCode", "en-US").let { code ->
                if (SupportedStudioLanguages.any { it.code == code }) code else "en-US"
            }
            val matchedLang = SupportedStudioLanguages.find { it.code == detectedLangCode }
                ?: SupportedStudioLanguages.first()

            val finalLanguage = if (preferredLanguage.code != AutoDetectLanguageOption.code) {
                preferredLanguage
            } else {
                matchedLang
            }

            return AcousticDnaAnalysis(
                suggestedName = json.optString("suggestedName", "My Cloned Voice").ifBlank { "My Cloned Voice" },
                closestBaseVoice = baseVoice,
                detectedLanguage = json.optString("detectedLanguage", finalLanguage.name).ifBlank { finalLanguage.name },
                detectedLanguageCode = finalLanguage.code,
                detectedAccent = json.optString("detectedAccent", "Natural Native Accent").ifBlank { "Natural Native Accent" },
                pitchDescriptor = json.optString("pitchDescriptor", "Balanced Studio Register"),
                paceDescriptor = json.optString("paceDescriptor", "Natural Cadence"),
                timbreSummary = json.optString("timbreSummary", "Authentic, expressive cloned speaker profile."),
                acousticDnaPrompt = json.optString(
                    "acousticDnaPrompt",
                    "Faithfully mirror the speaker's exact natural accent, vocal weight, resonance, and rhythmic cadence."
                ),
                tags = tags.take(3),
                defaultSpeed = json.optDouble("defaultSpeed", 1.0).toFloat().coerceIn(0.75f, 1.35f)
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice cloning analysis timed out", e)
            throw IllegalStateException("Voice analysis timed out after 60 seconds. Try a 10–30 second sample.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice cloning analysis failed", e)
            throw IllegalStateException(formatFirebaseError(e, "Voice analysis failed."))
        }
    }

    private fun buildTtsDirectorPrompt(
        script: String,
        voice: StudioVoice,
        config: HumanizationConfig,
        performanceMode: PerformanceMode
    ): String {
        val detectedInlineCues = extractBracketedEmotions(script)

        val paceLabel = when {
            config.speed <= 0.75f -> "slow, deliberate pacing (${String.format("%.2fx", config.speed)})"
            config.speed <= 0.92f -> "relaxed, slightly unhurried pacing (${String.format("%.2fx", config.speed)})"
            config.speed <= 1.08f -> "natural conversational pacing (${String.format("%.2fx", config.speed)})"
            config.speed <= 1.28f -> "brisk, energetic pacing (${String.format("%.2fx", config.speed)})"
            else -> "fast, snappy pacing (${String.format("%.2fx", config.speed)})"
        }

        val emotionAxes = buildList {
            if (config.positivity >= 0.45f) add("warmth/positivity ${(config.positivity * 100).toInt()}%")
            if (config.curiosity >= 0.45f) add("curiosity ${(config.curiosity * 100).toInt()}%")
            if (config.surprise >= 0.35f) add("surprise ${(config.surprise * 100).toInt()}%")
            if (config.sadness >= 0.30f) add("melancholy ${(config.sadness * 100).toInt()}%")
            add("expressive intensity ${(config.intensity * 100).toInt()}%")
        }.joinToString(", ")

        val breathDirective = if (config.naturalBreathsEnabled) {
            "Include subtle human micro-breaths, natural phrase tapering, and organic vocal inflection."
        } else {
            "Keep delivery ultra-clean and crisp with minimal breath sounds."
        }

        val moodDirective = if (config.emotionPreset == EmotionPreset.CUSTOM) {
            "Custom emotional blend ($emotionAxes)"
        } else {
            "${config.emotionPreset.label}: ${config.emotionPreset.directive} ($emotionAxes)"
        }

        return buildString {
            appendLine("# AUDIO PERFORMANCE DIRECTION")
            appendLine("Target Spoken Language: ${config.language} [${config.languageCode}]. Speak naturally in this language.")
            if (voice.isCloned) {
                appendLine("CRITICAL SPEAKER CLONE & ACCENT LOCK: Strictly preserve the cloned speaker's auto-detected accent (${voice.accent}), pitch register, timbre, rhythm, and cadence.")
                if (voice.acousticDnaPrompt.isNotBlank()) {
                    appendLine("Cloned Speaker Biometric DNA: ${voice.acousticDnaPrompt}")
                }
            } else {
                appendLine("Accent & Character: ${voice.accent}.")
                if (voice.acousticDnaPrompt.isNotBlank()) {
                    appendLine("Voice Character Style: ${voice.acousticDnaPrompt}")
                }
            }
            appendLine("Pacing: $paceLabel.")
            appendLine("Baseline Emotion: $moodDirective.")
            if (performanceMode != PerformanceMode.ULTRA_LOW_LATENCY) {
                appendLine("Humanization Realism: $breathDirective")
            }
            if (detectedInlineCues.isNotEmpty()) {
                val cueList = detectedInlineCues.joinToString(", ") { "[$it]" }
                appendLine("INLINE BRACKETED EMOTION CUES DETECTED: $cueList.")
                appendLine("CRITICAL EMOTION RULE: Every text inside square brackets [like this] is a live emotional/acting direction. Immediately shift your vocal tone, pitch, energy, breath, and emotion to embody that exact feeling or sound effect (e.g. genuine laughter for [laughter], tearful tremor for [crying], hushed breathy voice for [whisper], joyful smile for [happy], fierce intensity for [angry], sorrowful cadence for [sad], timed silence for [pause 0.5s]). NEVER speak the bracketed words aloud.")
            } else {
                appendLine("Any text inside [square brackets] is a vocal emotion/delivery cue—perform it vocally and never read the bracketed words aloud.")
            }
            appendLine("Read ONLY the transcript below faithfully:")
            appendLine()
            append(script.trim())
        }
    }

    private fun formatFirebaseError(e: Exception, fallback: String): String {
        val raw = e.localizedMessage.orEmpty()
        if (raw.contains("Default FirebaseApp is not initialized", ignoreCase = true)) {
            return "Firebase is not initialized in this APK because app/google-services.json was missing during build. Add the GOOGLE_SERVICES_JSON secret in GitHub Actions and rebuild."
        }
        return raw.ifBlank { fallback }
    }
}
