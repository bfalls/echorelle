package com.echorelle.android

import kotlin.math.ceil
import kotlin.math.floor

/** Read-only visual projection. It cannot issue an audio command or advance a voice. */
internal data class VisualProgress(val whole: Double, val detail: Float, val passes: Int, val cycle: Long = 0) {
    companion object {
        fun atFraction(period: Long, fraction: Double): VisualProgress {
            val whole = fraction.coerceIn(0.0, 1.0)
            // One gesture for short intervals; working stitches/folds of at most eight seconds
            // for longer ones. The completed form always represents the entire interval.
            val passes = ceil(period.coerceAtLeast(1).toDouble() / SAMPLE_RATE / 8.0).toInt().coerceAtLeast(1)
            val local = if (whole == 1.0) 1.0 else whole * passes - floor(whole * passes)
            return VisualProgress(whole, local.toFloat(), passes)
        }
        fun observed(snapshot: JobPlayback, period: Long, nowNanos: Long): VisualProgress {
            val elapsed = if (snapshot.mode == JobMode.PLAYING && snapshot.observedAtNanos > 0)
                (nowNanos - snapshot.observedAtNanos).coerceIn(0, 150_000_000L) else 0L
            // Bound extrapolation if the audio owner stalls. Never invent ongoing playback.
            val frames = snapshot.phase.toDouble() + elapsed.toDouble() * SAMPLE_RATE / 1e9
            val safePeriod=period.coerceAtLeast(1)
            return atFraction(period, (frames % safePeriod) / safePeriod).copy(
                cycle=snapshot.frames/safePeriod+(frames/safePeriod).toLong())
        }
    }
}
