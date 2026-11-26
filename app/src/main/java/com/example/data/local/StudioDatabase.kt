package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cloned_voices")
data class ClonedVoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseGeminiVoice: String,
    val acousticDnaPrompt: String,
    val pitchDescriptor: String,
    val paceDescriptor: String,
    val timbreSummary: String,
    val accent: String,
    val languageCode: String = "en-US",
    val languageName: String = "English (US)",
    val countryFlag: String = "🇺🇸",
    val tagsCsv: String,
    val defaultSpeed: Float,
    val sampleAudioPath: String,
    val waveformPeaksCsv: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "generations")
data class GenerationEntity(
    @PrimaryKey val id: String,
    val scriptText: String,
    val voiceId: String,
    val voiceName: String,
    val isClonedVoice: Boolean,
    val emotionPreset: String,
    val speed: Float,
    val volume: Float,
    val language: String,
    val countryFlag: String = "🇺🇸",
    val audioFilePath: String,
    val durationMs: Long,
    val latencyMs: Long,
    val waveformPeaksCsv: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_voices")
data class FavoriteVoiceEntity(
    @PrimaryKey val voiceId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface ClonedVoiceDao {
    @Query("SELECT * FROM cloned_voices ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ClonedVoiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(voice: ClonedVoiceEntity)

    @Query("DELETE FROM cloned_voices WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface GenerationDao {
    @Query("SELECT * FROM generations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GenerationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(generation: GenerationEntity)

    @Query("DELETE FROM generations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FavoriteVoiceDao {
    @Query("SELECT voiceId FROM favorite_voices")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteVoiceEntity)

    @Query("DELETE FROM favorite_voices WHERE voiceId = :voiceId")
    suspend fun removeFavorite(voiceId: String)
}

@Database(
    entities = [
        ClonedVoiceEntity::class,
        GenerationEntity::class,
        FavoriteVoiceEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class StudioDatabase : RoomDatabase() {
    abstract fun clonedVoiceDao(): ClonedVoiceDao
    abstract fun generationDao(): GenerationDao
    abstract fun favoriteVoiceDao(): FavoriteVoiceDao

    companion object {
        @Volatile
        private var instance: StudioDatabase? = null

        fun getInstance(context: Context): StudioDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    StudioDatabase::class.java,
                    "voxcraft_studio.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
