package com.echorelle.android

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RepeatOutputTest {
    @Test fun exactSubmittedSamplesContainEveryRepeat()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val repo=(context.applicationContext as EchorelleApp).repository
        repo.ready()
        val job=Job(UUID.randomUUID().toString(),"Submitted repeat fixture","tone-0",10L*SAMPLE_RATE,false,.8f)
        val capture=FloatArray(5*SAMPLE_RATE)
        val directory=File(context.getExternalFilesDir(null),"repeat-review").apply{mkdirs()}
        try {
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP_ALL))
            SystemClock.sleep(500)
            repo.update{it.copy(jobs=it.jobs+job)}
            AudioOutputCapture.size=0;AudioOutputCapture.segments=0;AudioOutputCapture.target=capture
            context.startForegroundService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.START).putExtra("id",job.id))
            val timeline=StringBuilder()
            val start=SystemClock.elapsedRealtime()
            val clip=Tones.all[0].pcm
            while((AudioOutputCapture.segments<4 || AudioOutputCapture.size<clip.size*4) && SystemClock.elapsedRealtime()-start<45000) {
                SystemClock.sleep(100)
                val state=PlaybackService.playback.value[job.id]
                timeline.appendLine("${SystemClock.elapsedRealtime()-start} submitted=${AudioOutputCapture.size} state=$state")
            }
            File(directory,"submitted-timeline.txt").writeText(timeline.toString())
            val received=AudioOutputCapture.size
            val energy=List((received+SAMPLE_RATE-1)/SAMPLE_RATE) { sec ->
                (sec*SAMPLE_RATE until minOf((sec+1)*SAMPLE_RATE,received)).sumOf{capture[it].toDouble()*capture[it]}
            }
            File(directory,"submitted-energy.txt").writeText(energy.mapIndexed{i,e->"$i $e"}.joinToString("\n"))
            assertEquals("Four distinct real-audio output sessions",4,AudioOutputCapture.segments)
            assertEquals("No PCM for the silent intervals",clip.size*4,received)
            val times=AudioOutputCapture.segmentNanos.take(4)
            File(directory,"segment-times.txt").writeText(times.joinToString("\n"))
            for((a,b) in times.zipWithNext())assertTrue("Repeat spacing ${(b-a)/1e9}s",b-a in 9_750_000_000L..10_250_000_000L)
            for(segment in 1..3)for(i in clip.indices)
                assertEquals("Repeated PCM must preserve the whole sound",clip[i]*.8f,capture[AudioOutputCapture.segmentOffsets[segment]+i],0f)
        } finally {
            AudioOutputCapture.target=null
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP).putExtra("id",job.id))
            repo.update{it.copy(jobs=it.jobs.filterNot{saved->saved.id==job.id})}
        }
    }
}
