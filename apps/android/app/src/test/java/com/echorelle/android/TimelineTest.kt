package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class TimelineTest {
    @Test fun immediateAndStartToStart() {
        val voice = Voice(floatArrayOf(1f, .5f), 5, 1f)
        val output = FloatArray(12)
        Timeline(listOf(voice)).render(output)
        assertArrayEquals(floatArrayOf(1f,.5f,0f,0f,0f,1f,.5f,0f,0f,0f,1f,.5f), output, 0f)
        assertEquals(3L, voice.onsets)
    }
    @Test fun exactLoopHasNoInsertedOrDroppedFramesAcross1000Joins() {
        val pcm = floatArrayOf(.2f, .4f, -.2f, -.4f)
        val output = FloatArray(pcm.size * 1001)
        Timeline(listOf(Voice(pcm, pcm.size.toLong(), 1f))).render(output)
        output.forEachIndexed { index, sample -> assertEquals(pcm[index % pcm.size], sample, 0f) }
    }
    @Test fun chunkBoundariesNeverChangeTimeline() {
        val whole = FloatArray(137)
        Timeline(listOf(Voice(floatArrayOf(1f, -1f), 7))).render(whole)
        val engine = Timeline(listOf(Voice(floatArrayOf(1f, -1f), 7)))
        val split = listOf(3, 48, 1, 85).flatMap { size -> FloatArray(size).also(engine::render).toList() }.toFloatArray()
        assertArrayEquals(whole, split, 0f)
    }
    @Test fun concurrentSourcesKeepIndependentPeriods() {
        val a = Voice(floatArrayOf(1f), 3, 1f)
        val b = Voice(floatArrayOf(1f), 5, 1f)
        val out = FloatArray(16)
        Timeline(listOf(a,b)).render(out)
        assertEquals(6L, a.onsets)
        assertEquals(4L, b.onsets)
        assertEquals(1f, out[15], 0f)
    }
    @Test fun exactEligibilityUsesFrames() {
        Voice(FloatArray(48000), 48000)
        assertThrows(IllegalArgumentException::class.java) { Voice(FloatArray(48001), 48000) }
        assertThrows(IllegalArgumentException::class.java) { Voice(FloatArray(0), 48000) }
    }
    @Test fun paletteHasCompleteSubSecondBuffersAndNoClipping() {
        assertEquals(5, Tones.all.size)
        assertEquals("Soft Ding", Tones.all.first().name)
        Tones.all.forEach { assertTrue(it.pcm.size in 1 until SAMPLE_RATE); assertTrue(it.pcm.all { v -> kotlin.math.abs(v) < 1f }) }
    }
}
