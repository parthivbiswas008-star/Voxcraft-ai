package com.example.audio

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.AudioPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

private const val TAG = "LocalAudioEngine"
private const val RECORD_SAMPLE_RATE = 16000
private const val MAX_INLINE_AUDIO_BYTES = 18 * 1024 * 1024

data class SavedAudioClip(
    val file: File,
    val mimeType: String,
    val durationMs: Long,
    val waveformPeaks: List<Float>
)

data class LiveRecordingState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0L,
    val currentLevel: Float = 0f,
    val liveWaveform: List<Float> = List(48) { 0.08f }
)

class LocalAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }

    private val _playbackState = MutableStateFlow(AudioPlaybackState())
    val playbackState: StateFlow<AudioPlaybackState> = _playbackState.asStateFlow()

    private val _recordingState = MutableStateFlow(LiveRecordingState())
    val recordingState: StateFlow<LiveRecordingState> = _recordingState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    @Volatile
    private var pollingIntervalMs: Long = 40L

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    private var recordedPcmStream: ByteArrayOutputStream? = null
    private var recordingStartMs: Long = 0L

    fun setPollingIntervalMs(intervalMs: Long) {
        pollingIntervalMs = intervalMs.coerceIn(16L, 100L)
    }

    @SuppressLint("MissingPermission")
    fun startRecording(onError: (String) -> Unit) {
        if (_recordingState.value.isRecording) return
        stopPlayback()

        val minBuf = AudioRecord.getMinBufferSize(
            RECORD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            onError("Unable to initialize microphone hardware on this device.")
            return
        }

        val bufferSize = maxOf(minBuf * 2, 4096)
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RECORD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission missing", e)
            onError("Microphone permission is required to record a voice sample.")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord", e)
            onError(e.localizedMessage ?: "Failed to access microphone.")
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            onError("Microphone could not be initialized. Try importing an audio file instead.")
            return
        }

        audioRecord = recorder
        val pcmOut = ByteArrayOutputStream()
        recordedPcmStream = pcmOut
        recordingStartMs = System.currentTimeMillis()

        _recordingState.value = LiveRecordingState(
            isRecording = true,
            elapsedMs = 0L,
            currentLevel = 0.05f,
            liveWaveform = List(48) { 0.08f }
        )

        recordJob = scope.launch(Dispatchers.IO) {
            try {
                recorder.startRecording()
                val shortBuf = ShortArray(1024)
                val byteBuf = ByteArray(shortBuf.size * 2)
                var lastUiUpdate = 0L

                while (isActive && _recordingState.value.isRecording) {
                    val read = recorder.read(shortBuf, 0, shortBuf.size)
                    if (read > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until read) {
                            val s = shortBuf[i]
                            sumSquares += (s.toDouble() * s.toDouble())
                            byteBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                            byteBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        synchronized(pcmOut) {
                            pcmOut.write(byteBuf, 0, read * 2)
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate >= 45L) {
                            lastUiUpdate = now
                            val rms = sqrt(sumSquares / read) / 32768.0
                            val normalized = (rms.toFloat() * 4.2f).coerceIn(0.06f, 1.0f)
                            val elapsed = now - recordingStartMs
                            _recordingState.update { prev ->
                                prev.copy(
                                    elapsedMs = elapsed,
                                    currentLevel = normalized,
                                    liveWaveform = (prev.liveWaveform.drop(1) + normalized)
                                )
                            }
                            if (elapsed >= 60_000L) {
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during microphone recording", e)
            }
        }
    }

    suspend fun stopRecordingAndSave(): SavedAudioClip? = withContext(Dispatchers.IO) {
        _recordingState.update { it.copy(isRecording = false) }
        recordJob?.cancel()
        recordJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord stop warning", e)
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "AudioRecord release warning", e)
        }
        audioRecord = null

        val pcmBytes = recordedPcmStream?.let { stream ->
            synchronized(stream) { stream.toByteArray() }
        } ?: ByteArray(0)
        recordedPcmStream = null

        if (pcmBytes.size < 3200) {
            return@withContext null
        }

        val wavBytes = encodePcmToWav(
            pcmData = pcmBytes,
            sampleRate = RECORD_SAMPLE_RATE,
            channels = 1,
            bitsPerSample = 16
        )
        val outFile = File(audioDir, "clone_sample_${UUID.randomUUID()}.wav")
        FileOutputStream(outFile).use { it.write(wavBytes) }

        val durationMs = ((pcmBytes.size.toLong() / 2L) * 1000L) / RECORD_SAMPLE_RATE
        val peaks = extractPcm16WaveformPeaks(pcmBytes, barCount = 48)
        SavedAudioClip(
            file = outFile,
            mimeType = "audio/wav",
            durationMs = durationMs,
            waveformPeaks = peaks
        )
    }

    suspend fun importAudioUri(uri: Uri): Result<SavedAudioClip> = withContext(Dispatchers.IO) {
        try {
            val resolvedMime = context.contentResolver.getType(uri) ?: "audio/wav"
            val ext = when {
                resolvedMime.contains("mpeg") || resolvedMime.contains("mp3") -> "mp3"
                resolvedMime.contains("mp4") || resolvedMime.contains("m4a") || resolvedMime.contains("aac") -> "m4a"
                resolvedMime.contains("ogg") -> "ogg"
                else -> "wav"
            }
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_INLINE_AUDIO_BYTES) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Audio sample exceeds 18 MB limit. Please select a clip under 60 seconds.")
                        )
                    }
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: return@withContext Result.failure(IllegalStateException("Could not read selected audio file."))

            if (rawBytes.size < 512) {
                return@withContext Result.failure(IllegalArgumentException("Selected audio file appears empty."))
            }

            val outFile = File(audioDir, "clone_import_${UUID.randomUUID()}.$ext")
            FileOutputStream(outFile).use { it.write(rawBytes) }

            val durationMs = probeDurationMs(outFile)
            val peaks = if (isWavBytes(rawBytes) && rawBytes.size > 44) {
                extractPcm16WaveformPeaks(rawBytes.copyOfRange(44, rawBytes.size), barCount = 48)
            } else {
                extractGenericWaveformPeaks(rawBytes, barCount = 48)
            }

            Result.success(
                SavedAudioClip(
                    file = outFile,
                    mimeType = resolvedMime,
                    durationMs = durationMs,
                    waveformPeaks = peaks
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import audio file", e)
            Result.failure(e)
        }
    }

    suspend fun saveSynthesizedAudio(
        audioBytes: ByteArray,
        mimeType: String,
        volumeGain: Float = 1.0f
    ): SavedAudioClip = withContext(Dispatchers.IO) {
        val lowerMime = mimeType.lowercase()
        val isRawPcm = lowerMime.contains("pcm") || lowerMime.contains("l16") ||
            (!isWavBytes(audioBytes) && !isMp3Bytes(audioBytes) && !isOggBytes(audioBytes) && !isMp4Bytes(audioBytes))

        val finalBytes: ByteArray
        val finalExt: String
        val finalMime: String
        val peaks: List<Float>
        var durationMs: Long

        if (isRawPcm) {
            val sampleRate = parseSampleRateFromMime(mimeType) ?: 24000
            val gainedPcm = applyPcm16Gain(audioBytes, volumeGain)
            finalBytes = encodePcmToWav(
                pcmData = gainedPcm,
                sampleRate = sampleRate,
                channels = 1,
                bitsPerSample = 16
            )
            finalExt = "wav"
            finalMime = "audio/wav"
            peaks = extractPcm16WaveformPeaks(gainedPcm, barCount = 48)
            durationMs = ((gainedPcm.size.toLong() / 2L) * 1000L) / sampleRate
        } else if (isWavBytes(audioBytes) && audioBytes.size > 44) {
            val pcmBody = audioBytes.copyOfRange(44, audioBytes.size)
            val gainedPcm = applyPcm16Gain(pcmBody, volumeGain)
            finalBytes = audioBytes.copyOfRange(0, 44) + gainedPcm
            finalExt = "wav"
            finalMime = "audio/wav"
            peaks = extractPcm16WaveformPeaks(gainedPcm, barCount = 48)
            durationMs = 0L
        } else {
            finalBytes = audioBytes
            finalExt = when {
                lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> "mp3"
                lowerMime.contains("ogg") -> "ogg"
                lowerMime.contains("mp4") || lowerMime.contains("aac") -> "m4a"
                else -> "wav"
            }
            finalMime = mimeType
            peaks = extractGenericWaveformPeaks(audioBytes, barCount = 48)
            durationMs = 0L
        }

        val outFile = File(audioDir, "vox_${UUID.randomUUID()}.$finalExt")
        FileOutputStream(outFile).use { it.write(finalBytes) }

        if (durationMs <= 0L) {
            durationMs = probeDurationMs(outFile)
        }

        SavedAudioClip(
            file = outFile,
            mimeType = finalMime,
            durationMs = durationMs.coerceAtLeast(300L),
            waveformPeaks = peaks
        )
    }

    fun playTrack(
        trackId: String,
        title: String,
        voiceName: String,
        isClonedVoice: Boolean,
        audioFilePath: String,
        waveformPeaks: List<Float>,
        latencyMs: Long? = null
    ) {
        val file = File(audioFilePath)
        if (!file.exists()) {
            Log.e(TAG, "Audio file missing at $audioFilePath")
            return
        }

        val current = _playbackState.value
        if (current.trackId == trackId && mediaPlayer != null) {
            togglePlayPause()
            return
        }

        releasePlayer()

        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                isLooping = _playbackState.value.isLooping
                setOnCompletionListener {
                    if (!isLooping) {
                        _playbackState.update { state ->
                            state.copy(
                                isPlaying = false,
                                positionMs = state.durationMs
                            )
                        }
                    }
                }
                start()
            }
            mediaPlayer = player

            _playbackState.value = AudioPlaybackState(
                trackId = trackId,
                title = title,
                voiceName = voiceName,
                isClonedVoice = isClonedVoice,
                audioFilePath = audioFilePath,
                waveformPeaks = waveformPeaks.ifEmpty { List(48) { 0.35f } },
                isPlaying = true,
                isLooping = player.isLooping,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(1L),
                latencyMs = latencyMs
            )

            startProgressPolling()
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer playback failed", e)
            releasePlayer()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                _playbackState.update { it.copy(isPlaying = false) }
            } else {
                if (player.currentPosition >= player.duration - 60) {
                    player.seekTo(0)
                }
                player.start()
                _playbackState.update { it.copy(isPlaying = true) }
                startProgressPolling()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Toggle play/pause failed", e)
        }
    }

    fun seekToFraction(fraction: Float) {
        val player = mediaPlayer ?: return
        val clamped = fraction.coerceIn(0f, 1f)
        val targetMs = (player.duration * clamped).toInt()
        try {
            player.seekTo(targetMs)
            _playbackState.update { it.copy(positionMs = targetMs.toLong()) }
        } catch (e: Exception) {
            Log.e(TAG, "Seek failed", e)
        }
    }

    fun toggleLoop() {
        val nextLoop = !_playbackState.value.isLooping
        mediaPlayer?.isLooping = nextLoop
        _playbackState.update { it.copy(isLooping = nextLoop) }
    }

    fun stopPlayback() {
        releasePlayer()
        _playbackState.value = AudioPlaybackState()
    }

    fun buildShareIntent(audioFilePath: String, title: String): Intent? {
        val file = File(audioFilePath)
        if (!file.exists()) return null
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = when (file.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                else -> "audio/wav"
            }
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "VoxCraft AI — $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create share URI", e)
            null
        }
    }

    suspend fun saveToDeviceStorage(
        audioFilePath: String,
        voiceName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val source = File(audioFilePath)
        if (!source.exists()) {
            return@withContext Result.failure(IllegalStateException("Audio file not found on device."))
        }

        val safeVoice = voiceName
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "Voice" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val ext = source.extension.lowercase().ifBlank { "wav" }
        val fileName = "VoxCraft_${safeVoice}_$stamp.$ext"
        val mime = when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> "audio/wav"
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VoxCraft AI")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create file in Downloads."))

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(source).use { input ->
                        input.copyTo(out)
                    }
                } ?: return@withContext Result.failure(IllegalStateException("Failed to write audio bytes."))

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Downloads/VoxCraft AI/$fileName")
            } else {
                val targetDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "VoxCraft AI"
                ).apply { mkdirs() }
                val dest = File(targetDir, fileName)
                source.copyTo(dest, overwrite = true)
                Result.success("VoxCraft AI/$fileName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio to internal storage", e)
            Result.failure(e)
        }
    }

    fun deleteAudioFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                if (_playbackState.value.audioFilePath == path) {
                    stopPlayback()
                }
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete audio file $path", e)
        }
    }

    fun release() {
        recordJob?.cancel()
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        releasePlayer()
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val player = mediaPlayer ?: break
                try {
                    if (player.isPlaying) {
                        _playbackState.update {
                            it.copy(
                                isPlaying = true,
                                positionMs = player.currentPosition.toLong(),
                                durationMs = player.duration.toLong().coerceAtLeast(1L)
                            )
                        }
                    }
                } catch (_: Exception) {
                    break
                }
                delay(pollingIntervalMs)
            }
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun probeDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 2500L
        } catch (_: Exception) {
            2500L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun parseSampleRateFromMime(mimeType: String): Int? {
        val match = Regex("rate=(\\d+)").find(mimeType)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun applyPcm16Gain(pcm: ByteArray, gain: Float): ByteArray {
        if (abs(gain - 1.0f) < 0.02f) return pcm
        val out = ByteArray(pcm.size)
        val count = pcm.size / 2
        for (i in 0 until count) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val scaled = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i * 2] = (scaled and 0xFF).toByte()
            out[i * 2 + 1] = ((scaled shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun encodePcmToWav(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(chunkSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
        }.array()

        return header + pcmData
    }

    private fun extractPcm16WaveformPeaks(pcm: ByteArray, barCount: Int): List<Float> {
        val totalSamples = pcm.size / 2
        if (totalSamples < barCount) return List(barCount) { 0.2f }
        val step = totalSamples / barCount
        val rawPeaks = FloatArray(barCount)
        var maxPeak = 0.001f

        for (bar in 0 until barCount) {
            val start = bar * step
            val end = min(start + step, totalSamples)
            var sumSq = 0.0
            val stride = maxOf(1, (end - start) / 64)
            var sampled = 0
            var idx = start
            while (idx < end) {
                val lo = pcm[idx * 2].toInt() and 0xFF
                val hi = pcm[idx * 2 + 1].toInt()
                val s = ((hi shl 8) or lo).toShort().toDouble() / 32768.0
                sumSq += s * s
                sampled++
                idx += stride
            }
            val rms = if (sampled > 0) sqrt(sumSq / sampled).toFloat() else 0f
            rawPeaks[bar] = rms
            if (rms > maxPeak) maxPeak = rms
        }

        return rawPeaks.map { (it / maxPeak).coerceIn(0.10f, 1.0f) }
    }

    private fun extractGenericWaveformPeaks(bytes: ByteArray, barCount: Int): List<Float> {
        if (bytes.size < barCount) return List(barCount) { 0.25f }
        val step = bytes.size / barCount
        val values = FloatArray(barCount)
        var maxVal = 1f
        for (i in 0 until barCount) {
            val start = i * step
            val end = min(start + step, bytes.size)
            var acc = 0f
            val stride = maxOf(1, (end - start) / 48)
            var count = 0
            var idx = start
            while (idx < end) {
                acc += abs(bytes[idx].toInt())
                count++
                idx += stride
            }
            val avg = if (count > 0) acc / count else 0f
            values[i] = avg
            if (avg > maxVal) maxVal = avg
        }
        return values.map { (it / maxVal).coerceIn(0.14f, 0.98f) }
    }

    private fun isWavBytes(bytes: ByteArray): Boolean =
        bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'A'.code.toByte()

    private fun isMp3Bytes(bytes: ByteArray): Boolean =
        bytes.size >= 3 && (
            (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) ||
                ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0)
            )

    private fun isOggBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
            bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()

    private fun isMp4Bytes(bytes: ByteArray): Boolean =
        bytes.size >= 8 &&
            bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() &&
            bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()
}
