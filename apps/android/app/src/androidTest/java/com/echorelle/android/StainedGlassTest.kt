package com.echorelle.android

import android.graphics.*
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class StainedGlassTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun artCadenceMaterialSeekingAndAtmosphereAreCaptured() {
        val directory=File(ui.activity.getExternalFilesDir(null),"glass-review").apply{mkdirs()}
        val sources=GlassArt.entries.associateWith{GlassSource.load(ui.activity.assets,it){}}
        val sheet=Bitmap.createBitmap(1800,1080,Bitmap.Config.ARGB_8888);val contact=Canvas(sheet)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textSize=18f}
        for((row,seconds) in listOf(60L,600L,7200L).withIndex()) {
            val glass=StainedGlassVisual(seconds*SAMPLE_RATE,sources.getValue(GlassArt.forPeriod(seconds*SAMPLE_RATE)))
            val frame=Bitmap.createBitmap(900,540,Bitmap.Config.ARGB_8888);val canvas=Canvas(frame)
            for((col,fraction) in listOf(0f,.25f,.5f,.9f,1f).withIndex()) {
                glass.draw(canvas,900f,540f,fraction,false)
                File(directory,"$seconds-${(fraction*100).toInt()}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
                contact.drawBitmap(frame,null,Rect(col*360,row*360,col*360+360,row*360+216),paint)
                contact.drawText("${GlassArt.forPeriod(seconds*SAMPLE_RATE).title}: ${(fraction*100).toInt()}%",col*360+8f,row*360+245f,paint)
            }
            glass.draw(canvas,900f,540f,.37f,true)
            val frozen=frame.copy(Bitmap.Config.ARGB_8888,false)
            glass.draw(canvas,900f,540f,.91f,false);glass.draw(canvas,900f,540f,.1f,false)
            glass.draw(canvas,900f,540f,.37f,true)
            assertTrue("Seek must restore exactly the same pieces, material, flight and fog",frozen.sameAs(frame))
            glass.draw(canvas,900f,540f,.37f,false,false)
            val still=frame.copy(Bitmap.Config.ARGB_8888,false)
            glass.draw(canvas,900f,540f,.37f,false,false)
            assertTrue("Reduced motion must be deterministic",still.sameAs(frame))
            val boundary=glass.timing.solveEnd.toFloat()
            glass.draw(canvas,900f,540f,boundary,false)
            assertEquals(glass.sequence.count,glass.sequence.completed(boundary))
            val completed=frame.copy(Bitmap.Config.ARGB_8888,false)
            glass.draw(canvas,900f,540f,1f,false)
            assertFalse("Completion should illuminate the window",completed.sameAs(frame))
            if(row==1)for(n in 0..100) {
                // Real-time ten-second excerpt around the middle of a ten-minute interval.
                glass.draw(canvas,900f,540f,.5f+n/10f/seconds,false)
                File(directory,"flight-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            still.recycle();frozen.recycle();completed.recycle();frame.recycle()
        }
        File(directory,"storyboard.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
        sheet.recycle()
        val start=SystemClock.elapsedRealtimeNanos()
        val longest=StainedGlassVisual(MAX_PERIOD,sources.getValue(GlassArt.GARDEN))
        val build=(SystemClock.elapsedRealtimeNanos()-start)/1e6
        val frame=Bitmap.createBitmap(900,540,Bitmap.Config.ARGB_8888)
        val seekStart=SystemClock.elapsedRealtimeNanos()
        longest.draw(Canvas(frame),900f,540f,.9f,false)
        val seek=(SystemClock.elapsedRealtimeNanos()-seekStart)/1e6
        File(directory,"latency.txt").writeText("24h preparation: ${build}ms; initial 90% bitmap seek: ${seek}ms; pieces: ${longest.sequence.count}")
        frame.recycle()
    }
    @Test fun hiddenGlassDoesNoWorkAndResumesDirectlyAtCurrentPhase() {
        val period=1265L*SAMPLE_RATE
        val snapshot=mutableStateOf(JobPlayback(JobMode.PAUSED,phase=period/4))
        val visible=mutableStateOf(true)
        ui.runOnUiThread {
            VisualWorkStats.lastGlassPeriod=null
            ui.activity.setContent {MaterialTheme{JobVisual(Visual.GLASS,snapshot.value,period,visible=visible.value)}}
        }
        ui.waitUntil(10000){VisualWorkStats.lastGlassPeriod==period}
        fun capture()=ui.onNodeWithTag("interval-visual").captureToImage().asAndroidBitmap()
        val before=capture()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun shell(command: String){instrumentation.uiAutomation.executeShellCommand(command).close()}
        try {
            shell("input keyevent 223");SystemClock.sleep(750)
            val draws=VisualWorkStats.glassDraws.get();val builds=VisualWorkStats.glassPreparations.get()
            ui.runOnUiThread {snapshot.value=JobPlayback(JobMode.PAUSED,phase=period*9/10,frames=500*period)}
            SystemClock.sleep(750)
            assertEquals(draws,VisualWorkStats.glassDraws.get());assertEquals(builds,VisualWorkStats.glassPreparations.get())
            shell("input keyevent 224");shell("wm dismiss-keyguard")
            ui.waitUntil(10000){VisualWorkStats.glassDraws.get()>draws}
            val after=capture();assertFalse(before.sameAs(after))
            assertEquals(builds,VisualWorkStats.glassPreparations.get())
            SystemClock.sleep(150);assertTrue(after.sameAs(capture()))
            ui.runOnIdle {visible.value=false}
            val hiddenDraws=VisualWorkStats.glassDraws.get()
            ui.runOnIdle {snapshot.value=JobPlayback(JobMode.PLAYING,phase=period/2);}
            SystemClock.sleep(200)
            assertEquals(hiddenDraws,VisualWorkStats.glassDraws.get())
        } finally {shell("input keyevent 224");shell("wm dismiss-keyguard")}
    }
}
