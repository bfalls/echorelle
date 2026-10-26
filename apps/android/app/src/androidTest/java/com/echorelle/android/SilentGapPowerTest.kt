package com.echorelle.android

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Emulator CPU observations only: not a physical battery/energy measurement. */
class SilentGapPowerTest {
    @Test fun compareSilentPlaybackAndPausedCpuWithScreenOff() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val repo=(context.applicationContext as EchorelleApp).repository
        repo.ready()
        assumeTrue("Do not disturb existing playback",PlaybackService.playback.value.values.none {
            it.mode==JobMode.PLAYING || it.mode==JobMode.PAUSED || it.mode==JobMode.INTERRUPTED
        })
        val job=Job(UUID.randomUUID().toString(),"CPU measurement fixture","tone-0",1265L*SAMPLE_RATE,false,0f)
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
        fun await(test: ()->Boolean) {
            val end=SystemClock.elapsedRealtime()+10000
            while(!test() && SystemClock.elapsedRealtime()<end)SystemClock.sleep(50)
            assertTrue(test())
        }
        val results=JSONObject()
        results.put("version",context.packageManager.getPackageInfo(context.packageName,0).versionName)
        results.put("measurement","Emulator process CPU only; not phone power or audible timing")
        fun measure(name: String) {
            val cpu=Process.getElapsedCpuTime();val wall=SystemClock.elapsedRealtime()
            val frames=PlaybackService.playback.value[job.id]?.frames?:0
            SystemClock.sleep(20000)
            val elapsed=SystemClock.elapsedRealtime()-wall
            results.put(name,JSONObject().put("wall_ms",elapsed).put("cpu_ms",Process.getElapsedCpuTime()-cpu)
                .put("consumed_frames",(PlaybackService.playback.value[job.id]?.frames?:0)-frames)
                .put("screen_interactive",context.getSystemService(PowerManager::class.java).isInteractive))
        }
        repo.update { it.copy(jobs=it.jobs+job) }
        try {
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            context.startForegroundService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.START).putExtra("id",job.id))
            await{(PlaybackService.playback.value[job.id]?.frames?:0)>2L*SAMPLE_RATE}
            shell("input keyevent 223")
            await{!context.getSystemService(PowerManager::class.java).isInteractive}
            SystemClock.sleep(1000)
            measure("silent_playback")
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.PAUSE).putExtra("id",job.id))
            await{PlaybackService.playback.value[job.id]?.mode==JobMode.PAUSED}
            SystemClock.sleep(1000)
            measure("paused")
            assertEquals(0L,results.getJSONObject("paused").getLong("consumed_frames"))
            assertTrue(results.getJSONObject("silent_playback").getLong("consumed_frames")>18L*SAMPLE_RATE)
            val label=InstrumentationRegistry.getArguments().getString("powerLabel","current")
            val dir=File(context.getExternalFilesDir(null),"power-review").apply{mkdirs()}
            File(dir,"$label.json").writeText(results.toString(2))
            instrumentation.sendStatus(0,Bundle().apply{putString("powerObservation",results.toString())})
        } finally {
            shell("input keyevent 224");shell("wm dismiss-keyguard")
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP).putExtra("id",job.id))
            repo.update { it.copy(jobs=it.jobs.filterNot { saved->saved.id==job.id }) }
        }
    }
}
