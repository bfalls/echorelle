package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class GlassSequenceTest {
    private val square=GlassPane(floatArrayOf(0f,0f,1f,0f,1f,1f,0f,1f))
    @Test fun cadenceAndCompletionSpanTheObservedInterval() {
        for(seconds in listOf(5L,10L,30L,60L,180L,600L,1265L,7200L,86400L)) {
            val sequence=GlassSequence(seconds*SAMPLE_RATE,listOf(square))
            assertEquals(0,sequence.completed(0f))
            assertEquals(sequence.count,sequence.completed(sequence.timing.solveEnd.toFloat()))
            assertEquals(sequence.count,sequence.completed(1f))
            assertTrue(sequence.secondsPerPiece in 2.0..3.0)
            assertEquals(1.0,sequence.pieces.sumOf{group->group.sumOf{it.area}},.00001)
            assertTrue(sequence.pieces.all{it.isNotEmpty()})
            val half=((sequence.count/2+.5)/sequence.count*sequence.timing.solveEnd).toFloat()
            assertEquals(sequence.count/2,sequence.completed(half))
            assertEquals(.5f,sequence.local(half),.002f)
        }
    }
    @Test fun concavePanesSplitWithoutLosingGlass() {
        val pane=GlassPane(floatArrayOf(0f,0f,1f,0f,1f,.3f,.3f,.3f,.3f,1f,0f,1f))
        val (a,b)=pane.split()!!
        assertEquals(pane.area,a.area+b.area,.000001)
        assertTrue(a.area>0 && b.area>0)
        val bridge=GlassPane(floatArrayOf(.84244776f,.4384765f,.84244776f,.414062f,.8281249f,.414062f,
            .8281249f,.40625f,.84244776f,.40625f,.84244776f,.4384765f))
        val split=bridge.split()!!
        assertEquals(bridge.area,split.first.area+split.second.area,1e-9)
    }
    @Test fun shortIntervalsGroupExistingPanesWithoutDuplication() {
        val originals=List(20){i->GlassPane(floatArrayOf(i/20f,0f,(i+1)/20f,0f,(i+1)/20f,1f,i/20f,1f))}
        val sequence=GlassSequence(10L*SAMPLE_RATE,originals)
        assertEquals(2,sequence.count)
        assertEquals(originals.toSet(),sequence.pieces.flatten().toSet())
    }
    @Test fun longBuildsAreBoundedAndCancellable() {
        var checks=0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            GlassSequence(MAX_PERIOD,listOf(square)) {if(++checks==2)throw java.util.concurrent.CancellationException()}
        }
        assertEquals(2,checks)
        assertTrue(GlassSequence(MAX_PERIOD,listOf(square)).count<=34560)
    }
    @Test fun artComplexityChangesWithDuration() {
        assertEquals(GlassArt.BLOSSOM,GlassArt.forPeriod(60L*SAMPLE_RATE))
        assertEquals(GlassArt.IRIS,GlassArt.forPeriod(600L*SAMPLE_RATE))
        assertEquals(GlassArt.GARDEN,GlassArt.forPeriod(7200L*SAMPLE_RATE))
        assertEquals(Visual.GLASS,Visual.fromSaved("GLASS"))
    }
    @Test fun actualBundledOutlinesSupportTheFullDurationRange() {
        for(art in GlassArt.entries) {
            val text=java.io.File("src/main/assets/glass/${art.asset}.json").readText()
            val originals=Regex("\\[\\[.*?\\]\\]").findAll(text).map { polygon ->
                GlassPane(Regex("-?\\d+(?:\\.\\d+)?").findAll(polygon.value).map{it.value.toFloat()}.toList().toFloatArray())
            }.toList()
            assertTrue(originals.size>10)
            assertEquals("Entire artwork is assigned to panes",1.0,originals.sumOf{it.area},.008)
            val sequence=GlassSequence(if(art==GlassArt.GARDEN)MAX_PERIOD else 7200L*SAMPLE_RATE,originals)
            assertEquals(originals.sumOf{it.area},sequence.pieces.sumOf{group->group.sumOf{it.area}},.00001)
        }
    }
}
