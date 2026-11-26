package com.example.ai

import android.util.Log
import com.example.model.AcousticDnaAnalysis
import com.example.model.EmotionPreset
import com.example.model.HumanizationConfig
import com.example.model.StudioVoice
import com.example.model.VoiceCatalog
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
                "suggestedName" to Schema.string(description = "A sleek 2-3 word studio voice name capturing the speaker vibe"),
                "closestBaseVoice" to Schema.enumeration(
                    values = VoiceCatalog.geminiVoiceNames,
                    description = "Closest matching Gemini neural voice anchor based on vocal register, warmth, and timbre"
                ),
                "accent" to Schema.string(description = "Detected regional accent or locale, e.g. US Studio, UK Soft, Indian English"),
                "pitchDescriptor" to Schema.string(description = "Concise vocal register description, e.g. Warm Baritone, Bright Alto, Clear Tenor"),
                "paceDescriptor" to Schema.string(description = "Concise speaking rhythm description, e.g. Unhurried Conversational, Crisp Upbeat"),
                "timbreSummary" to Schema.string(description = "One-sentence summary of vocal texture, resonance, and vocal personality"),
                "acousticDnaPrompt" to Schema.string(description = "Detailed vocal performance directive describing timbre, breathiness, inflection, phrasing, and cadence so TTS reproduces the speaker's style"),
                "tags" to Schema.array(
                    items = Schema.string(),
                    description = "3 short descriptive style tags"
                ),
                "defaultSpeed" to Schema.double(description = "Recommended natural speed multiplier between 0.8 and 1.25")
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
        config: HumanizationConfig
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

            val prompt = buildTtsDirectorPrompt(script, voice, config)
            val response = withTimeout(60_000L) {
                ttsModel.generateContent(prompt)
            }
            val latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

            val parts = response.candidates.firstOrNull()?.content?.parts.orEmpty()
            val audioPart = parts.filterIsInstance<InlineDataPart>().firstOrNull()
                ?: throw IllegalStateException(
                    "Voice synthesizer returned no audio stream. Please verify connection and try again."
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
            throw IllegalStateException("Speech synthesis timed out after 60 seconds. Please try a shorter passage or retry.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Speech synthesis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Speech generation failed.")
        }
    }

    suspend fun humanizeScript(
        rawScript: String,
        config: HumanizationConfig
    ): String {
        try {
            val prompt = buildString {
                appendLine("You are a master voice director and speech script humanizer.")
                appendLine("Rewrite the following script so it sounds extraordinarily natural, human, and expressive when spoken aloud.")
                appendLine("Rules:")
                appendLine("- Preserve the exact meaning, facts, and language (${config.language}).")
                appendLine("- Add natural conversational cadence, contractions where fitting, subtle em-dashes or ellipses for organic pauses, and at most 1-2 fitting non-verbal cues like [pause 0.5s], [warmly], or [laughter] ONLY if emotionally appropriate.")
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
            throw IllegalStateException(e.localizedMessage ?: "Failed to humanize script.")
        }
    }

    suspend fun analyzeVoiceSample(
        audioBytes: ByteArray,
        mimeType: String
    ): AcousticDnaAnalysis {
        try {
            val response = withTimeout(60_000L) {
                voiceProfilerModel.generateContent(
                    content {
                        inlineData(audioBytes, mimeType)
                        text(
                            "Listen closely to this voice recording and extract the speaker's acoustic DNA profile " +
                                "for neural voice cloning. Carefully identify vocal register/pitch, timbre/warmth, speaking rate, " +
                                "accent, breathing/pausing patterns, and pick the single closest neural anchor voice from the enum."
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
            }.ifEmpty { listOf("Cloned", "Custom", "Natural") }

            val baseVoice = json.optString("closestBaseVoice", "Kore").let { candidate ->
                if (candidate in VoiceCatalog.geminiVoiceNames) candidate else "Kore"
            }

            return AcousticDnaAnalysis(
                suggestedName = json.optString("suggestedName", "My Studio Voice").ifBlank { "My Studio Voice" },
                closestBaseVoice = baseVoice,
                accent = json.optString("accent", "Natural Studio").ifBlank { "Natural Studio" },
                pitchDescriptor = json.optString("pitchDescriptor", "Balanced Mid-Range"),
                paceDescriptor = json.optString("paceDescriptor", "Natural Cadence"),
                timbreSummary = json.optString("timbreSummary", "Warm, expressive vocal presence."),
                acousticDnaPrompt = json.optString(
                    "acousticDnaPrompt",
                    "Speak with natural warmth, organic pacing, and authentic conversational phrasing."
                ),
                tags = tags.take(3),
                defaultSpeed = json.optDouble("defaultSpeed", 1.0).toFloat().coerceIn(0.75f, 1.35f)
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice cloning analysis timed out", e)
            throw IllegalStateException("Voice analysis timed out after 60 seconds. Please try a 10–30 second sample.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice cloning analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Voice analysis failed.")
        }
    }

    private fun buildTtsDirectorPrompt(
        script: String,
        voice: StudioVoice,
        config: HumanizationConfig
    ): String {
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
            "Include subtle human micro-breaths, natural phrase tapering, and organic inflection."
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
            appendLine("Language & Accent: ${config.language} (${voice.accent}).")
            appendLine("Pacing: $paceLabel.")
            appendLine("Emotion & Humanization: $moodDirective.")
            appendLine("Realism: $breathDirective")
            if (voice.isCloned && voice.acousticDnaPrompt.isNotBlank()) {
                appendLine("Cloned Speaker Acoustic DNA: ${voice.acousticDnaPrompt}")
            }
            appendLine("Bracketed cues like [laughter], [sigh], [pause 0.5s], [whisper], [excited], [warmly], [slowly] are performance stage directions—perform them naturally as real vocal effects without speaking the bracketed words aloud.")
            appendLine("Read ONLY the transcript below verbatim:")
            appendLine()
            append(script.trim())
        }
    }
}
