package com.echorelle.android

import android.app.AlarmManager
import android.content.Intent
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

class SleepingPlaybackTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val repo get()=(context.applicationContext as EchorelleApp).repository
    private fun await(condition: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+12000
        while(!condition() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(25)
        assertTrue("Timed out: waiting=${PlaybackWorkStats.waiting}, alarm=${PlaybackWorkStats.alarmScheduled}, output=${PlaybackWorkStats.outputOpen}",condition())
    }
    private fun command(action: String,id: String="") {
        val intent=Intent(context,PlaybackService::class.java).setAction(action).putExtra("id",id)
        if(action==PlaybackService.START || action==PlaybackService.RESUME)context.startForegroundService(intent) else context.startService(intent)
    }
    private fun shell(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use{it.readText()}
    }
    private fun openApp() {
        shell("input keyevent 224");shell("wm dismiss-keyguard")
        instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
    }

    @Test fun matchLoopKeepsOneContinuousSampleAccurateStream()=runBlocking {
        repo.ready()
        val clip=Tones.all[0].pcm
        val job=Job(UUID.randomUUID().toString(),"Match fixture","tone-0",clip.size.toLong(),true,.8f)
        val captured=FloatArray(4*SAMPLE_RATE)
        try {
            openApp();command(PlaybackService.STOP_ALL);SystemClock.sleep(300)
            repo.update{it.copy(jobs=it.jobs+job)}
            AudioOutputCapture.size=0;AudioOutputCapture.segments=0;AudioOutputCapture.target=captured
            command(PlaybackService.START,job.id)
            await{AudioOutputCapture.size>=3*SAMPLE_RATE}
            assertEquals(1,AudioOutputCapture.segments)
            assertFalse(PlaybackWorkStats.waiting);assertFalse(PlaybackWorkStats.alarmScheduled)
            assertTrue(PlaybackWorkStats.outputOpen)
            for(i in 240 until 3*SAMPLE_RATE)assertEquals(clip[i%clip.size]*.8f,captured[i],0f)
        } finally {
            AudioOutputCapture.target=null;command(PlaybackService.STOP_ALL)
            repo.update{it.copy(jobs=it.jobs.filterNot{saved->saved.id==job.id})}
        }
    }

    @Test fun screenOffGapClosesOutputReleasesLockAndDoesNoVisualWork()=runBlocking {
        repo.ready()
        val job=Job(UUID.randomUUID().toString(),"Sleeping fixture","tone-0",20L*SAMPLE_RATE,false,.8f)
        try {
            repo.update{it.copy(jobs=it.jobs+job)}
            openApp();command(PlaybackService.START,job.id)
            await{PlaybackWorkStats.waiting && PlaybackWorkStats.alarmScheduled && !PlaybackWorkStats.outputOpen && !PlaybackWorkStats.wakeHeld}
            shell("input keyevent 223")
            await{!context.getSystemService(PowerManager::class.java).isInteractive}
            // Screen-off and Activity.onStop are separate one-time lifecycle events.
            SystemClock.sleep(1500)
            val submitted=PlaybackWorkStats.submitted.get();val publishes=PlaybackWorkStats.publications.get()
            val draws=VisualWorkStats.draws.get()+VisualWorkStats.paperDraws.get()+VisualWorkStats.glassDraws.get()
            val preparations=VisualWorkStats.preparations.get()+VisualWorkStats.paperPreparations.get()+VisualWorkStats.glassPreparations.get()
            val cpu=android.os.Process.getElapsedCpuTime()
            SystemClock.sleep(2000)
            val cpuMs=android.os.Process.getElapsedCpuTime()-cpu
            assertEquals(submitted,PlaybackWorkStats.submitted.get())
            assertEquals(publishes,PlaybackWorkStats.publications.get())
            assertEquals(draws,VisualWorkStats.draws.get()+VisualWorkStats.paperDraws.get()+VisualWorkStats.glassDraws.get())
            assertEquals(preparations,VisualWorkStats.preparations.get()+VisualWorkStats.paperPreparations.get()+VisualWorkStats.glassPreparations.get())
            assertFalse(PlaybackWorkStats.outputOpen);assertFalse(PlaybackWorkStats.wakeHeld)
            command(PlaybackService.PAUSE,job.id)
            await{PlaybackService.playback.value[job.id]?.mode==JobMode.PAUSED}
            assertFalse(PlaybackWorkStats.alarmScheduled)
            val frozen=PlaybackService.playback.value.getValue(job.id).frames
            SystemClock.sleep(250);assertEquals(frozen,PlaybackService.playback.value.getValue(job.id).frames)
            command(PlaybackService.RESUME,job.id)
            await{PlaybackWorkStats.alarmScheduled && PlaybackService.playback.value[job.id]?.mode==JobMode.PLAYING}
            val directory=File(context.getExternalFilesDir(null),"power-review").apply{mkdirs()}
            File(directory,"sleeping-gap.json").writeText(JSONObject().put("wall_ms",2000).put("process_cpu_ms",cpuMs)
                .put("submitted_pcm_during_gap",0).put("background_publications",0).put("visual_work",0).toString(2))
        } finally {
            command(PlaybackService.STOP_ALL)
            repo.update{it.copy(jobs=it.jobs.filterNot{saved->saved.id==job.id})}
            shell("input keyevent 224");shell("wm dismiss-keyguard")
        }
    }
    @Test fun mixedJobsKeepIndependentPhaseWhileTheEarliestAlarmChanges()=runBlocking {
        repo.ready()
        val a=Job(UUID.randomUUID().toString(),"Gap A","tone-0",10L*SAMPLE_RATE,false,0f)
        val b=Job(UUID.randomUUID().toString(),"Gap B","tone-1",13L*SAMPLE_RATE,false,0f)
        try {
            repo.update{it.copy(jobs=it.jobs+listOf(a,b))}
            openApp();command(PlaybackService.START,a.id);command(PlaybackService.START,b.id)
            await{PlaybackWorkStats.waiting && PlaybackService.playback.value[b.id]?.mode==JobMode.PLAYING}
            command(PlaybackService.PAUSE,a.id);await{PlaybackService.playback.value[a.id]?.mode==JobMode.PAUSED}
            val frozen=PlaybackService.playback.value.getValue(a.id).frames
            val other=PlaybackService.playback.value.getValue(b.id).frames
            val publications=PlaybackWorkStats.publications.get()
            SystemClock.sleep(300)
            assertEquals(frozen,PlaybackService.playback.value.getValue(a.id).frames)
            assertTrue("B before=$other after=${PlaybackService.playback.value[b.id]} interactive=${context.getSystemService(PowerManager::class.java).isInteractive} publications=$publications -> ${PlaybackWorkStats.publications.get()} ${PlaybackService.debugVisibility()}",PlaybackService.playback.value.getValue(b.id).frames>other)
            command(PlaybackService.RESUME,a.id);await{(PlaybackService.playback.value[a.id]?.frames?:0)>frozen}
            command(PlaybackService.STOP,a.id);await{PlaybackService.playback.value[a.id]?.mode==JobMode.STOPPED}
            assertEquals(JobMode.PLAYING,PlaybackService.playback.value[b.id]?.mode)
            assertEquals(1L,PlaybackService.playback.value.getValue(b.id).onsets)
        } finally {
            command(PlaybackService.STOP_ALL)
            repo.update{it.copy(jobs=it.jobs.filterNot{saved->saved.id==a.id || saved.id==b.id})}
        }
    }
    @Test fun upcomingTimerStillPlaysWhenIdleIsRequested()=runBlocking {
        repo.ready()
        val job=Job(UUID.randomUUID().toString(),"Doze alarm fixture","tone-0",15L*SAMPLE_RATE,false,.8f)
        val clip=Tones.all[0].pcm
        try {
            openApp();command(PlaybackService.STOP_ALL);SystemClock.sleep(300)
            repo.update{it.copy(jobs=it.jobs+job)}
            AudioOutputCapture.size=0;AudioOutputCapture.segments=0;AudioOutputCapture.target=FloatArray(3*SAMPLE_RATE)
            command(PlaybackService.START,job.id)
            await{PlaybackWorkStats.alarmScheduled && !PlaybackWorkStats.wakeHeld}
            shell("input keyevent 223")
            await{!context.getSystemService(PowerManager::class.java).isInteractive}
            val idleResult=android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("cmd deviceidle force-idle deep")).bufferedReader().use{it.readText()}
            // Android may deliberately refuse deep idle because this timer is imminent.
            val enteredIdle=context.getSystemService(PowerManager::class.java).isDeviceIdleMode
            val until=SystemClock.elapsedRealtime()+22000
            while((AudioOutputCapture.segments<2 || AudioOutputCapture.size<2*clip.size) && SystemClock.elapsedRealtime()<until)SystemClock.sleep(50)
            assertEquals("Alarm must deliver the next real sound after the idle request",2,AudioOutputCapture.segments)
            assertEquals("No gap PCM was submitted",2*clip.size,AudioOutputCapture.size)
            val spacing=AudioOutputCapture.segmentNanos[1]-AudioOutputCapture.segmentNanos[0]
            assertTrue("Doze onset spacing ${spacing/1e9}s",spacing in 14_750_000_000L..15_250_000_000L)
            val directory=File(context.getExternalFilesDir(null),"power-review").apply{mkdirs()}
            File(directory,"doze-alarm.json").writeText(JSONObject().put("entered_forced_doze",enteredIdle).put("idle_request_result",idleResult).put("onset_spacing_ns",spacing)
                .put("submitted_pcm_frames",AudioOutputCapture.size).put("expected_clip_frames",clip.size*2).toString(2))
        } finally {
            AudioOutputCapture.target=null;shell("cmd deviceidle unforce")
            command(PlaybackService.STOP_ALL)
            repo.update{it.copy(jobs=it.jobs.filterNot{saved->saved.id==job.id})}
            shell("input keyevent 224");shell("wm dismiss-keyguard")
        }
    }
}
