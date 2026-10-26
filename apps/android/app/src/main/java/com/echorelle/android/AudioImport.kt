package com.echorelle.android

import android.content.Context
import android.media.*
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.*
import java.nio.ByteOrder
import java.util.UUID

const val MAX_DECODED_FRAMES = 60 * SAMPLE_RATE
private const val MAX_INPUT = 25L * 1024 * 1024
private const val MAX_LIBRARY = 128L * 1024 * 1024

/** Copy once, decode off the mixer, retain intentional silence, normalize to 48kHz mono. */
object AudioImport {
    suspend fun run(context: Context, uri: Uri, repository: JobRepository): Sound {
        val id = UUID.randomUUID().toString()
        val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null } }.getOrNull()?.take(120) ?: "Imported audio"
        val copy = File(repository.audioDirectory, "$id.input")
        val raw = File(repository.audioDirectory, "$id.raw")
        val destination = File(repository.audioDirectory, "$id.pcm")
        var committed = false
        try {
            require(repository.state.value.imports.size < 100) { "Library limit: 100 imports." }
            var copied = 0L
            context.contentResolver.openInputStream(uri)!!.use { input -> copy.outputStream().use { out ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    copied += n; require(copied <= MAX_INPUT) { "Import limit: 25 MiB compressed." }
                    out.write(buffer, 0, n)
                }
            } }
            val decoded = decode(copy, raw)
            val sourceFrames = decoded.frames
            val count = ((sourceFrames * SAMPLE_RATE + decoded.rate / 2) / decoded.rate).toInt()
            require(count in 1..MAX_DECODED_FRAMES) { "Import limit: 60 seconds of decoded audio." }
            val usage = repository.audioDirectory.listFiles()?.filter { it.extension == "pcm" }?.sumOf { it.length() } ?: 0
            require(usage + count * 4L <= MAX_LIBRARY) { "Audio library limit: 128 MiB." }
            // At most 60 seconds / 192kHz mono; streaming resampling avoids a second whole source array.
            DataInputStream(raw.inputStream().buffered()).use { input -> DataOutputStream(destination.outputStream().buffered()).use { output ->
                var sourceIndex = 0L
                var a = input.readFloat()
                var b = if (sourceFrames > 1) input.readFloat() else a
                for (i in 0 until count) {
                    if (i % 4096 == 0) currentCoroutineContext().ensureActive()
                    val position = i.toDouble() * decoded.rate / SAMPLE_RATE
                    val wanted = position.toLong().coerceAtMost(sourceFrames - 1)
                    while (sourceIndex < wanted) {
                        a = b; sourceIndex++
                        b = if (sourceIndex + 1 < sourceFrames) input.readFloat() else a
                    }
                    val fraction = (position - wanted).coerceIn(0.0, 1.0).toFloat()
                    output.writeFloat((a + (b - a) * fraction).coerceIn(-1f, 1f))
                }
            } }
            val sound = Sound(id, name, "Saved on this device", count, sourceFrames, decoded.rate)
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repository.update { it.copy(imports = it.imports + sound) }
                committed = true
            }
            return sound
        } finally {
            copy.delete(); raw.delete()
            if (!committed) destination.delete()
        }
    }
    private data class Decoded(val frames: Long, val rate: Int)
    private suspend fun decode(file: File, raw: File): Decoded {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No supported audio track.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val metadataDelay = if (format.containsKey(MediaFormat.KEY_ENCODER_DELAY)) format.getInteger(MediaFormat.KEY_ENCODER_DELAY).coerceAtLeast(0) else 0
            val padding = if (format.containsKey(MediaFormat.KEY_ENCODER_PADDING)) format.getInteger(MediaFormat.KEY_ENCODER_PADDING).coerceAtLeast(0) else 0
            val trackId = if (format.containsKey(MediaFormat.KEY_TRACK_ID)) format.getInteger(MediaFormat.KEY_TRACK_ID) else -1
            val bounds = if (format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm") Mp4Frames.bounds(file, trackId, format.getInteger(MediaFormat.KEY_SAMPLE_RATE)) else null
            val delay = maxOf(metadataDelay.toLong(), bounds?.delay ?: 0L)
            require(delay <= 192000L * 5 && padding <= 192000L * 5) { "Invalid codec delay or padding." }
            // Apply gapless metadata ourselves once, to exact output frame counts.
            format.setInteger(MediaFormat.KEY_ENCODER_DELAY, 0)
            format.setInteger(MediaFormat.KEY_ENCODER_PADDING, 0)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false; var outputDone = false
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var frames = 0L; var lastProgress = android.os.SystemClock.elapsedRealtime()
            DataOutputStream(raw.outputStream().buffered()).use { output ->
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()
                    require(android.os.SystemClock.elapsedRealtime() - lastProgress < 15_000) { "Decoder stalled or file is truncated." }
                    if (!inputDone) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = requireNotNull(decoder.getInputBuffer(index))
                            val n = extractor.readSampleData(buffer, 0)
                            if (n < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { decoder.queueInputBuffer(index, 0, n, extractor.sampleTime, 0); extractor.advance() }
                        }
                    }
                    val index = decoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = decoder.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                        require(rate in 8000..192000 && channels in 1..8) { "Unsupported sample rate or channel layout." }
                        require(encoding == AudioFormat.ENCODING_PCM_FLOAT || encoding == AudioFormat.ENCODING_PCM_16BIT) { "Unsupported decoded PCM format." }
                    } else if (index >= 0) {
                        lastProgress = android.os.SystemClock.elapsedRealtime()
                        val buffer = requireNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.nativeOrder())
                        buffer.position(info.offset); buffer.limit(info.offset + info.size)
                        val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                        require(info.size % (bytes * channels) == 0) { "Incomplete decoded frame." }
                        while (buffer.remaining() >= bytes * channels) {
                            var sum = 0f
                            repeat(channels) { sum += if (bytes == 4) buffer.float else buffer.short / 32768f }
                            require(sum.isFinite()) { "Invalid decoded samples." }
                            if (frames >= delay) output.writeFloat((sum / channels).coerceIn(-1f, 1f))
                            frames++
                            require(frames <= rate * 60L + delay + padding) { "Import limit: 60 seconds of decoded audio." }
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            val retained = minOf(frames - delay - padding, bounds?.frames?.minus(delay) ?: Long.MAX_VALUE)
            require(retained > 0) { "Audio is empty or has invalid delay/padding metadata." }
            RandomAccessFile(raw, "rw").use { it.setLength(retained * 4) }
            return Decoded(retained, rate)
        } finally {
            runCatching { codec?.stop() }; codec?.release(); extractor.release()
        }
    }
}
