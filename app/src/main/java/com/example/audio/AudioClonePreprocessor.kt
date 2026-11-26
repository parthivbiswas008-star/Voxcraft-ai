package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "AudioClonePreprocessor"
private const val TARGET_SAMPLE_RATE = 16_000
private const val FALLBACK_WAV_SAMPLE_RATE = 8_000
private const val TARGET_SPEECH_MS = 20_000L
private const val FRAME_MS = 500
private const val SAMPLES_PER_FRAME = (TARGET_SAMPLE_RATE * FRAME_MS) / 1000
private const val MAX_DIRECT_FALLBACK_BYTES = 12 * 1024 * 1024

data class PreparedVoiceFingerprint(
    val bytes: ByteArray,
    val mimeType: String,
    val sourceDurationMs: Long,
    val extractedSpeechMs: Long
)

object AudioClonePreprocessor {

    fun prepareVoiceFingerprint(
        audioFile: File,
        originalMimeType: String,
        sourceDurationMs: Long,
        cacheDir: File,
        onStage: (String) -> Unit = {}
    ): PreparedVoiceFingerprint {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            throw IllegalStateException("Audio sample file is missing or empty.")
        }

        onStage("Scanning audio & extracting clearest speech…")

        // Step 1: Extract representative 16kHz mono PCM across the entire audio duration
        val candidatePcm: ShortArray? = try {
            if (isWavFile(audioFile)) {
                extractFromWavFile(audioFile)
            } else {
                extractWithMediaCodec(audioFile, sourceDurationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Primary audio decode path failed, attempting MediaCodec fallback", e)
            try {
                extractWithMediaCodec(audioFile, sourceDurationMs)
            } catch (codecErr: Exception) {
                Log.w(TAG, "MediaCodec decode also failed", codecErr)
                null
            }
        }

        if (candidatePcm == null || candidatePcm.size < TARGET_SAMPLE_RATE / 2) {
            // Safe fallback: if file is reasonably small, pass original bytes directly
            if (audioFile.length() <= MAX_DIRECT_FALLBACK_BYTES) {
                return PreparedVoiceFingerprint(
                    bytes = audioFile.readBytes(),
                    mimeType = originalMimeType.ifBlank { "audio/wav" },
                    sourceDurationMs = sourceDurationMs,
                    extractedSpeechMs = sourceDurationMs
                )
            }
            throw IllegalStateException(
                "Unable to decode this large audio file. Try a standard MP3, M4A, WAV, or OGG file."
            )
        }

        // Step 2: Select up to 20s of clearest active speech across start, middle, and end
        val activeSpeechPcm = selectClearestSpeechFrames(candidatePcm)
        val normalizedPcm = normalizeSpeechLevel(activeSpeechPcm)
        val extractedSpeechMs = (normalizedPcm.size.toLong() * 1000L) / TARGET_SAMPLE_RATE

        // Step 3: Encode into ultra-compact mono AAC (.m4a) with safe 8kHz mono WAV fallback
        onStage("Compressing compact voice fingerprint…")
        val aacBytes = encodePcmToCompactM4a(normalizedPcm, TARGET_SAMPLE_RATE, cacheDir)
        if (aacBytes != null && aacBytes.size >= 1024) {
            Log.d(
                TAG,
                "Prepared compact M4A fingerprint: ${aacBytes.size / 1024} KB (${extractedSpeechMs}ms speech from ${sourceDurationMs}ms source)"
            )
            return PreparedVoiceFingerprint(
                bytes = aacBytes,
                mimeType = "audio/mp4",
                sourceDurationMs = sourceDurationMs,
                extractedSpeechMs = extractedSpeechMs
            )
        }

        val compactWav = encodeCompact8kWav(normalizedPcm)
        Log.d(
            TAG,
            "Prepared compact 8kHz WAV fingerprint: ${compactWav.size / 1024} KB (${extractedSpeechMs}ms speech from ${sourceDurationMs}ms source)"
        )
        return PreparedVoiceFingerprint(
            bytes = compactWav,
            mimeType = "audio/wav",
            sourceDurationMs = sourceDurationMs,
            extractedSpeechMs = extractedSpeechMs
        )
    }

    private fun isWavFile(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < 44) return false
                val header = ByteArray(12)
                raf.readFully(header)
                header[0] == 'R'.code.toByte() && header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() && header[3] == 'F'.code.toByte() &&
                    header[8] == 'W'.code.toByte() && header[9] == 'A'.code.toByte() &&
                    header[10] == 'V'.code.toByte() && header[11] == 'E'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun extractFromWavFile(file: File): ShortArray? {
        RandomAccessFile(file, "r").use { raf ->
            val fileLen = raf.length()
            if (fileLen < 44) return null

            raf.seek(12)
            var audioFormat = 1
            var channels = 1
            var sampleRate = TARGET_SAMPLE_RATE
            var bitsPerSample = 16
            var dataOffset = -1L
            var dataSize = 0L

            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= fileLen) {
                raf.readFully(chunkHeader)
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFFFFFFL

                if (chunkId == "fmt " && chunkSize >= 16) {
                    val fmtBuf = ByteArray(16)
                    raf.readFully(fmtBuf)
                    val bb = ByteBuffer.wrap(fmtBuf).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = bb.short.toInt() and 0xFFFF
                    channels = (bb.short.toInt() and 0xFFFF).coerceAtLeast(1)
                    sampleRate = bb.int.coerceAtLeast(8000)
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = bb.short.toInt() and 0xFFFF
                    val remaining = chunkSize - 16
                    if (remaining > 0) raf.seek(raf.filePointer + remaining)
                } else if (chunkId == "data") {
                    dataOffset = raf.filePointer
                    dataSize = min(chunkSize, fileLen - dataOffset)
                    break
                } else {
                    val skip = chunkSize + (chunkSize and 1L)
                    if (skip <= 0 || raf.filePointer + skip > fileLen) break
                    raf.seek(raf.filePointer + skip)
                }
            }

            // Only handle standard uncompressed 16-bit PCM directly; let MediaCodec handle exotic WAV encodings
            if (audioFormat != 1 || bitsPerSample != 16 || dataOffset < 0 || dataSize <= 0) {
                return null
            }

            val bytesPerFrame = channels * 2
            val totalFrames = dataSize / bytesPerFrame
            val totalDurationMs = (totalFrames * 1000L) / sampleRate

            val windows: List<Pair<Long, Long>> = if (totalDurationMs <= 35_000L) {
                listOf(0L to totalFrames)
            } else {
                val windowFrames = (sampleRate * 10L).coerceAtMost(totalFrames / 6)
                val fractions = listOf(0.04, 0.22, 0.40, 0.58, 0.76, 0.90)
                fractions.map { frac ->
                    val startFrame = ((totalFrames - windowFrames).coerceAtLeast(0L) * frac).toLong()
                    startFrame to windowFrames.coerceAtMost(totalFrames - startFrame)
                }
            }

            val accumulator = ShortList(TARGET_SAMPLE_RATE * 65)
            val ioBuf = ByteArray(64 * 1024)

            for ((startFrame, frameCount) in windows) {
                if (frameCount <= 0) continue
                raf.seek(dataOffset + startFrame * bytesPerFrame)
                var remainingBytes = frameCount * bytesPerFrame
                val windowMono = ShortList(frameCount.toInt().coerceAtMost(sampleRate * 12))

                while (remainingBytes > 0) {
                    val toRead = min(ioBuf.size.toLong(), remainingBytes).toInt()
                    val alignedRead = (toRead / bytesPerFrame) * bytesPerFrame
                    if (alignedRead <= 0) break
                    val read = raf.read(ioBuf, 0, alignedRead)
                    if (read <= 0) break
                    val validBytes = (read / bytesPerFrame) * bytesPerFrame
                    val framesRead = validBytes / bytesPerFrame
                    for (f in 0 until framesRead) {
                        var sum = 0
                        val base = f * bytesPerFrame
                        for (ch in 0 until channels) {
                            val idx = base + ch * 2
                            val lo = ioBuf[idx].toInt() and 0xFF
                            val hi = ioBuf[idx + 1].toInt()
                            sum += ((hi shl 8) or lo).toShort().toInt()
                        }
                        windowMono.add((sum / channels).toShort())
                    }
                    remainingBytes -= validBytes
                }

                val resampled = resampleMonoPcm(windowMono.toShortArray(), sampleRate, TARGET_SAMPLE_RATE)
                accumulator.addAll(resampled)
            }

            return accumulator.toShortArray()
        }
    }

    private fun extractWithMediaCodec(file: File, fallbackDurationMs: Long): ShortArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = format
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return null

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else {
                44_100
            }
            var channelCount = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else {
                1
            }
            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                fallbackDurationMs.coerceAtLeast(10_000L) * 1000L
            }

            val windowTargetsUs: List<Pair<Long, Long>> = if (durationUs <= 35_000_000L) {
                listOf(0L to 35_000_000L)
            } else {
                val windowLenUs = 9_000_000L
                val maxStart = (durationUs - windowLenUs).coerceAtLeast(0L)
                listOf(0.04, 0.22, 0.40, 0.58, 0.76, 0.90).map { frac ->
                    val startUs = (maxStart * frac).toLong()
                    startUs to windowLenUs
                }
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val finalAccumulator = ShortList(TARGET_SAMPLE_RATE * 65)

            for ((windowStartUs, windowLenUs) in windowTargetsUs) {
                extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                val windowEndUs = windowStartUs + windowLenUs
                val windowMono = ShortList(sampleRate * 10)
                val maxWindowSamples = (sampleRate * (windowLenUs / 1_000_000L + 1)).toInt()
                var inputDone = false
                var outputDone = false
                var idleIterations = 0

                while (!outputDone && windowMono.size < maxWindowSamples && idleIterations < 80) {
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000L)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val readSize = extractor.readSampleData(inBuf, 0)
                                val ptsUs = extractor.sampleTime
                                if (readSize < 0 || (ptsUs > windowEndUs && windowMono.size > sampleRate)) {
                                    codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(inIdx, 0, readSize, ptsUs.coerceAtLeast(0L), 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx >= 0 -> {
                            idleIterations = 0
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && bufferInfo.size > 0) {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val totalShorts = shortBuf.remaining()
                                val frames = totalShorts / channelCount
                                for (f in 0 until frames) {
                                    var sum = 0
                                    for (ch in 0 until channelCount) {
                                        sum += shortBuf.get().toInt()
                                    }
                                    windowMono.add((sum / channelCount).toShort())
                                }
                            }
                            val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(outIdx, false)
                            if (eos || (bufferInfo.presentationTimeUs > windowEndUs && windowMono.size > sampleRate)) {
                                outputDone = true
                            }
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val fmt = codec.outputFormat
                            if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                            }
                            if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                            }
                        }
                        else -> {
                            idleIterations++
                        }
                    }
                }

                if (windowMono.size > 0) {
                    val resampled = resampleMonoPcm(windowMono.toShortArray(), sampleRate, TARGET_SAMPLE_RATE)
                    finalAccumulator.addAll(resampled)
                }
            }

            return if (finalAccumulator.size > 0) finalAccumulator.toShortArray() else null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun selectClearestSpeechFrames(pcm: ShortArray): ShortArray {
        val totalFrames = pcm.size / SAMPLES_PER_FRAME
        val maxFrames = (TARGET_SPEECH_MS / FRAME_MS).toInt()
        if (totalFrames <= 0) return pcm

        data class ScoredFrame(val index: Int, val rms: Float, val speechScore: Float)

        val frames = ArrayList<ScoredFrame>(totalFrames)
        var maxRms = 0.0001f
        for (i in 0 until totalFrames) {
            val start = i * SAMPLES_PER_FRAME
            var sumSq = 0.0
            var peak = 0
            for (s in start until start + SAMPLES_PER_FRAME) {
                val v = pcm[s].toInt()
                val av = abs(v)
                if (av > peak) peak = av
                val norm = v / 32768.0
                sumSq += norm * norm
            }
            val rms = sqrt(sumSq / SAMPLES_PER_FRAME).toFloat()
            if (rms > maxRms) maxRms = rms
            // Penalize hard clipping slightly so clean speech wins over loud pops/noise bursts
            val clipPenalty = if (peak > 32_000) 0.78f else 1.0f
            frames.add(ScoredFrame(index = i, rms = rms, speechScore = rms * clipPenalty))
        }

        val silenceThreshold = (maxRms * 0.14f).coerceAtLeast(0.006f)
        val activeFrames = frames.filter { it.rms >= silenceThreshold }

        val chosenIndices: List<Int> = when {
            totalFrames <= maxFrames -> {
                if (activeFrames.size >= 6) {
                    activeFrames.map { it.index }
                } else {
                    frames.map { it.index }
                }
            }
            activeFrames.size <= maxFrames && activeFrames.size >= 6 -> {
                activeFrames.map { it.index }
            }
            else -> {
                val pool = if (activeFrames.size >= maxFrames) activeFrames else frames
                pool.sortedByDescending { it.speechScore }
                    .take(maxFrames)
                    .map { it.index }
                    .sorted()
            }
        }

        val out = ShortArray(chosenIndices.size * SAMPLES_PER_FRAME)
        chosenIndices.forEachIndexed { outIdx, srcFrameIdx ->
            System.arraycopy(
                pcm,
                srcFrameIdx * SAMPLES_PER_FRAME,
                out,
                outIdx * SAMPLES_PER_FRAME,
                SAMPLES_PER_FRAME
            )
        }
        return out
    }

    private fun normalizeSpeechLevel(pcm: ShortArray): ShortArray {
        var peak = 0
        for (s in pcm) {
            val av = abs(s.toInt())
            if (av > peak) peak = av
        }
        if (peak < 250 || peak >= 18_000) return pcm
        val gain = (22_000f / peak.toFloat()).coerceAtMost(3.5f)
        val out = ShortArray(pcm.size)
        for (i in pcm.indices) {
            out[i] = (pcm[i] * gain).roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return out
    }

    private fun encodePcmToCompactM4a(
        pcm: ShortArray,
        sampleRate: Int,
        cacheDir: File
    ): ByteArray? {
        val tempFile = File(cacheDir, "voice_fp_${System.nanoTime()}.m4a")
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 48_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            val bufferInfo = MediaCodec.BufferInfo()

            var sampleCursor = 0
            var inputDone = false
            var outputDone = false
            var idleCycles = 0

            while (!outputDone && idleCycles < 120) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(10_000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            if (sampleCursor >= pcm.size) {
                                val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate
                                encoder.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val maxShorts = min(inBuf.capacity() / 2, pcm.size - sampleCursor)
                                val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate
                                val orderBuf = inBuf.order(ByteOrder.LITTLE_ENDIAN)
                                for (i in 0 until maxShorts) {
                                    orderBuf.putShort(pcm[sampleCursor + i])
                                }
                                sampleCursor += maxShorts
                                encoder.queueInputBuffer(inIdx, 0, maxShorts * 2, ptsUs, 0)
                            }
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        idleCycles = 0
                        val outBuf = encoder.getOutputBuffer(outIdx)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && bufferInfo.size > 0 && outBuf != null && muxerStarted && trackIndex >= 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        val eos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        encoder.releaseOutputBuffer(outIdx, false)
                        if (eos) outputDone = true
                    }
                    else -> {
                        idleCycles++
                    }
                }
            }

            if (muxerStarted) {
                muxer.stop()
                muxerStarted = false
            }
            return if (tempFile.exists() && tempFile.length() >= 1024L) {
                tempFile.readBytes()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "AAC M4A encoding unavailable on device, falling back to compact WAV", e)
            return null
        } finally {
            try {
                encoder?.stop()
            } catch (_: Exception) {
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun encodeCompact8kWav(pcm16k: ShortArray): ByteArray {
        val downsampled = resampleMonoPcm(pcm16k, TARGET_SAMPLE_RATE, FALLBACK_WAV_SAMPLE_RATE)
        val dataSize = downsampled.size * 2
        val byteRate = FALLBACK_WAV_SAMPLE_RATE * 2
        val out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + dataSize)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(FALLBACK_WAV_SAMPLE_RATE)
            putInt(byteRate)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataSize)
            for (s in downsampled) {
                putShort(s)
            }
        }
        return out.array()
    }

    private fun resampleMonoPcm(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        if (input.isEmpty() || srcRate == dstRate) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outSize = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = ShortArray(outSize)
        val lastIdx = input.size - 1
        for (i in 0 until outSize) {
            val srcPos = i * ratio
            val idx0 = srcPos.toInt().coerceIn(0, lastIdx)
            val idx1 = (idx0 + 1).coerceAtMost(lastIdx)
            val frac = srcPos - idx0
            val sample = input[idx0] * (1.0 - frac) + input[idx1] * frac
            out[i] = sample.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    private class ShortList(initialCapacity: Int = 16_384) {
        private var data = ShortArray(initialCapacity.coerceAtLeast(1024))
        var size: Int = 0
            private set

        fun add(value: Short) {
            if (size >= data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = value
        }

        fun addAll(values: ShortArray) {
            if (values.isEmpty()) return
            val required = size + values.size
            if (required > data.size) {
                var newCap = data.size * 2
                while (newCap < required) newCap *= 2
                data = data.copyOf(newCap)
            }
            System.arraycopy(values, 0, data, size, values.size)
            size += values.size
        }

        fun toShortArray(): ShortArray = data.copyOf(size)
    }
}
