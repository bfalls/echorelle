package com.echorelle.android

import kotlin.math.min

/** Shared cube/origami timing: reserve up to five observed seconds for the completed object. */
internal class CubeTiming(period: Long) {
    private val seconds=period.coerceAtLeast(1).toDouble()/SAMPLE_RATE
    val revealSeconds=min(5.0,seconds/2)
    val solveEnd=1-revealSeconds/seconds
    fun solveFraction(whole: Float): Float = if(whole>=solveEnd.toFloat())1f else (whole/solveEnd).toFloat().coerceIn(0f,1f)
    fun revealFraction(whole: Float): Float = if(whole<=solveEnd.toFloat())0f else ((whole-solveEnd)/(1-solveEnd)).toFloat().coerceIn(0f,1f)
    fun isRevealing(whole: Double)=whole>=solveEnd
}
