package com.echorelle.android

import android.content.res.AssetManager
import android.graphics.*
import org.json.JSONObject
import kotlin.math.*

internal class GlassSource(val bitmap: Bitmap,val panes: List<GlassPane>) {
    companion object {
        fun load(assets: AssetManager,art: GlassArt,check: ()->Unit): GlassSource {
            val json=assets.open("glass/${art.asset}.json").bufferedReader().use{JSONObject(it.readText())}
            require(json.getInt("version")==1)
            val array=json.getJSONArray("panes")
            val panes=List(array.length()) { i ->
                check();val polygon=array.getJSONArray(i)
                GlassPane(FloatArray(polygon.length()*2){n->polygon.getJSONArray(n/2).getDouble(n%2).toFloat()})
            }
            val bitmap=assets.open("glass/${art.asset}.webp").use{BitmapFactory.decodeStream(it)}
            require(bitmap!=null && bitmap.width<=1024 && bitmap.height<=1024)
            return GlassSource(bitmap,panes)
        }
    }
}

/** Per-card render state. All I/O, topology and path creation happen before construction completes. */
internal class StainedGlassVisual(val period: Long,source: GlassSource,check: ()->Unit={}) {
    val sequence=GlassSequence(period,source.panes,check)
    val timing get()=sequence.timing
    private val image=source.bitmap
    private val iw=image.width.toFloat();private val ih=image.height.toFloat()
    private val shader=BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
    private val paths=sequence.pieces.map { panes ->
        check();Path().apply { for(pane in panes) {
            val p=pane.points;moveTo(p[0]*iw,p[1]*ih)
            for(i in 2 until p.size step 2)lineTo(p[i]*iw,p[i+1]*ih)
            close()
        } }
    }
    private val centers=paths.map{RectF().also{r->it.computeBounds(r,true)}}
    private val assembled=Bitmap.createBitmap(image.width,image.height,Bitmap.Config.ARGB_8888)
    private val assembly=Canvas(assembled)
    private val settlementBatch=Path()
    private val partialBatch=Path()
    private var partialCount=-1
    private var settled=0
    private val glass=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{this.shader=this@StainedGlassVisual.shader}
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rim=Paint(Paint.ANTI_ALIAS_FLAG).apply{style=Paint.Style.STROKE;strokeJoin=Paint.Join.ROUND;strokeWidth=.7f;color=0x89535d58.toInt()}
    private val fog=Array(4){Paint(Paint.ANTI_ALIAS_FLAG)}
    private val beams=Path()
    private var width=0f;private var height=0f;private var dark=false
    private var room: Shader?=null
    private var pool: Shader?=null
    private var sunlight: Shader?=null

    fun draw(canvas: Canvas,w: Float,h: Float,whole: Float,night: Boolean,motion: Boolean=true) {
        if(BuildConfig.DEBUG)VisualWorkStats.glassDraws.incrementAndGet()
        if(width!=w || height!=h || dark!=night) {
            width=w;height=h;dark=night
            room=LinearGradient(0f,0f,w,h,if(night)0xff07121a.toInt() else 0xff202e33.toInt(),0xff0f1b22.toInt(),Shader.TileMode.CLAMP)
            pool=RadialGradient(w*.5f,h*.79f,w*.46f,intArrayOf(0x294bbfb0,0x123e8b9d,0x00213f4b),null,Shader.TileMode.CLAMP)
            sunlight=RadialGradient(w*.60f,h*.30f,w*.55f,intArrayOf(0x50fff0c2,0x13ffd28c,0x00ffffff),null,Shader.TileMode.CLAMP)
            for(i in fog.indices)fog[i].shader=RadialGradient(0f,0f,w*(.28f+i*.03f),intArrayOf(0x13c7d8d4,0x07b8d0d0,0x00d6e4e2),null,Shader.TileMode.CLAMP)
            beams.rewind();beams.moveTo(w*.36f,h*.08f);beams.lineTo(w*.88f,h);beams.lineTo(w*.29f,h);beams.close()
        }
        paint.shader=room;paint.alpha=255;canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=pool;canvas.drawRect(0f,0f,w,h,paint);paint.shader=null
        val scale=min(w*.78f/iw,h*.80f/ih)
        val left=(w-iw*scale)/2;val top=h*.075f
        val right=left+iw*scale;val bottom=top+ih*scale
        paint.color=0x65000000;canvas.drawRoundRect(left-5,top+4,right+7,bottom+11,4f,4f,paint)
        paint.color=0xff555a53.toInt();canvas.drawRect(left-3,top-3,right+3,bottom+3,paint)
        paint.color=0xff141f23.toInt();canvas.drawRect(left,top,right,bottom,paint)
        val count=sequence.completed(whole)
        val batchEnd=count/256*256
        if(batchEnd<settled) {assembled.eraseColor(Color.TRANSPARENT);settled=0;partialCount=-1}
        // Normal animation adds one piece every ~2.5s. Resume seeks once into this bounded bitmap.
        // Fixed batch boundaries keep antialiasing identical after arbitrary seeks.
        while(settled<batchEnd) {
            settlementBatch.rewind()
            val end=settled+256
            while(settled<end)settlementBatch.addPath(paths[settled++])
            assembly.drawPath(settlementBatch,glass);assembly.drawPath(settlementBatch,rim)
        }
        if(partialCount!=count) {
            partialBatch.rewind()
            for(i in settled until count)partialBatch.addPath(paths[i])
            partialCount=count
        }
        canvas.save();canvas.translate(left,top);canvas.scale(scale,scale)
        paint.alpha=255;canvas.drawBitmap(assembled,0f,0f,paint)
        canvas.drawPath(partialBatch,glass);canvas.drawPath(partialBatch,rim)
        if(count<sequence.count && motion) {
            val t=sequence.local(whole).toDouble()
            val eased=t*t*t*(t*(t*6-15)+10)
            val remaining=(1-eased).toFloat()
            val rect=centers[count];val cx=rect.centerX();val cy=rect.centerY()
            val side=count%4
            val startX=when(side){0->-left/scale-rect.width()-cx;1->(w-left)/scale+rect.width()-cx;else->sin(count*2.4).toFloat()*iw*.5f}
            val startY=when(side){2->-top/scale-rect.height()-cy;3->(h-top)/scale+rect.height()-cy;else->cos(count*1.7).toFloat()*ih*.5f}
            canvas.save()
            canvas.translate(startX*remaining+sin(PI*t).toFloat()*iw*.05f,startY*remaining)
            canvas.rotate(((if(count%2==0)1 else -1)*24*remaining),cx,cy)
            canvas.scale(1f,.82f+.18f*eased.toFloat(),cx,cy)
            paint.color=0x45000000;canvas.save();canvas.translate(6*remaining,9*remaining);canvas.drawPath(paths[count],paint);canvas.restore()
            canvas.drawPath(paths[count],glass);canvas.drawPath(paths[count],rim)
            canvas.restore()
        }
        canvas.restore()
        // A slow haze sits around the window, never across its readable artwork.
        val elapsed=if(motion)whole*period.toDouble()/SAMPLE_RATE else 0.0
        canvas.save();canvas.clipOutRect(left,top,right,bottom)
        for(i in fog.indices) {
            canvas.save();canvas.translate(w*(.18f+i*.21f)+(sin(elapsed/17+i)*w*.025).toFloat(),
                h*(.25f+i*.2f)+(cos(elapsed/23+i)*h*.035).toFloat())
            canvas.scale(1f,.38f);canvas.drawCircle(0f,0f,w*.4f,fog[i]);canvas.restore()
        }
        val light=timing.revealFraction(whole)
        paint.shader=sunlight;paint.alpha=(18+light*60).toInt();canvas.drawPath(beams,paint)
        paint.alpha=255;paint.shader=null;canvas.restore()
        // Subtle transmitted-light bloom grows only after the last piece is seated.
        if(light>0) {
            canvas.save();canvas.clipRect(left,top,right,bottom)
            paint.shader=sunlight;paint.alpha=(light*38).toInt();canvas.drawRect(left,top,right,bottom,paint)
            paint.shader=null;paint.alpha=255;canvas.restore()
        }
        if(BuildConfig.DEBUG)VisualWorkStats.lastGlassPeriod=period
    }
}
