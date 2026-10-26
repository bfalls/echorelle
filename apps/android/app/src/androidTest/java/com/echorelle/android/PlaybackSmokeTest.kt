package com.echorelle.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val repo get()=(context.applicationContext as EchorelleApp).repository
    private fun await(test:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+10000
        while(!test() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50)
        assertTrue("Condition timed out",test())
    }
    @Test fun fifteenHundredSecondJobStillBeginsAtPointTwoPercentAfterThreeSeconds() = runBlocking {
        val original=repo.ready()
        val job=Job(UUID.randomUUID().toString(),"Long interval fixture","tone-0",1500L*SAMPLE_RATE,false,0f)
        repo.update{it.copy(jobs=it.jobs+job,visual=Visual.BIRD)}
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        try {
            context.startForegroundService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.START).putExtra("id",job.id))
            await{(PlaybackService.playback.value[job.id]?.frames?:0)>=3L*SAMPLE_RATE}
            val snapshot=PlaybackService.playback.value.getValue(job.id)
            val phase=VisualProgress.observed(snapshot,job.period,SystemClock.elapsedRealtimeNanos())
            assertTrue(phase.whole in .002.. .007)
            assertEquals(1L,snapshot.onsets)
            assertEquals(JobMode.PLAYING,snapshot.mode)
        } finally {
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP_ALL))
            repo.update{original}
        }
    }
    @Test fun mediaSessionPauseResumeAndStopControlTheRuntime() = runBlocking {
        val original=repo.ready()
        val job=Job(UUID.randomUUID().toString(),"Media controls fixture","tone-0",48000,false,0f)
        repo.update{it.copy(jobs=it.jobs+job)}
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        context.startForegroundService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.START).putExtra("id",job.id))
        try {
            await{PlaybackService.playback.value[job.id]?.mode==JobMode.PLAYING}
            instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
            val manager=context.getSystemService(android.media.session.MediaSessionManager::class.java)
            val controller=manager.getActiveSessions(null).first{it.packageName==context.packageName}
            // Shell identity is needed only to discover the controller. Keep app focus requests
            // under the real app identity, just as they run from the system notification.
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            controller.transportControls.pause();await{PlaybackService.playback.value[job.id]?.mode==JobMode.PAUSED}
            val phase=PlaybackService.playback.value.getValue(job.id).frames
            SystemClock.sleep(250);assertEquals(phase,PlaybackService.playback.value.getValue(job.id).frames)
            controller.transportControls.play();await{PlaybackService.playback.value[job.id]?.mode==JobMode.PLAYING}
            controller.transportControls.stop();await{PlaybackService.playback.value[job.id]?.mode==JobMode.STOPPED}
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            context.startService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP_ALL))
            repo.update{original}
        }
    }
    @Test fun independentJobsPreservePhaseAndVisualsNeverRestartAudio() = runBlocking {
        val original=repo.ready()
        val a=Job(UUID.randomUUID().toString(),"Test A","tone-0",48000,false)
        val b=Job(UUID.randomUUID().toString(),"Test B","tone-1",72000,false)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        repo.update{it.copy(jobs=it.jobs+listOf(a,b))}
        fun command(action:String,id:String="") {
            val intent=Intent(context,PlaybackService::class.java).setAction(action).putExtra("id",id)
            if(action==PlaybackService.START || action==PlaybackService.RESUME) context.startForegroundService(intent) else context.startService(intent)
        }
        try {
            command(PlaybackService.START,a.id);command(PlaybackService.START,b.id)
            await{(PlaybackService.playback.value[a.id]?.frames?:0)>48000 && (PlaybackService.playback.value[b.id]?.frames?:0)>48000}
            command(PlaybackService.PAUSE,a.id)
            await{PlaybackService.playback.value[a.id]?.mode==JobMode.PAUSED}
            val frozen=PlaybackService.playback.value.getValue(a.id).frames
            val advancing=PlaybackService.playback.value.getValue(b.id).frames
            SystemClock.sleep(500)
            assertEquals(frozen,PlaybackService.playback.value.getValue(a.id).frames)
            assertTrue(PlaybackService.playback.value.getValue(b.id).frames>advancing)
            for(visual in Visual.entries) repo.update{it.copy(visual=visual)}
            assertEquals(frozen,PlaybackService.playback.value.getValue(a.id).frames)
            command(PlaybackService.RESUME,a.id)
            await{(PlaybackService.playback.value[a.id]?.frames?:0)>frozen+10000}
            command(PlaybackService.STOP,a.id)
            await{PlaybackService.playback.value[a.id]?.mode==JobMode.STOPPED}
            assertEquals(JobMode.PLAYING,PlaybackService.playback.value[b.id]?.mode)
            command(PlaybackService.PAUSE,b.id);await{PlaybackService.playback.value[b.id]?.mode==JobMode.PAUSED}
            SystemClock.sleep(200)
            command(PlaybackService.RESUME,b.id);await{PlaybackService.playback.value[b.id]?.mode==JobMode.PLAYING}
            // Persisted document never contains instructions to resume a runtime.
            val restored=JobRepository.decode(JobRepository.encode(repo.state.value))
            assertTrue(restored.jobs.any{it.id==a.id});assertFalse(JobRepository.encode(restored).contains("PLAYING"))
        } finally {
            command(PlaybackService.STOP_ALL)
            repo.update{original}
        }
    }
    @Test fun importsUseDecodedFramesCopyOfflineAndCleanFailures() = runBlocking {
        val original=repo.ready()
        val wav=File(context.cacheDir,"fixture.wav")
        val frames=441
        val bytes=ByteBuffer.allocate(44+frames*4).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36+frames*4).put("WAVEfmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(2).putInt(44100).putInt(44100*4).putShort(4).putShort(16)
            .put("data".toByteArray()).putInt(frames*4)
        repeat(frames){bytes.putShort(if(it in 10..430) 1000 else 0);bytes.putShort(if(it in 10..430) 1000 else 0)}
        wav.writeBytes(bytes.array())
        var imported:Sound?=null
        try {
            imported=AudioImport.run(context,Uri.fromFile(wav),repo)
            assertEquals(441,imported.sourceFrames);assertEquals(44100,imported.sourceRate);assertEquals(480,imported.frames)
            wav.delete()
            val pcm=repo.pcm(imported)
            assertEquals(480,pcm.size);assertEquals(0f,pcm.first(),0f);assertEquals(0f,pcm.last(),0f)
            assertTrue(pcm.any{it>0})
            wav.writeText("This is not audio")
            try{AudioImport.run(context,Uri.fromFile(wav),repo);fail("Corrupt import accepted")}catch(_:IllegalArgumentException){}catch(_:java.io.IOException){}
            assertFalse(repo.audioDirectory.listFiles()!!.any{it.extension in listOf("input","raw")})
            val reloaded=JobRepository(context).ready()
            assertEquals(imported.frames,reloaded.imports.first{it.id==imported.id}.frames)
        } finally {
            repo.update{original};imported?.let{File(repo.audioDirectory,"${it.id}.pcm").delete()};wav.delete()
        }
    }
    @Test fun compressedImportsRespectEncoderDelayAndPadding() = runBlocking {
        val original = repo.ready()
        val imported = mutableListOf<Sound>()
        try {
            for (name in listOf("gapless.mp3", "gapless.m4a")) {
                val file = File(context.cacheDir, name)
                instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
                val sound = AudioImport.run(context, Uri.fromFile(file), repo)
                imported.add(sound); file.delete()
                assertEquals("$name retained content frames", 57600, sound.frames)
                assertEquals(48000, sound.sourceRate)
            }
        } finally {
            repo.update { original }
            imported.forEach { File(repo.audioDirectory, "${it.id}.pcm").delete() }
        }
    }
    @Test fun fourActiveCapDuplicateStartAndFocusInterruption() = runBlocking {
        val original = repo.ready()
        val jobs = (0..4).map { Job(UUID.randomUUID().toString(), "Cap fixture $it", "tone-0", 48000L + it*4800, false, 0f) }
        repo.update { it.copy(jobs = it.jobs + jobs) }
        val audio = context.getSystemService(android.media.AudioManager::class.java)
        val focus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }.build()
        fun command(action: String, id: String = "") {
            val intent=Intent(context,PlaybackService::class.java).setAction(action).putExtra("id",id)
            if(action==PlaybackService.START || action==PlaybackService.RESUME) context.startForegroundService(intent) else context.startService(intent)
        }
        try {
            for(job in jobs.take(4)) { command(PlaybackService.START,job.id); await{PlaybackService.playback.value[job.id]?.mode==JobMode.PLAYING} }
            command(PlaybackService.START,jobs[4].id)
            await{PlaybackService.playback.value[jobs[4].id]?.mode==JobMode.ERROR}
            assertTrue(PlaybackService.playback.value.getValue(jobs[4].id).reason.contains("Four"))
            SystemClock.sleep(1100)
            val before=PlaybackService.playback.value.getValue(jobs[1].id).frames
            command(PlaybackService.START,jobs[1].id)
            SystemClock.sleep(300)
            assertTrue(PlaybackService.playback.value.getValue(jobs[1].id).frames>=before)
            command(PlaybackService.PAUSE,jobs[0].id);await{PlaybackService.playback.value[jobs[0].id]?.mode==JobMode.PAUSED}
            assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,audio.requestAudioFocus(focus))
            await{PlaybackService.playback.value[jobs[1].id]?.mode==JobMode.INTERRUPTED}
            assertEquals(JobMode.PAUSED,PlaybackService.playback.value[jobs[0].id]?.mode)
            audio.abandonAudioFocusRequest(focus);SystemClock.sleep(300)
            assertEquals(JobMode.INTERRUPTED,PlaybackService.playback.value[jobs[1].id]?.mode)
            command(PlaybackService.RESUME,jobs[1].id);await{PlaybackService.playback.value[jobs[1].id]?.mode==JobMode.PLAYING}
        } finally {
            audio.abandonAudioFocusRequest(focus);command(PlaybackService.STOP_ALL);repo.update{original}
        }
    }
}
