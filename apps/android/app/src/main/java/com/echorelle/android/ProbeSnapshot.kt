package com.echorelle.android

enum class ProbeMode { STOPPED, PLAYING, PAUSED, PREVIEW, ERROR }

data class ProbeSnapshot(
    val mode: ProbeMode = ProbeMode.STOPPED,
    val sound: String = "",
    val periodFrames: Long = 0,
    val playedFrames: Long = 0,
    val sources: Int = 1,
    val reason: String = "Stopped · choose an interval or preview a sound",
) {
    val active get() = mode == ProbeMode.PLAYING || mode == ProbeMode.PAUSED || mode == ProbeMode.PREVIEW
    val secondsUntilNext get() = if (periodFrames > 0) (periodFrames - playedFrames % periodFrames).toDouble() / SAMPLE_RATE else 0.0
    val label get() = when (mode) {
        ProbeMode.PLAYING -> "Playing · $sound · every ${periodFrames.toDouble() / SAMPLE_RATE}s · $sources source(s)"
        ProbeMode.PREVIEW -> "Previewing · $sound · plays once"
        else -> reason
    }
}
