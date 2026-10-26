package com.echorelle.android

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.*

/** Legal sticker poses on individual cubelet bodies, with an audio-phase-bound solved reveal. */
internal class CubeVisual(period: Long, seed: Long=period, checkCancelled: () -> Unit = {}) {
    val sequence=CubeSequence(period,seed,checkCancelled)
    val timing=CubeTiming(period)
    private class Face(val id: Int,val color: Int,val cell: Int=-1,val axis: Int=0,val layer: Int=1,val sticker: Int=-1) {
        val points=FloatArray(8)
        var depth=0f;var shade=0;var visible=false
    }
    private val size=sequence.size
    private val bodyFaces=6*size*size*size
    private val faces=Array(bodyFaces+6*size*size) { id ->
        if(id<bodyFaces) Face(id,-1,id/6,id%6/2,if(id%2==0)1 else -1)
        else Face(id,(id-bodyFaces)/(size*size),sticker=id-bodyFaces)
    }
    private val order=faces.copyOf()
    private val path=Path()
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE }
    private val palette=intArrayOf(0xffd84938.toInt(),0xffed8d32.toInt(),0xfff6f3e9.toInt(),
        0xfff1cb37.toInt(),0xff35a887.toInt(),0xff397fc7.toInt())
    private val shades=Array(6) { face -> IntArray(64) { shade ->
        val amount=.64f+shade*.36f/63
        val color=palette[face]
        Color.rgb((Color.red(color)*amount).toInt(),(Color.green(color)*amount).toInt(),(Color.blue(color)*amount).toInt())
    } }
    private var width=0f;private var height=0f;private var night=false
    private var background: Shader?=null
    private var cx=0f;private var cy=0f;private var scale=0f
    private var turnCos=1f;private var turnSin=0f
    private var yawCos=1f;private var yawSin=0f
    private var pitchCos=1f;private var pitchSin=0f
    private var rollCos=1f;private var rollSin=0f
    private val transformed=FloatArray(3)
    private val normal=FloatArray(3)
    private val center=FloatArray(3)

    private fun transform(px: Float,py: Float,pz: Float,axis: Int,out: FloatArray) {
        var x=px;var y=py;var z=pz
        when(axis) {
            0 -> {y=py*turnCos-pz*turnSin;z=py*turnSin+pz*turnCos}
            1 -> {z=pz*turnCos-px*turnSin;x=pz*turnSin+px*turnCos}
            2 -> {x=px*turnCos-py*turnSin;y=px*turnSin+py*turnCos}
        }
        val yawX=x*yawCos+z*yawSin
        val yawZ=z*yawCos-x*yawSin
        val pitchY=y*pitchCos-yawZ*pitchSin
        val pitchZ=y*pitchSin+yawZ*pitchCos
        out[0]=yawX*rollCos-pitchY*rollSin
        out[1]=yawX*rollSin+pitchY*rollCos
        out[2]=pitchZ
    }
    private fun project(px: Float,py: Float,pz: Float,axis: Int,out: FloatArray,index: Int) {
        transform(px,py,pz,axis,transformed)
        val x=transformed[0];val y=transformed[1];val z=transformed[2]
        out[index]=cx+scale*(.80f*x-.60f*z)
        out[index+1]=cy-scale*(.86f*y-.51f*(.60f*x+.80f*z))
    }
    private fun rounded(points: FloatArray) {
        path.rewind()
        for(i in 0..3) {
            val prev=(i+3)%4;val next=(i+1)%4
            val x=points[i*2];val y=points[i*2+1]
            val enterX=x+(points[prev*2]-x)*.10f;val enterY=y+(points[prev*2+1]-y)*.10f
            val exitX=x+(points[next*2]-x)*.10f;val exitY=y+(points[next*2+1]-y)*.10f
            if(i==0) path.moveTo(enterX,enterY) else path.lineTo(enterX,enterY)
            path.quadTo(x,y,exitX,exitY)
        }
        path.close()
    }
    fun draw(canvas: Canvas,w: Float,h: Float,fraction: Float,dark: Boolean) {
        if(BuildConfig.DEBUG) VisualWorkStats.draws.incrementAndGet()
        if(width!=w || height!=h || night!=dark) {
            width=w;height=h;night=dark
            background=LinearGradient(0f,0f,w,h,if(dark)0xff142330.toInt() else 0xffeceae5.toInt(),
                if(dark)0xff253443.toInt() else 0xfff7f5ee.toInt(),Shader.TileMode.CLAMP)
        }
        cx=w*.5f;cy=h*.48f;scale=min(w/5.8f,h/5.7f)*3/size
        paint.shader=background;paint.alpha=255;canvas.drawRect(0f,0f,w,h,paint);paint.shader=null
        for(i in 12 downTo 1) {
            paint.color=Color.argb(if(dark)4 else 3,9,18,24)
            val shadowScale=scale*size/3
            canvas.drawOval(cx-shadowScale*(1.6f+i*.025f),cy+shadowScale*(2.30f-i*.008f),
                cx+shadowScale*(1.6f+i*.025f),cy+shadowScale*(2.45f+i*.008f),paint)
        }
        val index=sequence.moveIndex(timing.solveFraction(fraction))
        val state=sequence.stateAt(index)
        val turn=sequence.turns.getOrNull(index)
        // Use 80% of each move's slot for the turn, with a short settled beat
        // either side. This makes turns 25% quicker without shifting the route.
        val t=((sequence.moveFraction(index,timing.solveFraction(fraction))-.1f)/.8f).coerceIn(0f,1f)
        val eased=t*t*t*(t*(t*6-15)+10)
        val angle=(turn?.direction?:0)*PI.toFloat()/2*eased
        turnCos=cos(angle);turnSin=sin(angle)
        val reveal=timing.revealFraction(fraction)
        // Show the back and underside, then return exactly to the original presentation.
        // The endpoint guard avoids a tiny 2pi floating-point pose drift on reattachment.
        val show=if(reveal<.00001f || reveal>.99999f)0f else reveal
        val orbit=show*show*show*(show*(show*6-15)+10)
        val yaw=2*PI.toFloat()*orbit
        val pitch=sin(2*PI.toFloat()*show)*1.0f
        val roll=sin(4*PI.toFloat()*show)*.16f
        yawCos=cos(yaw);yawSin=sin(yaw)
        pitchCos=cos(pitch);pitchSin=sin(pitch)
        rollCos=cos(roll);rollSin=sin(roll)
        for(f in faces) {
            val body=f.color<0
            val i=f.sticker*6
            val px=if(body)f.cell/(size*size)-(size-1)/2f else state[i].toFloat()/sequence.coordinateStep
            val py=if(body)f.cell/size%size-(size-1)/2f else state[i+1].toFloat()/sequence.coordinateStep
            val pz=if(body)f.cell%size-(size-1)/2f else state[i+2].toFloat()/sequence.coordinateStep
            val nx=if(body)(if(f.axis==0)f.layer else 0).toFloat() else state[i+3].toFloat()
            val ny=if(body)(if(f.axis==1)f.layer else 0).toFloat() else state[i+4].toFloat()
            val nz=if(body)(if(f.axis==2)f.layer else 0).toFloat() else state[i+5].toFloat()
            val selected=turn!=null && (if(turn.axis==0)px else if(turn.axis==1)py else pz)==turn.layer.toFloat()/sequence.coordinateStep
            val turnAxis=if(selected)turn!!.axis else -1
            transform(nx,ny,nz,turnAxis,normal)
            val nX=normal[0];val nY=normal[1];val nZ=normal[2]
            f.visible=.51f*nY+.86f*(.60f*nX+.80f*nZ)>.001f
            if(!f.visible) continue
            val offset=if(body).495f else .503f
            transform(px+nx*offset,py+ny*offset,pz+nz*offset,turnAxis,center)
            f.depth=.51f*center[1]+.86f*(.60f*center[0]+.80f*center[2])
            f.shade=((.3f*nX+.8f*nY+.55f*nZ).coerceIn(0f,1f)*63).toInt()
            val normalAxis=if(nx!=0f)0 else if(ny!=0f)1 else 2
            val half=if(body).495f else .434f
            for(corner in 0..3) {
                val a=if(corner==0 || corner==3)-half else half
                val b=if(corner<2)-half else half
                val dx=if(normalAxis==0)0f else if(normalAxis==1)b else a
                val dy=if(normalAxis==1)0f else if(normalAxis==2)b else a
                val dz=if(normalAxis==2)0f else if(normalAxis==0)b else a
                project(px+nx*offset+dx,py+ny*offset+dy,pz+nz*offset+dz,turnAxis,f.points,corner*2)
            }
        }
        java.util.Arrays.sort(order) { a,b ->
            val depth=a.depth.compareTo(b.depth)
            if(depth==0)a.id.compareTo(b.id) else depth
        }
        for(f in order) if(f.visible) {
            rounded(f.points)
            paint.color=if(f.color<0)Color.rgb(19+f.shade/5,25+f.shade/5,29+f.shade/5) else shades[f.color][f.shade]
            canvas.drawPath(path,paint)
            if(f.color>=0) {
                edge.strokeWidth=(scale*.012f).coerceAtLeast(.5f);edge.color=0x38ffffff
                canvas.drawPath(path,edge)
            }
        }
    }
}
