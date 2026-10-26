package com.echorelle.android

import kotlin.math.ceil
import kotlin.random.Random

/** A seeded legal scramble, solved by inversion. Sparse checkpoints bound seek work. */
internal class CubeSequence(period: Long, seed: Long=period, checkCancelled: () -> Unit = {}) {
    val size=when { period>=3600L*SAMPLE_RATE -> 5; period>=1200L*SAMPLE_RATE -> 4; else -> 3 }
    val coordinateStep=if(size%2==0)2 else 1
    val radius=(size-1)*coordinateStep/2
    data class Turn(val axis: Int,val layer: Int,val direction: Int) {
        fun inverse()=copy(direction=-direction)
    }
    private val solveSeconds=period.coerceAtLeast(1).toDouble()/SAMPLE_RATE-CubeTiming(period).revealSeconds
    val turns: List<Turn>
    val boundaries: FloatArray
    private val stride=128
    private val checkpoints: Array<IntArray>
    private val solved=solvedState(size)
    private val working=solved.copyOf()
    private var workingIndex=-1
    internal var lastSeekTurns=0; private set
    internal val checkpointBytes get()=checkpoints.size*solved.size*4

    init {
        val count=ceil(solveSeconds).toInt().coerceIn(1,86400)
        val random=Random(seed)
        val scramble=ArrayList<Turn>(count)
        var previousAxis=-1
        repeat(count) { i ->
            if(i%stride==0) checkCancelled()
            // Different consecutive axes avoid trivial immediate undoing.
            val axis=if(previousAxis<0)random.nextInt(3) else (previousAxis+1+random.nextInt(2))%3
            val layer=if(size==3) { if(random.nextBoolean())radius else -radius }
                else -radius+random.nextInt(size)*coordinateStep
            scramble+=Turn(axis,layer,if(random.nextBoolean())1 else -1)
            previousAxis=axis
        }
        turns=scramble.asReversed().map { it.inverse() }
        boundaries=FloatArray(count+1) { (it/solveSeconds).toFloat().coerceAtMost(1f) }
        boundaries[count]=1f
        checkpoints=Array((count-1)/stride+1) { IntArray(solved.size) }
        // Walk construction once, recording only every 128th solution pose.
        val pose=solved.copyOf()
        for(i in turns.lastIndex downTo 0) {
            if(i%stride==0) checkCancelled()
            apply(pose,turns[i].inverse())
            if(i%stride==0) pose.copyInto(checkpoints[i/stride])
        }
    }

    /** Borrowed read-only pose; next call may update this buffer. No skipped-interval replay. */
    fun stateAt(index: Int): IntArray {
        require(index in 0..turns.size)
        lastSeekTurns=0
        if(index==turns.size) return solved
        if(workingIndex !in 0..index || index-workingIndex>=stride) {
            checkpoints[index/stride].copyInto(working)
            workingIndex=index/stride*stride
        }
        while(workingIndex<index) { apply(working,turns[workingIndex++]);lastSeekTurns++ }
        return working
    }
    fun moveIndex(fraction: Float): Int {
        if(fraction>=1f) return turns.size
        var low=0;var high=turns.lastIndex
        while(low<high) {
            val middle=(low+high+1)/2
            if(boundaries[middle]<=fraction) low=middle else high=middle-1
        }
        return low
    }
    fun moveFraction(index: Int,fraction: Float): Float = if(index>=turns.size)0f else
        ((fraction-boundaries[index])/(boundaries[index+1]-boundaries[index])).coerceIn(0f,1f)

    companion object {
        fun solvedState(size: Int=3): IntArray = IntArray(36*size*size).also { state ->
            require(size in 3..5)
            val step=if(size%2==0)2 else 1
            val radius=(size-1)*step/2
            var sticker=0
            for(axis in 0..2) for(sign in listOf(1,-1)) for(u in -radius..radius step step) for(v in -radius..radius step step) {
                val i=sticker++*6
                state[i+axis]=sign*radius
                state[i+(axis+1)%3]=u;state[i+(axis+2)%3]=v
                state[i+3+axis]=sign
            }
        }
        fun apply(state: IntArray, turn: Turn) {
            val a=(turn.axis+1)%3;val b=(turn.axis+2)%3
            for(i in state.indices step 6) if(state[i+turn.axis]==turn.layer) {
                for(offset in 0..3 step 3) {
                    val x=state[i+offset+a];val y=state[i+offset+b]
                    state[i+offset+a]=-turn.direction*y
                    state[i+offset+b]=turn.direction*x
                }
            }
        }
        fun isSolved(state: IntArray): Boolean {
            val area=state.size/36
            for(sticker in 0 until state.size/6) {
                val face=sticker/area;val axis=face/2;val layer=if(face%2==0) 1 else -1
                if(state[sticker*6+3+axis]!=layer) return false
            }
            return true
        }
        fun parse(text: String, radius: Int=1): List<Turn> = buildList {
            for(token in text.split(' ').filter { it.isNotBlank() }) {
                val face="RLUDFB".indexOf(token[0]);require(face>=0)
                val layer=if(face%2==0) 1 else -1
                val turn=Turn(face/2,layer*radius,if(token.endsWith("'")) layer else -layer)
                repeat(if(token.endsWith('2')) 2 else 1) { add(turn) }
            }
        }
    }
}
