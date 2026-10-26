package com.echorelle.android

import org.junit.Assert.*
import org.junit.Test

class CubeSequenceTest {
    @Test fun allSliceTurnsAndTheirInversesAreExact() {
        for(size in 3..5) {
            val solved=CubeSequence.solvedState(size)
            val step=if(size%2==0)2 else 1
            val radius=(size-1)*step/2
            for(axis in 0..2) for(layer in -radius..radius step step) {
                val turn=CubeSequence.Turn(axis,layer,1)
                val state=solved.copyOf()
                repeat(4){CubeSequence.apply(state,turn)}
                assertArrayEquals(solved,state)
                CubeSequence.apply(state,turn);CubeSequence.apply(state,turn.inverse())
                assertArrayEquals(solved,state)
            }
        }
        val state=CubeSequence.solvedState()
        repeat(6){for(turn in CubeSequence.parse("R U R' U'"))CubeSequence.apply(state,turn)}
        assertArrayEquals(CubeSequence.solvedState(),state)
    }
    @Test fun randomScramblesSolveByExactReversePlaybackWithLegalStickers() {
        for(seconds in listOf(5L,60L,1265L,7200L)) {
            val route=CubeSequence(seconds*SAMPLE_RATE,42)
            val state=route.stateAt(0).copyOf()
            assertFalse(CubeSequence.isSolved(state))
            route.turns.forEachIndexed { index,turn ->
                CubeSequence.apply(state,turn)
                assertArrayEquals(state,route.stateAt(index+1))
            }
            assertTrue(CubeSequence.isSolved(state))
            // Reverse the solution to reconstruct its exact scrambled starting pose.
            for(turn in route.turns.asReversed())CubeSequence.apply(state,turn.inverse())
            assertArrayEquals(state,route.stateAt(0))
            val seen=hashSetOf<List<Int>>()
            for(i in state.indices step 6) {
                assertTrue(seen.add(state.slice(i until i+6)))
                assertEquals(1,(3..5).sumOf{state[i+it]*state[i+it]})
                val axis=(0..2).first{state[i+it+3]!=0}
                assertEquals(state[i+axis],state[i+axis+3]*route.radius)
                assertTrue((0..2).all{state[i+it] in -route.radius..route.radius})
            }
        }
    }
    @Test fun cadenceIsOneTurnPerSecondWithOnlyTheLastSlotPossiblyShorter() {
        for(seconds in listOf(5.0,5.2,60.0,1265.0,7200.0,86400.0)) {
            val period=(seconds*SAMPLE_RATE).toLong()
            val route=CubeSequence(period)
            val solveSeconds=seconds-CubeTiming(period).revealSeconds
            assertEquals(kotlin.math.ceil(solveSeconds).toInt(),route.turns.size)
            for(i in route.turns.indices) {
                assertEquals(i.toDouble(),route.boundaries[i]*solveSeconds,.01)
                assertTrue(route.boundaries[i+1]>route.boundaries[i])
            }
            assertEquals(route.turns.size,route.moveIndex(1f))
        }
    }
    @Test fun intervalSeedsAreDistinctAndRecreationAndSeekingAreDeterministic() {
        val a=CubeSequence(1265L*SAMPLE_RATE,123)
        val b=CubeSequence(1265L*SAMPLE_RATE,124)
        val copy=CubeSequence(1265L*SAMPLE_RATE,123)
        assertFalse(a.stateAt(0).contentEquals(b.stateAt(0)))
        assertNotEquals(a.turns,b.turns)
        for(index in listOf(0,900,5,1000,1000,200,a.turns.size)) {
            assertArrayEquals(a.stateAt(index),copy.stateAt(index))
            assertTrue(a.lastSeekTurns<128)
        }
    }
    @Test fun aDayLongRouteHasBoundedMemoryAndInstantBoundedSeek() {
        val route=CubeSequence(MAX_PERIOD,99)
        assertEquals(5,route.size)
        assertTrue(route.checkpointBytes<2_500_000)
        for(index in listOf(80000,3,60000,60001,0,86394)) {
            val state=route.stateAt(index).copyOf()
            assertTrue(route.lastSeekTurns<128)
            assertArrayEquals(state,route.stateAt(index))
            assertEquals(0,route.lastSeekTurns)
        }
        assertTrue(CubeSequence.isSolved(route.stateAt(route.turns.size)))
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            CubeSequence(MAX_PERIOD,99) { throw java.util.concurrent.CancellationException() }
        }
    }
    @Test fun solveFinishesBeforeRevealAndStaysSolvedThroughTheEnd() {
        for(seconds in listOf(5L,10L,60L,1265L,7200L)) {
            val period=seconds*SAMPLE_RATE
            val timing=CubeTiming(period)
            val route=CubeSequence(period)
            assertEquals(kotlin.math.min(5.0,seconds/2.0),seconds*(1-timing.solveEnd),.000001)
            for(p in listOf(timing.solveEnd.toFloat(),((timing.solveEnd+1)/2).toFloat(),1f))
                assertTrue(CubeSequence.isSolved(route.stateAt(route.moveIndex(timing.solveFraction(p)))))
            assertEquals(0f,timing.revealFraction(timing.solveEnd.toFloat()))
            assertEquals(1f,timing.revealFraction(1f))
        }
    }
    @Test fun onlyRequestedStylesRemainAndLegacyPreferencesMigrate() {
        assertEquals(listOf(Visual.BIRD,Visual.CUBE,Visual.GLASS),Visual.entries.toList())
        for(name in listOf("LOOM","GARDEN","INK","STARS","LANDSCAPE"))assertEquals(Visual.BIRD,Visual.fromSaved(name))
    }
}
