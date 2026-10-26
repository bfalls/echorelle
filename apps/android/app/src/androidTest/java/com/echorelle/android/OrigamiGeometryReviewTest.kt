package com.echorelle.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OrigamiGeometryReviewTest {
    @Test fun everyStepAndFinishedMaterialAreCapturedFromTheActualRenderer() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.getExternalFilesDir(null),"origami-review").apply{mkdirs()}
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.DKGRAY;textSize=12f}
        for(seconds in listOf(5L,60L,1265L)) {
            val paper=OrigamiVisual(seconds*SAMPLE_RATE)
            val count=paper.model.stepCount
            val frame=Bitmap.createBitmap(720,432,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(frame)
            val sheet=Bitmap.createBitmap(1600,((count+9)/10)*120,Bitmap.Config.ARGB_8888)
            val contact=Canvas(sheet)
            for(i in 0 until count) {
                paper.draw(canvas,720f,432f,((i+.5f)/count*paper.timing.solveEnd).toFloat(),false)
                val x=i%10*160;val y=i/10*120
                contact.drawBitmap(frame,null,Rect(x,y,x+160,y+96),paint)
                contact.drawText("${i+1}: ${paper.model.stages[i].move.label.take(18)}",x+3f,y+111f,paint)
            }
            File(directory,"${paper.model.project}-steps.png").outputStream().use{sheet.compress(Bitmap.CompressFormat.PNG,100,it)}
            sheet.recycle()
            paper.draw(canvas,720f,432f,1f,false)
            File(directory,"${paper.model.project}-final.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            val finished=frame.copy(Bitmap.Config.ARGB_8888,false)
            paper.draw(canvas,720f,432f,paper.timing.solveEnd.toFloat(),false)
            assertTrue("All folds finish before the reveal, in the final resting view",finished.sameAs(frame))
            for(n in 0..60) {
                val progress=(paper.timing.solveEnd+(1-paper.timing.solveEnd)*n/60).toFloat()
                assertEquals(1f,paper.timing.solveFraction(progress))
                paper.draw(canvas,720f,432f,progress,false)
                if(n==24)assertFalse("The finished object must rotate",finished.sameAs(frame))
                File(directory,"${paper.model.project}-reveal-${n.toString().padStart(3,'0')}.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
            assertTrue("Rotation returns exactly to the finished resting view",finished.sameAs(frame))
            finished.recycle()
            if(paper.model.project==PaperProject.HELMET) {
                val pixels=IntArray(frame.width*frame.height)
                frame.getPixels(pixels,0,frame.width,0,0,frame.width,frame.height)
                assertTrue("Colored paper must be a solid textured surface, not faint wireframe",
                    pixels.count{Color.green(it)>Color.red(it)+8}>1000)
            }
            // Out-of-order reconstruction must match, including ink texture and triangle order.
            paper.draw(canvas,720f,432f,.37f,true)
            val frozen=frame.copy(Bitmap.Config.ARGB_8888,false)
            paper.draw(canvas,720f,432f,.98f,false)
            paper.draw(canvas,720f,432f,.1f,false)
            paper.draw(canvas,720f,432f,.37f,true)
            assertTrue("Seeking must reconstruct the same material and geometry",frozen.sameAs(frame))
            File(directory,"${paper.model.project}-dark.png").outputStream().use{frame.compress(Bitmap.CompressFormat.PNG,100,it)}
            frozen.recycle();frame.recycle()
        }
    }
}
