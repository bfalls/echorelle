package com.echorelle.android

import kotlin.math.roundToInt

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Real Canvas captures, not hours of accelerated audio or a separate animation scheduler. */
class VisualRenderingTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun cubeMotionStudiesUseActualLayerGeometryAndFreezeExactly() {
        val directory=File(ui.activity.getExternalFilesDir(null),"cube-review").apply{mkdirs()}
        val sheet=Bitmap.createBitmap(1500,880,Bitmap.Config.ARGB_8888)
        val contact=android.graphics.Canvas(sheet)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color=android.graphics.Color.DKGRAY;textSize=17f
        }
        for((row,seconds) in listOf(5L,60L,1500L,7200L).withIndex()) {
            val cube=CubeVisual(seconds*SAMPLE_RATE)
            val frame=Bitmap.createBitmap(720,432,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(frame)
            cube.draw(canvas,720f,432f,cube.timing.solveEnd.toFloat(),false)
            val solvedFront=frame.copy(Bitmap.Config.ARGB_8888,false)
            cube.draw(canvas,720f,432f,((cube.timing.solveEnd+1)/2).toFloat(),false)
            assertFalse("Solved reveal must show other faces",solvedFront.sameAs(frame))
            cube.draw(canvas,720f,432f,1f,false)
            assertTrue("Reveal returns exactly to starting orientation",solvedFront.sameAs(frame))
            solvedFront.recycle()
            for((column,fraction) in listOf(0f,.25f,.5f,.9f,1f).withIndex()) {
                cube.draw(canvas,720f,432f,fraction,false)
                contact.drawBitmap(frame,null,android.graphics.Rect(column*300,row*220,column*300+300,row*220+180),paint)
                contact.drawText("${seconds}s / ${(fraction*100).toInt()}%",column*300+12f,row*220+204f,paint)
            }
            if(seconds==5L || seconds==60L) for(n in 0..150) {
                cube.draw(canvas,720f,432f,n/150f,false)
                File(directory,"cube-$seconds-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            if(seconds>=1500L) for(n in 0..150) {
                val fraction=(cube.timing.solveEnd+(1-cube.timing.solveEnd)*n/150).toFloat()
                cube.draw(canvas,720f,432f,fraction,false)
                File(directory,"cube-reveal-$seconds-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            cube.draw(canvas,720f,432f,.371f,true)
            val frozen=frame.copy(Bitmap.Config.ARGB_8888,false)
            cube.draw(canvas,720f,432f,.371f,true)
            assertTrue("A cube phase must redraw deterministically",frozen.sameAs(frame))
            File(directory,"cube-$seconds-dark.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            frozen.recycle();frame.recycle()
        }
        File(directory,"cube-storyboard.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
        sheet.recycle()
        val cube=CubeVisual(1500L*SAMPLE_RATE)
        val frame=Bitmap.createBitmap(1280,192,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(frame)
        for(n in 0..120) {
            for((column,start) in listOf(.25f,.5f,.75f,.95f).withIndex()) {
                canvas.save();canvas.translate(column*320f,0f)
                cube.draw(canvas,320f,192f,start+n/10f/1500f,false)
                canvas.restore()
            }
            File(directory,"cube-realtime-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        frame.recycle()
    }
    @Test fun allStylesRevealProgressAtSecondsMinutesAndHoursAndFreezeWhenPaused() {
        val choice=mutableStateOf(Visual.BIRD)
        val period=mutableStateOf(5L*SAMPLE_RATE)
        val snapshot=mutableStateOf(JobPlayback())
        ui.runOnUiThread { ui.activity.setContent { MaterialTheme { Column { JobVisual(choice.value,snapshot.value,period.value) } } } }
        val directory=File(ui.activity.getExternalFilesDir(null),"visual-review").apply{mkdirs()}
        fun capture(): Bitmap {
            if(choice.value==Visual.GLASS) ui.waitUntil(10000) {VisualWorkStats.lastGlassPeriod==period.value}
            if(choice.value==Visual.CUBE) ui.waitUntil(10000) {
                VisualWorkStats.lastDrawnKey==VisualModels.Key(period.value,0)
            }
            if(choice.value==Visual.BIRD) ui.waitUntil(10000) {
                VisualWorkStats.lastPaperProject==PaperProject.forPeriod(period.value)
            }
            return ui.onNodeWithTag("interval-visual").captureToImage().asAndroidBitmap()
        }
        for(visual in Visual.entries) for(seconds in listOf(5L,60L,1200L,1265L,1500L,7200L)) {
            var previous:Bitmap?=null
            for(percent in listOf(0,25,50,75,99)) {
                ui.runOnIdle {
                    VisualWorkStats.lastPaperProject=null;VisualWorkStats.lastDrawnKey=null;VisualWorkStats.lastGlassPeriod=null
                    choice.value=visual;period.value=seconds*SAMPLE_RATE
                    snapshot.value=JobPlayback(JobMode.PAUSED,phase=period.value*percent/100)
                }
                val bitmap=capture()
                previous?.let { assertFalse("$visual ${seconds}s: $percent% must reveal more progress",it.sameAs(bitmap)) }
                File(directory,"${visual.name}-$seconds-$percent.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                previous=bitmap
            }
        }
        // Hour-long intervals must still redraw fine detail smoothly between 100ms observations.
        for(visual in Visual.entries) {
            ui.mainClock.autoAdvance=false
            ui.runOnUiThread {
                choice.value=visual;period.value=7200L*SAMPLE_RATE
                val fraction=if(visual==Visual.CUBE) CubeSequence(period.value).let {
                    val move=it.turns.size/2
                    (it.boundaries[move]+it.boundaries[move+1])/2
                }.toDouble()*CubeTiming(period.value).solveEnd else if(visual==Visual.GLASS) {
                    val timing=CubeTiming(period.value)
                    val count=((7200-timing.revealSeconds)/2.5).roundToInt()
                    (count/2+.5)/count*timing.solveEnd
                } else .4
                snapshot.value=JobPlayback(JobMode.PLAYING,phase=(period.value*fraction).toLong(),observedAtNanos=SystemClock.elapsedRealtimeNanos())
            }
            ui.mainClock.advanceTimeByFrame()
            val before=capture()
            SystemClock.sleep(80)
            ui.mainClock.advanceTimeBy(32)
            val after=capture()
            assertFalse("$visual must interpolate without another audio snapshot",before.sameAs(after))
            ui.runOnUiThread { snapshot.value=snapshot.value.copy(mode=JobMode.PAUSED) }
            ui.mainClock.advanceTimeByFrame()
            val frozen=capture()
            SystemClock.sleep(80);ui.mainClock.advanceTimeBy(32)
            assertTrue("$visual must freeze while paused",frozen.sameAs(capture()))
            ui.mainClock.autoAdvance=true
        }
    }

    @Test fun intervalsBelowFiveSecondsStayStaticAcrossPhasesAndRedraws() {
        val choice=mutableStateOf(Visual.BIRD)
        val period=mutableStateOf(4L*SAMPLE_RATE)
        val snapshot=mutableStateOf(JobPlayback(JobMode.PLAYING))
        ui.runOnUiThread { ui.activity.setContent { MaterialTheme { JobVisual(choice.value,snapshot.value,period.value) } } }
        fun capture(): Bitmap {
            if(choice.value==Visual.CUBE) ui.waitUntil(10000) {
                VisualWorkStats.lastDrawnKey==VisualModels.Key(period.value,0)
            }
            if(choice.value==Visual.BIRD) ui.waitUntil(10000) {
                VisualWorkStats.lastPaperProject==PaperProject.forPeriod(period.value)
            }
            return ui.onNodeWithTag("interval-visual").captureToImage().asAndroidBitmap()
        }
        for(visual in Visual.entries) for(frames in listOf(20160L, SAMPLE_RATE.toLong(),5L*SAMPLE_RATE-1)) {
            ui.runOnIdle { choice.value=visual;period.value=frames;snapshot.value=JobPlayback(JobMode.PLAYING,phase=0) }
            val before=capture()
            ui.runOnIdle { snapshot.value=snapshot.value.copy(phase=frames*9/10,observedAtNanos=SystemClock.elapsedRealtimeNanos()) }
            val after=capture()
            assertTrue("$visual must be static below five seconds",before.sameAs(after))
        }
    }

    /** Uses the actual product renderer for review artifacts, without modifying any saved job. */
    @Test fun sequentialOrigamiMotionStudiesAndMaterialAreDeterministic() {
        val directory=File(ui.activity.getExternalFilesDir(null),"origami-review").apply{mkdirs()}
        val sheet=Bitmap.createBitmap(1500,660,Bitmap.Config.ARGB_8888)
        val contact=android.graphics.Canvas(sheet)
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        for((row,seconds) in listOf(5L,60L,1500L).withIndex()) {
            val bird=OrigamiVisual(seconds*SAMPLE_RATE)
            for((column,fraction) in listOf(0f,.25f,.5f,.9f,1f).withIndex()) {
                val frame=Bitmap.createBitmap(600,360,Bitmap.Config.ARGB_8888)
                bird.draw(android.graphics.Canvas(frame),600f,360f,fraction,false)
                contact.drawBitmap(frame,null,android.graphics.Rect(column*300,row*220,column*300+300,row*220+180),paint)
                paint.color=android.graphics.Color.DKGRAY;paint.textSize=17f
                contact.drawText("${seconds}s / ${(fraction*100).toInt()}%",column*300+12f,row*220+204f,paint)
                frame.recycle()
            }
        }
        File(directory,"storyboard.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
        sheet.recycle()
        val proof=Bitmap.createBitmap(1800,220,Bitmap.Config.ARGB_8888)
        val proofCanvas=android.graphics.Canvas(proof)
        val longPaper=OrigamiVisual(1265L*SAMPLE_RATE)
        for((column,seconds) in listOf(0f,3f,316.25f,632.5f,1138.5f,1265f).withIndex()) {
            proofCanvas.save();proofCanvas.translate(column*300f,0f)
            longPaper.draw(proofCanvas,300f,180f,seconds/1265f,false)
            proofCanvas.restore()
            proofCanvas.drawText("${seconds.toInt()} / 1265 seconds",column*300+12f,204f,paint)
        }
        File(directory,"interval-1265-proof.png").outputStream().use{proof.compress(Bitmap.CompressFormat.PNG,100,it)}
        proof.recycle()
        for(seconds in listOf(5L,60L,1265L,7200L)) {
            val bird=OrigamiVisual(seconds*SAMPLE_RATE)
            val frame=Bitmap.createBitmap(720,432,Bitmap.Config.ARGB_8888)
            val canvas=android.graphics.Canvas(frame)
            // Entire five-second construction; long construction accelerated for form review.
            for(n in 0..150) {
                bird.draw(canvas,720f,432f,n/150f,false)
                File(directory,"origami-$seconds-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            bird.draw(canvas,720f,432f,.5f,true)
            File(directory,"origami-$seconds-dark.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            val a=frame.copy(Bitmap.Config.ARGB_8888,false)
            bird.draw(canvas,720f,432f,.5f,true)
            assertTrue("Material must not flicker or change grain on redraw",a.sameAs(frame))
            a.recycle();frame.recycle()
        }
        val bird=OrigamiVisual(1265L*SAMPLE_RATE)
        val strip=Bitmap.createBitmap(960,192,Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(strip)
        for(n in 0..120) {
            for((column,start) in listOf(.25f,.5f,.9f).withIndex()) {
                canvas.save();canvas.translate(column*320f,0f)
                bird.draw(canvas,320f,192f,start+n/10f/1265f,false)
                canvas.restore()
            }
            File(directory,"origami-1265-realtime-${n.toString().padStart(3,'0')}.png").outputStream().use{strip.compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        strip.recycle()
    }
}
