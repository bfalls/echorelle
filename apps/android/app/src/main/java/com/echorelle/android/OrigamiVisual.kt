package com.echorelle.android

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.*
import kotlin.random.Random

/** Shaded, textured rendering of a single material sheet and exactly one active hinge. */
internal class OrigamiVisual(val model: SheetFolds, period: Long) {
    constructor(period: Long,checkCancelled: ()->Unit={}) : this(OrigamiPrograms.create(PaperProject.forPeriod(period),checkCancelled),period)
    val timing=CubeTiming(period)
    private class Face(val id: Int) {
        val points=FloatArray(6);val world=FloatArray(9);val uv=FloatArray(6)
        var depth=0f;var shade=0;var back=0;var edges=0
    }
    private val faces=Array(model.maxTriangles){Face(it)}
    private val order=faces.copyOf()
    private val grain=makePaper()
    private val texture=grain.copy(Bitmap.Config.ARGB_8888,true)
    private val textureCanvas=Canvas(texture)
    private val shader=BitmapShader(texture,Shader.TileMode.MIRROR,Shader.TileMode.MIRROR)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE}
    private val creasePaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val matrix=Matrix()
    private val path=Path()
    private val flatPoints=FloatArray(8)
    private val flatUV=FloatArray(6)
    private var inkCount=-1
    private var width=0f;private var height=0f;private var night=false
    private var background: Shader?=null
    private val palette=when(model.project) {
        PaperProject.DART -> intArrayOf(0xfff0e6ce.toInt(),0xfffaf6e8.toInt())
        PaperProject.HELMET -> intArrayOf(0xfff4ead5.toInt(),0xff71959c.toInt())
        PaperProject.SHELL -> intArrayOf(0xff9cad84.toInt(),0xffe6dfb9.toInt())
    }
    private val lights=Array(2){side->Array(96){n->
        val amount=.58+n*.0052
        val c=palette[side]
        LightingColorFilter(Color.rgb((Color.red(c)*amount).toInt().coerceAtMost(255),
            (Color.green(c)*amount).toInt().coerceAtMost(255),(Color.blue(c)*amount).toInt().coerceAtMost(255)),0)
    }}
    fun draw(canvas: Canvas,w: Float,h: Float,fraction: Float,dark: Boolean) {
        if(BuildConfig.DEBUG)VisualWorkStats.paperDraws.incrementAndGet()
        if(width!=w || height!=h || night!=dark) {
            width=w;height=h;night=dark
            background=LinearGradient(0f,0f,w,h,if(dark)0xff142330.toInt() else 0xffe9e5dc.toInt(),
                if(dark)0xff253442.toInt() else 0xfff7f5ec.toInt(),Shader.TileMode.CLAMP)
        }
        paint.shader=background;paint.colorFilter=null;paint.alpha=255
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=null
        val folding=timing.solveFraction(fraction)
        val reveal=timing.revealFraction(fraction)
        // Exact endpoint poses also make paused seeking and interval completion deterministic.
        val show=if(reveal<.00001f || reveal>.99999f)0.0 else reveal.toDouble()
        val orbit=show*show*show*(show*(show*6-15)+10)
        val pitch=sin(2*PI*show)*.42
        val roll=sin(4*PI*show)*.12
        val cp=cos(pitch);val sp=sin(pitch);val cr=cos(roll);val sr=sin(roll)
        val stage=model.stages[model.step(folding)]
        val local=model.local(folding)
        val angle=stage.move.angle*local
        val c=cos(angle);val s=sin(angle)
        val hinge=stage.move.hinge
        val yaw=(if(model.project==PaperProject.DART) .26+1.0*folding else .26)+2*PI*orbit
        val cameraCos=cos(yaw);val cameraSin=sin(yaw)
        val currentInk=if(folding>=1f)model.inks.size else stage.inkCount
        if(inkCount!=currentInk) {
            textureCanvas.drawBitmap(grain,0f,0f,null)
            for(i in 0 until currentInk) {
                val ink=model.inks[i]
                creasePaint.strokeWidth=.65f;creasePaint.color=0x465b624f
                textureCanvas.drawLine((ink.ax*384).toFloat(),((1-ink.ay)*384).toFloat(),
                    (ink.bx*384).toFloat(),((1-ink.by)*384).toFloat(),creasePaint)
            }
            inkCount=currentInk
        }
        var minX=Double.POSITIVE_INFINITY;var maxX=Double.NEGATIVE_INFINITY
        var minY=Double.POSITIVE_INFINITY;var maxY=Double.NEGATIVE_INFINITY
        var flat=true
        for((index,triangle) in stage.triangles.withIndex()) {
            val face=faces[index]
            for(i in 0..2) {
                val p=when(i){0->triangle.a;1->triangle.b;else->triangle.c}
                var x=p.x;var y=p.y;var z=p.z
                if(triangle.moving) {
                    val rx=x-hinge.ax;val ry=y-hinge.ay;val rz=z-hinge.az
                    val dot=(hinge.ux*rx+hinge.uy*ry+hinge.uz*rz)*(1-c)
                    x=hinge.ax+rx*c+(hinge.uy*rz-hinge.uz*ry)*s+hinge.ux*dot
                    y=hinge.ay+ry*c+(hinge.uz*rx-hinge.ux*rz)*s+hinge.uy*dot
                    z=hinge.az+rz*c+(hinge.ux*ry-hinge.uy*rx)*s+hinge.uz*dot
                }
                // Rigid whole-object presentation, after the final hinge has stopped.
                val py=y*cp-z*sp;val pz=y*sp+z*cp
                val px=x*cr-py*sr;y=x*sr+py*cr;x=px;z=pz
                face.world[i*3]=x.toFloat();face.world[i*3+1]=y.toFloat();face.world[i*3+2]=z.toFloat()
                if(abs(z)>1e-6)flat=false
                face.uv[i*2]=(p.u*384).toFloat();face.uv[i*2+1]=((1-p.v)*384).toFloat()
                val screenX=cameraCos*x+cameraSin*z
                val screenY=.90*y-.435*(cameraCos*z-cameraSin*x)
                face.points[i*2]=screenX.toFloat();face.points[i*2+1]=screenY.toFloat()
                minX=min(minX,screenX);maxX=max(maxX,screenX);minY=min(minY,screenY);maxY=max(maxY,screenY)
            }
            val q=face.world
            val ax=q[3]-q[0];val ay=q[4]-q[1];val az=q[5]-q[2]
            val bx=q[6]-q[0];val by=q[7]-q[1];val bz=q[8]-q[2]
            val nx=ay*bz-az*by;val ny=az*bx-ax*bz;val nz=ax*by-ay*bx
            val length=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(.000001f)
            face.back=if(-.90*cameraSin*nx+.435*ny+.90*cameraCos*nz>=0)0 else 1
            face.shade=(abs((-.35f*nx+.65f*ny+.68f*nz)/length)*95).toInt().coerceIn(0,95)
            face.depth=((- .90*cameraSin*(q[0]+q[3]+q[6])+.435*(q[1]+q[4]+q[7])+.90*cameraCos*(q[2]+q[5]+q[8]))/3+triangle.layer*.00001).toFloat()
            face.edges=triangle.edges
        }
        val count=stage.triangles.size
        if(flat && show==0.0)for(i in 0 until count)faces[i].depth=(if(local<.5)stage.triangles[i].startLayer else stage.triangles[i].layer)*.001f
        // Only camera framing changes here; paper coordinates/metrics never do.
        val approach=((folding-.65f)/.35f).coerceIn(0f,1f)
        val requested=min(w/2.8,h/2.5)*(1+1.1*approach*approach*(3-2*approach))
        val scale=min(requested,min(w/((maxX-minX)+.45),h/((maxY-minY)+.45))).toFloat()
        val cx=w*.5f-((minX+maxX)/2*scale).toFloat()
        val cy=h*.48f+((minY+maxY)/2*scale).toFloat()
        for(i in 10 downTo 1) {
            paint.color=Color.argb(if(dark)4 else 3,9,18,24)
            canvas.drawOval(w*.5f-scale*(.60f+i*.02f),h*.89f-i*.35f,w*.5f+scale*(.60f+i*.02f),h*.92f+i*.35f,paint)
        }
        paint.alpha=255
        if(fraction<=0f) {
            for((i,p) in model.initial.withIndex()) {
                flatPoints[i*2]=cx+(scale*cameraCos*p.x).toFloat()
                flatPoints[i*2+1]=cy-(scale*(.90*p.y+.435*cameraSin*p.x)).toFloat()
                if(i<3){flatUV[i*2]=(p.u*384).toFloat();flatUV[i*2+1]=((1-p.v)*384).toFloat()}
            }
            matrix.reset();matrix.setPolyToPoly(flatUV,0,flatPoints,0,3);shader.setLocalMatrix(matrix)
            path.rewind();path.moveTo(flatPoints[0],flatPoints[1])
            for(i in 1..3)path.lineTo(flatPoints[i*2],flatPoints[i*2+1])
            path.close();paint.shader=shader;paint.colorFilter=lights[0][92]
            canvas.drawPath(path,paint);paint.shader=null;paint.colorFilter=null
            if(BuildConfig.DEBUG)VisualWorkStats.lastPaperProject=model.project
            return
        }
        for(i in 0 until count)for(v in 0..2) {
            val points=faces[i].points
            points[v*2]=cx+scale*points[v*2];points[v*2+1]=cy-scale*points[v*2+1]
        }
        for(i in 0 until count)order[i]=faces[i]
        java.util.Arrays.sort(order,0,count){a,b->val depth=a.depth.compareTo(b.depth);if(depth==0)a.id.compareTo(b.id) else depth}
        for(i in 0 until count) {
            val f=order[i];val p=f.points
            path.rewind();path.moveTo(p[0],p[1]);path.lineTo(p[2],p[3]);path.lineTo(p[4],p[5]);path.close()
            matrix.reset()
            if(!matrix.setPolyToPoly(f.uv,0,p,0,3))continue
            shader.setLocalMatrix(matrix);paint.shader=shader;paint.colorFilter=lights[f.back][f.shade]
            canvas.drawPath(path,paint)
            paint.shader=null;paint.colorFilter=null
            edge.strokeWidth=(scale*.0025f).coerceAtLeast(.45f);edge.color=0x5042473c
            for(k in 0..2)if(f.edges and (1 shl k)!=0) {
                val j=(k+1)%3
                canvas.drawLine(p[k*2],p[k*2+1],p[j*2],p[j*2+1],edge)
            }
        }
        if(BuildConfig.DEBUG)VisualWorkStats.lastPaperProject=model.project
    }

    private fun makePaper(): Bitmap {
        val random=Random(49271)
        val pixels=IntArray(384*384) {
            val grain=random.nextInt(-10,11)
            Color.rgb((241+grain).coerceIn(0,255),(235+grain).coerceIn(0,255),(220+grain).coerceIn(0,255))
        }
        val bitmap=Bitmap.createBitmap(384,384,Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels,0,384,0,0,384,384)
        val canvas=Canvas(bitmap)
        val fiber=Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(1800) {
            val x=random.nextFloat()*384;val y=random.nextFloat()*384
            fiber.color=if(it%3==0) Color.argb(23,91,76,51) else Color.argb(55,255,254,241)
            fiber.strokeWidth=.25f+random.nextFloat()*.55f
            canvas.drawLine(x,y,x+random.nextFloat()*7-3,y+random.nextFloat()*1.8f-.9f,fiber)
        }
        return bitmap
    }
}
