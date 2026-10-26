package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class VisualProgressTest {
    @Test fun cyclesFollowConsumedAudioFramesIncludingInterpolatedWrapAndPause() {
        val period=10L*SAMPLE_RATE
        val sample=JobPlayback(JobMode.PLAYING,phase=period-240,frames=3*period-240,observedAtNanos=1_000_000_000)
        assertEquals(2L,VisualProgress.observed(sample,period,1_000_000_000).cycle)
        assertEquals(3L,VisualProgress.observed(sample,period,1_010_000_000).cycle)
        assertEquals(2L,VisualProgress.observed(sample.copy(mode=JobMode.PAUSED),period,9_000_000_000).cycle)
        assertEquals(500L,VisualProgress.observed(sample.copy(frames=500*period,phase=0),period,1_000_000_000).cycle)
    }
    @Test fun secondsMinutesAndHoursHaveAnExactBeginningAndEnd() {
        for(seconds in listOf(5L,60L,1200L,7200L)) {
            val period=seconds*SAMPLE_RATE
            assertEquals(0.0,VisualProgress.atFraction(period,0.0).whole,0.0)
            assertEquals(1.0,VisualProgress.atFraction(period,1.0).whole,0.0)
            var previous=-1.0
            repeat(1001) { n ->
                val p=VisualProgress.atFraction(period,n/1000.0)
                assertTrue(p.whole>previous);previous=p.whole
                assertTrue(p.detail in 0f..1f)
                assertTrue(seconds.toDouble()/p.passes<=8.0)
            }
        }
        assertEquals(1,VisualProgress.atFraction(5L*SAMPLE_RATE,.5).passes)
        assertEquals(8,VisualProgress.atFraction(60L*SAMPLE_RATE,.5).passes)
        assertEquals(150,VisualProgress.atFraction(1200L*SAMPLE_RATE,.5).passes)
        assertEquals(900,VisualProgress.atFraction(7200L*SAMPLE_RATE,.5).passes)
    }
    @Test fun interpolationFillsDisplayFramesBetweenAudioObservations() {
        for(seconds in listOf(5L,60L,1200L,7200L)) {
            val period=seconds*SAMPLE_RATE
            val sample=JobPlayback(JobMode.PLAYING,phase=period/2,frames=period/2,observedAtNanos=1_000_000_000)
            val a=VisualProgress.observed(sample,period,1_000_000_000)
            val b=VisualProgress.observed(sample,period,1_016_666_667)
            val c=VisualProgress.observed(sample,period,1_033_333_334)
            assertTrue(a.whole<b.whole);assertTrue(b.whole<c.whole)
            assertEquals(0.016666667/seconds,b.whole-a.whole,1e-12)
        }
    }
    @Test fun pauseFreezesAndStaleObservationsCannotRunForever() {
        val playing=JobPlayback(JobMode.PLAYING,phase=24000,observedAtNanos=1_000_000_000)
        val paused=playing.copy(mode=JobMode.PAUSED)
        val period=5L*SAMPLE_RATE
        assertEquals(VisualProgress.observed(paused,period,1_000_000_000),VisualProgress.observed(paused,period,9_000_000_000))
        assertEquals(VisualProgress.observed(playing,period,1_150_000_000),VisualProgress.observed(playing,period,9_000_000_000))
        assertEquals(.1,VisualProgress.observed(playing,period,999_000_000).whole,1e-12)
    }
    @Test fun onsetWrapAndSubFramePrecisionDoNotLoseLongIntervalProgress() {
        val nearEnd=JobPlayback(JobMode.PLAYING,phase=239760,observedAtNanos=1_000_000_000)
        assertEquals(.001,VisualProgress.observed(nearEnd,240000,1_010_000_000).whole,1e-12)
        val period=7200L*SAMPLE_RATE
        val a=VisualProgress.atFraction(period,.5)
        val b=VisualProgress.atFraction(period,(period/2+1).toDouble()/period)
        assertTrue(b.whole>a.whole)
    }
}
