package com.echorelle.android

/** Monotonic audio-owner schedule; conversion avoids overflow during multi-day sessions. */
internal class GapClock(var originNanos: Long) {
    fun nanosAt(frame: Long)=originNanos+frame/SAMPLE_RATE*1_000_000_000L+frame%SAMPLE_RATE*1_000_000_000L/SAMPLE_RATE
    fun frameAt(now: Long): Long {
        val elapsed=(now-originNanos).coerceAtLeast(0)
        return elapsed/1_000_000_000L*SAMPLE_RATE+elapsed%1_000_000_000L*SAMPLE_RATE/1_000_000_000L
    }
}

internal class SilentGap(val start: Long,val end: Long,val clock: GapClock,val voiceBases: LongArray=LongArray(0)) {
    val deadline=clock.nanosAt(end)
    val prepareAt=deadline-1_000_000_000L
    fun advanceAt(now: Long)=(clock.frameAt(now)-start).coerceIn(0,end-start)
}
