package com.echorelle.android

import java.util.PriorityQueue
import kotlin.math.*

internal enum class GlassArt(val asset: String,val title: String) {
    BLOSSOM("blossom","Magnolia"), IRIS("iris","Iris garden"), GARDEN("garden","River of light");
    companion object {
        fun forPeriod(period: Long)=when {
            period<180L*SAMPLE_RATE -> BLOSSOM
            period<900L*SAMPLE_RATE -> IRIS
            else -> GARDEN
        }
    }
}

/** Original sheet coordinates; segmentation is performed offline, never on the audio/UI path. */
internal class GlassPane(val points: FloatArray) {
    val area: Double
    val cx: Float
    val cy: Float
    init {
        var a=0.0;var x=0.0;var y=0.0
        for(i in points.indices step 2) {
            val j=(i+2)%points.size
            a+=points[i].toDouble()*points[j+1]-points[j].toDouble()*points[i+1]
            x+=points[i];y+=points[i+1]
        }
        area=abs(a)/2;cx=(x/(points.size/2)).toFloat();cy=(y/(points.size/2)).toFloat()
    }
    fun split(): Pair<GlassPane,GlassPane>? {
        var minX=1f;var maxX=0f;var minY=1f;var maxY=0f
        for(i in points.indices step 2) {minX=min(minX,points[i]);maxX=max(maxX,points[i]);minY=min(minY,points[i+1]);maxY=max(maxY,points[i+1])}
        val axis=if((maxX-minX)*1.5f>maxY-minY)0 else 1
        fun clip(side: Float,line: Float): GlassPane? {
            val out=ArrayList<Float>()
            for(i in points.indices step 2) {
                val j=(i+2)%points.size
                val a=(points[i+axis]-line)*side;val b=(points[j+axis]-line)*side
                if(a>=0) {out.add(points[i]);out.add(points[i+1])}
                if((a>=0)!=(b>=0)) {
                    val t=a/(a-b)
                    out.add(points[i]+(points[j]-points[i])*t);out.add(points[i+1]+(points[j+1]-points[i+1])*t)
                }
            }
            return if(out.size<6)null else GlassPane(out.toFloatArray()).takeIf{it.area>1e-12}
        }
        // A concave contour may have a long zero-area bridge at a previous cut.
        // Bisect actual area, rather than its bounding box, to avoid splitting that bridge.
        var low=if(axis==0)minX else minY;var high=if(axis==0)maxX else maxY
        repeat(14) {
            val mid=(low+high)/2
            if((clip(1f,mid)?.area?:0.0)>area/2)low=mid else high=mid
        }
        val line=(low+high)/2
        val a=clip(1f,line)?:return null;val b=clip(-1f,line)?:return null
        return a to b
    }
}

/** Bounded deterministic construction route. Seeking computes one index, never replays intervals. */
internal class GlassSequence(period: Long,original: List<GlassPane>,checkCancelled: ()->Unit={}) {
    val timing=CubeTiming(period)
    val count=((period.toDouble()/SAMPLE_RATE-timing.revealSeconds)/2.5).roundToInt().coerceIn(1,34560)
    val pieces: List<List<GlassPane>>
    val secondsPerPiece=(period.toDouble()/SAMPLE_RATE-timing.revealSeconds)/count
    init {
        require(original.isNotEmpty())
        val queue=PriorityQueue<GlassPane>(compareByDescending<GlassPane>{it.area}.thenBy{it.cx}.thenBy{it.cy})
        queue.addAll(original)
        while(queue.size<count) {
            if(queue.size%32==0)checkCancelled()
            val biggest=queue.remove();val split=biggest.split()?:error("Glass pane cannot be subdivided")
            queue.add(split.first);queue.add(split.second)
        }
        // Distribute assembly across the picture; ordering is stable across redraws and recovery.
        val panes=queue.toList().sortedBy { pane ->
            var z=(pane.cx*65535).toInt()*73856093 xor ((pane.cy*65535).toInt()*19349663)
            z=z xor (z ushr 16);z
        }
        pieces=if(panes.size==count)panes.map{listOf(it)} else {
            // Short intervals assemble sections. Balance their glass area so a quarter of
            // the construction still looks approximately a quarter complete.
            val groups=List(count){ArrayList<GlassPane>()};val areas=DoubleArray(count)
            val bins=PriorityQueue<Int>(compareBy<Int>{areas[it]}.thenBy{it})
            bins.addAll(0 until count)
            for(pane in panes.sortedByDescending{it.area}) {
                checkCancelled();val index=bins.remove()
                groups[index].add(pane);areas[index]+=pane.area;bins.add(index)
            }
            groups
        }
    }
    fun completed(whole: Float)=floor(timing.solveFraction(whole).toDouble()*count).toInt().coerceIn(0,count)
    fun local(whole: Float): Float {
        val position=timing.solveFraction(whole)*count
        return if(position>=count)1f else (position-floor(position)).coerceIn(0f,1f)
    }
}
