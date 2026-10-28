package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class GapClockTest {
    @Test fun deadlinesDoNotDriftWhenPreparationWakesEarlyOrLate() {
        val clock=GapClock(1_000_000_000)
        val gap=SilentGap(20160,10L*SAMPLE_RATE,clock)
        assertEquals(11_000_000_000L,gap.deadline)
        assertEquals(10_000_000_000L,gap.prepareAt)
        assertEquals(5L*SAMPLE_RATE-20160,gap.advanceAt(6_000_000_000))
        assertEquals(10L*SAMPLE_RATE-20160,gap.advanceAt(50_000_000_000))
        assertEquals(0,gap.advanceAt(0))
        assertEquals(21_000_000_000L,SilentGap(500000,20L*SAMPLE_RATE,clock).deadline)
    }
    @Test fun longSessionsAndFractionalFramesAvoidOverflow() {
        val clock=GapClock(123456789)
        for(frame in listOf(0L,1L,20160L,MAX_PERIOD,100L*MAX_PERIOD)) {
            val now=clock.nanosAt(frame)
            assertTrue(now>=clock.originNanos)
            assertTrue(frame-clock.frameAt(now) in 0..1)
        }
    }
    @Test fun gapAdvanceDoesNotAdvancePausedOrStoppedJobs() {
        val job=Job("a","","",MAX_PERIOD,false)
        val voice=JobVoice(job,floatArrayOf(.5f)).apply{elapsed=240}
        voice.advanceGap(MAX_PERIOD-240)
        assertEquals(MAX_PERIOD,voice.elapsed);assertEquals(240,voice.ramp)
        voice.mode=JobMode.PAUSED;voice.advanceGap(48000)
        assertEquals(MAX_PERIOD,voice.elapsed)
    }
}
