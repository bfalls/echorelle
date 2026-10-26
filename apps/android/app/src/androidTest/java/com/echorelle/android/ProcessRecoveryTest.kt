package com.echorelle.android

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Two explicit instrumentation phases, separated externally by force-stop or emulator reboot.
 * Skipped in the ordinary suite so it cannot pretend an in-process reset is process recovery. */
class ProcessRecoveryTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val repo get()=(context.applicationContext as EchorelleApp).repository
    @Test fun seedRunningDefinition() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processPhase")=="seed")
        val original=repo.ready()
        val backup=File(context.filesDir,"process-test-backup.json")
        require(!backup.exists()) { "An earlier process test requires recovery first." }
        backup.writeText(JobRepository.encode(original))
        val fixture=Job(UUID.randomUUID().toString(),"Process recovery fixture","tone-0",48000,false,0f)
        File(context.filesDir,"process-test-id.txt").writeText(fixture.id)
        repo.update{it.copy(jobs=it.jobs+fixture,visual=Visual.CUBE)}
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        context.startForegroundService(Intent(context,PlaybackService::class.java).setAction(PlaybackService.START).putExtra("id",fixture.id))
        val end=SystemClock.elapsedRealtime()+10000
        while((PlaybackService.playback.value[fixture.id]?.frames?:0)<48000 && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50)
        assertTrue((PlaybackService.playback.value[fixture.id]?.frames?:0)>=48000)
    }
    @Test fun verifyStoppedDefinitionAndRestoreLibrary() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("processPhase")=="verify")
        val library=repo.ready()
        val backup=File(context.filesDir,"process-test-backup.json")
        val idFile=File(context.filesDir,"process-test-id.txt")
        val original=JobRepository.decode(backup.readText())
        try {
            assertTrue(library.jobs.any{it.id==idFile.readText()})
            assertEquals(Visual.CUBE,library.visual)
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync();SystemClock.sleep(1000)
            assertTrue("No automatic audio restart",PlaybackService.playback.value.values.none{it.mode==JobMode.PLAYING})
            assertTrue(library.ready)
        } finally {
            repo.update{original.copy(ready=true,message=it.message)}
            backup.delete();idFile.delete()
        }
    }
}
