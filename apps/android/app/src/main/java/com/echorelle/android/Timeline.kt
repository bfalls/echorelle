package com.echorelle.android

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.exp
import kotlin.math.abs

const val SAMPLE_RATE = 48_000

/** All periods and cursors are integer PCM frames, never wall-clock deadlines. */
class Voice(val pcm: FloatArray, val periodFrames: Long, val gain: Float = 0.7f) {
    init {
        require(pcm.isNotEmpty())
        require(periodFrames >= pcm.size)
        require(gain in 0f..1f)
    }
    var phase = 0L
        private set
    @Volatile var onsets = 0L
        private set

    fun sample(): Float {
        if (phase == 0L) onsets++
        val result = if (phase < pcm.size) pcm[phase.toInt()] * gain else 0f
        phase++
        if (phase == periodFrames) phase = 0
        return result
    }
}

class Timeline(val voices: List<Voice>) {
    init { require(voices.size in 1..4) }
    @Volatile var frames = 0L
        private set
    fun render(target: FloatArray) {
        for (i in target.indices) {
            var mixed = 0f
            for (voice in voices) mixed += voice.sample()
            // Headroom for this run's actual sources. A single sound no longer loses 12 dB
            // to room reserved for three nonexistent sources. The source count stays fixed.
            target[i] = (mixed / voices.size).coerceIn(-1f, 1f)
        }
        frames += target.size
    }
}

data class Tone(val name: String, val description: String, val pcm: FloatArray) {
    val seconds get() = pcm.size.toDouble() / SAMPLE_RATE
}

/** Original mathematical synthesis. Entire decay is included in each finite PCM buffer. */
object Tones {
    val all: List<Tone> by lazy {
        listOf(
            synth("Soft Ding", "Quiet rounded ding, short decay", .42, 660.0, .60, 12.0, 0),
            synth("Warm Bell", "Mellow low bell with a soft strike", .65, 392.0, .58, 9.0, 1),
            synth("Bright Glass Ping", "Clear higher glass-like ping", .38, 1320.0, .50, 14.0, 1),
            synth("Gentle String Pluck", "Soft brief plucked string", .48, 330.0, .58, 13.0, 2),
            synth("Loud Alarm Buzz", "Harsh, urgent buzzer; use normal device volume", .72, 180.0, .92, 1.0, 3)
        )
    }
    private fun synth(name: String, description: String, seconds: Double, hz: Double,
                      level: Double, decay: Double, kind: Int): Tone {
        val pcm = FloatArray((seconds * SAMPLE_RATE).toInt()) { frame ->
            val t = frame.toDouble() / SAMPLE_RATE
            val x = 2 * PI * hz * t
            val wave = when (kind) {
                1 -> (sin(x) + .28 * sin(2.71 * x)) / 1.28
                2 -> (sin(x) + .3 * sin(2*x) + .15 * sin(3*x)) / 1.45
                3 -> (sin(x) + .5 * sin(3*x) + .3 * sin(5*x)) / 1.8
                else -> sin(x)
            }
            val envelope = minOf(1.0, t / .006, (seconds - t) / .012) * exp(-decay*t)
            (wave * envelope * level).toFloat()
        }
        check(pcm.all { abs(it) < 1 })
        return Tone(name, description, pcm)
    }
}
