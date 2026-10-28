package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class JobBehaviorTest {
    private val sound = Sound("test", "Fixture", "", 48_001)
    private fun job(period: Long = 5) = Job("a", "A", "test", period, false, 1f)
    @Test fun eligibilityRevalidatesExactFramesAndMatch() {
        assertNotNull(Eligibility.reason(sound, 48_000, false))
        assertNull(Eligibility.reason(sound, 48_001, false))
        assertNull(Eligibility.reason(sound, 48_001, true))
        assertNotNull(Eligibility.reason(sound, 48_002, true))
        assertNotNull(Eligibility.reason(sound.copy(error="Decoder failed"),48_001,true))
        assertNotNull(Eligibility.reason(sound.copy(frames=20160),20160,false))
        assertNull(Eligibility.reason(sound.copy(frames=20160),20160,true))
    }
    @Test fun timingHasNoDefaultAndRoundTripsFramePrecision() {
        for(text in listOf("", "NaN", "Infinity", "-1", "999999999999999999999")) assertNull(Eligibility.parse(text))
        for(frames in listOf(1L,20160L,48001L,MAX_PERIOD)) assertEquals(frames,Eligibility.parse(Eligibility.seconds(frames)))
    }
    @Test fun independentPauseAndResumeFreezeClipAndCycle() {
        val a=JobVoice(job(),floatArrayOf(.2f,.4f,.6f));a.ramp=240
        val b=JobVoice(job(7),floatArrayOf(.7f));b.ramp=240
        assertEquals(.2f,a.sample(),0f);b.sample()
        a.mode=JobMode.PAUSED
        repeat(8){assertEquals(0f,a.sample(),0f);b.sample()}
        assertEquals(1,a.elapsed);assertEquals(9,b.elapsed)
        a.mode=JobMode.PLAYING
        assertEquals(.4f,a.sample(),0f)
        assertEquals(.6f,a.sample(),0f)
        assertEquals(0f,a.sample(),0f);assertEquals(0f,a.sample(),0f)
        assertEquals(.2f,a.sample(),0f)
    }
    @Test fun realJobExactLoopRetains1000Joins() {
        val pcm=floatArrayOf(.3f,.1f,-.1f,-.3f)
        val voice=JobVoice(job(4),pcm);voice.ramp=240
        repeat(4004){assertEquals(pcm[it%4],voice.sample(),0f)}
    }
    @Test fun stopFadesThenResetsAndSeamOptionPreservesCount() {
        val voice=JobVoice(job(500),FloatArray(500){.5f});voice.ramp=240
        repeat(100){voice.sample()};voice.stopping=true;voice.ramp=0
        val first=voice.sample();repeat(239){voice.sample()}
        assertTrue(first>0);assertEquals(JobMode.STOPPED,voice.mode);assertEquals(0,voice.elapsed)
        val softened=JobVoice(job(500).copy(soften=true),FloatArray(500){.5f});softened.ramp=240
        assertEquals(0f,softened.sample(),0f);repeat(499){softened.sample()}
        assertEquals(500,softened.elapsed);assertEquals(0f,softened.sample(),0f)
    }
}
