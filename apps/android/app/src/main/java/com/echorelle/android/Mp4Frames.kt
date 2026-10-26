package com.echorelle.android

import java.io.File
import java.io.RandomAccessFile

/** ISO BMFF media duration is an integer in the audio track timescale, not a rounded UI duration.
 * Android's extractor can expose AAC delay but omit final partial-packet padding. */
internal object Mp4Frames {
    private data class Box(val type: String, val data: Long, val end: Long)
    data class Bounds(val frames: Long, val delay: Long)
    fun bounds(file: File, trackId: Int, rate: Int): Bounds? = RandomAccessFile(file, "r").use { input ->
        fun children(start: Long, end: Long): List<Box> {
            val result = mutableListOf<Box>()
            var position = start
            while (position + 8 <= end) {
                require(result.size < 10000) { "Too many container boxes." }
                input.seek(position)
                var length = input.readInt().toLong() and 0xffffffffL
                val kind = ByteArray(4); input.readFully(kind)
                var header = 8
                if (length == 1L) { length = input.readLong(); header = 16 }
                if (length == 0L) length = end - position
                require(length >= header && length <= end - position) { "Truncated audio container." }
                result += Box(String(kind, Charsets.US_ASCII), position + header, position + length)
                position += length
            }
            return result
        }
        val top = children(0, input.length())
        val movie = top.firstOrNull { it.type == "moov" } ?: return@use null
        val movieBoxes = children(movie.data, movie.end)
        val movieHeader = movieBoxes.firstOrNull { it.type == "mvhd" } ?: error("Missing movie timescale.")
        input.seek(movieHeader.data); val movieVersion = input.readUnsignedByte()
        input.seek(movieHeader.data + if(movieVersion == 1) 20 else 12)
        val movieScale = input.readInt().toLong() and 0xffffffffL
        require(movieScale > 0) { "Invalid movie timescale." }
        for (track in movieBoxes.filter { it.type == "trak" }) {
            val boxes = children(track.data, track.end)
            val header = boxes.firstOrNull { it.type == "tkhd" } ?: continue
            input.seek(header.data); val version = input.readUnsignedByte()
            input.seek(header.data + if (version == 1) 20 else 12)
            val id = input.readInt()
            if (id != trackId) continue
            val media = boxes.firstOrNull { it.type == "mdia" } ?: continue
            val mediaBoxes = children(media.data, media.end)
            val handler = mediaBoxes.firstOrNull { it.type == "hdlr" } ?: continue
            input.seek(handler.data + 8)
            if (input.readInt() != 0x736f756e) continue // soun
            var editStart = 0L
            var editTicks: Long? = null
            boxes.firstOrNull { it.type == "edts" }?.let { edit ->
                children(edit.data, edit.end).firstOrNull { it.type == "elst" }?.let { list ->
                    input.seek(list.data); val editVersion = input.readUnsignedByte()
                    input.seek(list.data + 4)
                    val count = input.readInt()
                    require(count in 0..1) { "Complex edited audio is unsupported. Export it as WAV or FLAC first." }
                    if(count == 1) {
                        editTicks = if(editVersion == 1) input.readLong() else input.readInt().toLong() and 0xffffffffL
                        editStart = if(editVersion == 1) input.readLong() else input.readInt().toLong()
                        require(editStart >= 0 && input.readInt() == 0x10000) { "Unsupported audio edit rate or empty edit. Export WAV or FLAC first." }
                    }
                }
            }
            val duration = mediaBoxes.firstOrNull { it.type == "mdhd" } ?: continue
            input.seek(duration.data); val mdhdVersion = input.readUnsignedByte()
            input.seek(duration.data + if (mdhdVersion == 1) 20 else 12)
            val scale = input.readInt().toLong() and 0xffffffffL
            val ticks = if (mdhdVersion == 1) input.readLong() else input.readInt().toLong() and 0xffffffffL
            require(scale > 0 && ticks in 1..(scale * 65L)) { "Unsupported audio track duration (limit 60 seconds)." }
            val numerator = ticks * rate
            require(numerator % scale == 0L) { "Audio track duration does not end on a sample. Export WAV or FLAC first." }
            require(editStart <= ticks && editStart * rate % scale == 0L) { "Audio edit does not start on a sample." }
            editTicks?.let { length ->
                // Movie-time edits are often rounded to milliseconds. Never use that rounded value
                // to trim samples; reject a genuine extra out-point instead of ignoring it.
                val difference = kotlin.math.abs(length.toDouble()/movieScale - (ticks-editStart).toDouble()/scale)
                require(difference <= 1.0/movieScale + 1e-9) { "Trimmed audio edit is unsupported. Export WAV or FLAC first." }
            }
            return@use Bounds(numerator / scale, editStart * rate / scale)
        }
        null
    }
}
