package com.example.data

import com.example.audio.LocalAudioEngine
import com.example.data.local.ClonedVoiceEntity
import com.example.data.local.FavoriteVoiceEntity
import com.example.data.local.GenerationEntity
import com.example.data.local.StudioDatabase
import com.example.model.StudioVoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StudioRepository(
    private val database: StudioDatabase,
    private val audioEngine: LocalAudioEngine
) {
    val clonedVoices: Flow<List<ClonedVoiceEntity>> = database.clonedVoiceDao().observeAll()

    val generations: Flow<List<GenerationEntity>> = database.generationDao().observeAll()

    val favoriteVoiceIds: Flow<Set<String>> = database.favoriteVoiceDao()
        .observeFavoriteIds()
        .map { it.toSet() }

    suspend fun saveClonedVoice(entity: ClonedVoiceEntity) {
        database.clonedVoiceDao().upsert(entity)
    }

    suspend fun deleteClonedVoice(entity: ClonedVoiceEntity) {
        audioEngine.deleteAudioFile(entity.sampleAudioPath)
        database.clonedVoiceDao().deleteById(entity.id)
        database.favoriteVoiceDao().removeFavorite(entity.id)
    }

    suspend fun saveGeneration(entity: GenerationEntity) {
        database.generationDao().insert(entity)
    }

    suspend fun deleteGeneration(entity: GenerationEntity) {
        audioEngine.deleteAudioFile(entity.audioFilePath)
        database.generationDao().deleteById(entity.id)
    }

    suspend fun toggleFavorite(voiceId: String, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            database.favoriteVoiceDao().removeFavorite(voiceId)
        } else {
            database.favoriteVoiceDao().addFavorite(FavoriteVoiceEntity(voiceId = voiceId))
        }
    }

    fun toStudioVoice(clone: ClonedVoiceEntity): StudioVoice = StudioVoice(
        id = clone.id,
        displayName = clone.name,
        geminiVoiceName = clone.baseGeminiVoice,
        isCloned = true,
        categoryLabel = "My Clone",
        languageCode = clone.languageCode,
        languageName = clone.languageName,
        countryFlag = clone.countryFlag,
        accent = clone.accent,
        description = clone.timbreSummary,
        tags = clone.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        acousticDnaPrompt = clone.acousticDnaPrompt,
        defaultSpeed = clone.defaultSpeed,
        waveformSignature = parsePeaks(clone.waveformPeaksCsv),
        sampleAudioPath = clone.sampleAudioPath
    )

    companion object {
        fun encodePeaks(peaks: List<Float>): String =
            peaks.joinToString(",") { String.format("%.3f", it) }

        fun parsePeaks(csv: String): List<Float> =
            csv.split(",").mapNotNull { it.trim().toFloatOrNull() }
                .ifEmpty { List(28) { 0.35f } }
    }
}
