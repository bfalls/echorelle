package com.echorelle.android

import java.math.BigDecimal
import java.math.RoundingMode

const val MAX_PERIOD = 86_400L * SAMPLE_RATE
const val MAX_ACTIVE = 4

data class Sound(val id: String, val name: String, val description: String, val frames: Int,
    val sourceFrames: Long = frames.toLong(), val sourceRate: Int = SAMPLE_RATE, val error: String? = null) {
    val seconds get() = frames.toDouble() / SAMPLE_RATE
}
data class Job(val id: String, val name: String, val soundId: String, val period: Long,
    val match: Boolean, val volume: Float = .8f, val soften: Boolean = false)
enum class JobMode { STOPPED, PLAYING, PAUSED, INTERRUPTED, ERROR }
data class JobPlayback(val mode: JobMode = JobMode.STOPPED, val phase: Long = 0,
    val frames: Long = 0, val reason: String = "", val onsets: Long = 0,
    val observedAtNanos: Long = 0, val visualSeed: Long = 0)
enum class Visual(val title: String) {
    BIRD("Origami"), CUBE("Cube"), GLASS("Stained glass");
    companion object {
        // Removed visual preferences migrate without losing the user's jobs or imports.
        fun fromSaved(name: String): Visual = entries.firstOrNull { it.name==name } ?: BIRD
    }
}
data class Library(val jobs: List<Job> = emptyList(), val imports: List<Sound> = emptyList(),
    val visual: Visual = Visual.BIRD, val ready: Boolean = false, val message: String = "") {
    val sounds get() = Tones.all.mapIndexed { i, t -> Sound("tone-$i", t.name, t.description, t.pcm.size) } + imports
}
object Eligibility {
    fun parse(text: String): Long? = try {
        BigDecimal(text.trim()).multiply(BigDecimal(SAMPLE_RATE)).setScale(0, RoundingMode.HALF_UP)
            .longValueExact().takeIf { it in 1..MAX_PERIOD }
    } catch (_: Exception) { null }
    fun reason(sound: Sound?, period: Long?, match: Boolean): String? = when {
        sound == null -> "Choose a sound."
        sound.error != null -> sound.error
        sound.frames <= 0 -> "Sound duration is unavailable."
        period == null -> "Enter an interval in seconds or enable Match."
        period > MAX_PERIOD || period <= 0 -> "Choose an interval up to 24 hours."
        !match && period < SAMPLE_RATE -> "Manual intervals must be at least 1 second. Enable Match for this short loop."
        period < sound.frames -> "${sound.seconds}s sound exceeds your ${period.toDouble()/SAMPLE_RATE}s interval."
        match && period != sound.frames.toLong() -> "Match must use the exact sound length."
        else -> null
    }
    fun seconds(frames: Long): String = BigDecimal(frames).divide(BigDecimal(SAMPLE_RATE), 9, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** One worker owns these cursors. Pauses never advance them. */
internal class JobVoice(val job: Job, val pcm: FloatArray, val previewRepeats: Int = 0) {
    // Created with the voice on the command path, never inside the audio render loop.
    val visualSeed=kotlin.random.Random.nextLong()
    @Volatile var mode = JobMode.PLAYING
    @Volatile var elapsed = 0L
    var ramp = 0
    var stopping = false
    val period = job.period
    fun rewind(frames: Long) { elapsed = (elapsed - frames).coerceAtLeast(0) }
    fun canAdvanceSilence(frames: Int): Boolean {
        if(mode!=JobMode.PLAYING) return true
        val phase=elapsed%period
        return !stopping && previewRepeats==0 && phase>=pcm.size && period-phase>=frames
    }
    fun advanceSilence(frames: Int) {
        if(mode==JobMode.PLAYING) { elapsed+=frames;ramp=(ramp+frames).coerceAtMost(240) }
    }
    fun sample(): Float {
        if (mode != JobMode.PLAYING) return 0f
        if (previewRepeats > 0 && elapsed >= pcm.size.toLong() * previewRepeats) {
            mode = JobMode.STOPPED; return 0f
        }
        val phase = elapsed % period
        var value = if (phase < pcm.size) pcm[phase.toInt()] * job.volume else 0f
        if (job.soften && phase < pcm.size) {
            // Explicit duration-preserving 5ms edge fade. Never trim or shift frames.
            value *= minOf(1f, phase / 240f, (pcm.size - 1 - phase) / 240f).coerceAtLeast(0f)
        }
        if (!stopping && ramp < 240) { value *= ramp / 240f; ramp++ }
        if (stopping) {
            value *= (240 - ramp).coerceAtLeast(0) / 240f
            ramp++
            if (ramp >= 240) { mode = JobMode.STOPPED; elapsed = 0; return 0f }
        }
        elapsed++
        return value
    }
}
