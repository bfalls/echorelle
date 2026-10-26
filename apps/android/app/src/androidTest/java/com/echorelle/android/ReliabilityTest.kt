package com.echorelle.android

import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class ReliabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Test fun removedVisualPreferencesMigrateWithoutLosingJobsOrImports() {
        val job=Job(UUID.randomUUID().toString(),"Migration fixture","tone-0",1500L*SAMPLE_RATE,false)
        val sound=Sound(UUID.randomUUID().toString(),"Imported fixture","Saved on this device",48000)
        val original=Library(jobs=listOf(job),imports=listOf(sound),visual=Visual.CUBE)
        val encoded=JobRepository.encode(original)
        for(old in listOf("LOOM","GARDEN","INK","STARS","LANDSCAPE")) {
            val migrated=JobRepository.decode(JSONObject(encoded).put("visual",old).toString())
            assertEquals(original.jobs,migrated.jobs);assertEquals(original.imports,migrated.imports)
            assertEquals(Visual.BIRD,migrated.visual)
        }
        assertEquals(Visual.CUBE,JobRepository.decode(encoded).visual)
    }
    private fun await(timeout: Long = 10000, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(20)
        assertTrue("Condition timed out", condition())
    }
    private fun command(action: String, id: String = "") {
        val intent = Intent(context, PlaybackService::class.java).setAction(action).putExtra("id", id)
        if (action == PlaybackService.START || action == PlaybackService.RESUME) context.startForegroundService(intent) else context.startService(intent)
    }
    @Test fun atomicBackupCorruptionRecoveryAndOrphanCleanup() = runBlocking {
        val directory = File(context.cacheDir, "storage-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        try {
            val job = Job(UUID.randomUUID().toString(), "Saved fixture", "tone-0", 48000, false)
            val expected = Library(jobs = listOf(job), visual = Visual.CUBE)
            File(directory, "library-v1.json").writeText("incomplete write")
            File(directory, "library-v1.json.bak").writeText(JobRepository.encode(expected))
            val audio = File(directory, "audio").apply { mkdirs() }
            File(audio, "orphan.pcm").writeText("unreferenced")
            File(audio, "abandoned.input").writeText("partial")
            val recovered = JobRepository(isolated).ready()
            assertEquals(listOf(job), recovered.jobs); assertEquals(Visual.CUBE, recovered.visual)
            assertTrue(audio.listFiles()!!.isEmpty())
            File(directory, "library-v1.json").writeText("{\"version\":999}")
            File(audio, "preserve.pcm").writeText("retain for recovery")
            val corrupt = JobRepository(isolated).ready()
            assertTrue(corrupt.message.contains("couldn’t be opened"))
            assertTrue(directory.listFiles()!!.any { it.name.startsWith("library-recovery-") })
            assertTrue(File(audio, "preserve.pcm").exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun oversizedAndCanceledImportsLeaveNoPartialAssets() = runBlocking {
        val directory = File(context.cacheDir, "imports-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) { override fun getFilesDir() = directory }
        val repo = JobRepository(isolated); repo.ready()
        val input = File(directory, "source.wav")
        try {
            RandomAccessFile(input, "rw").use { it.setLength(26L * 1024 * 1024) }
            try { AudioImport.run(context, Uri.fromFile(input), repo); fail("Oversized import accepted") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("25 MiB")) }
            assertTrue(repo.audioDirectory.listFiles()!!.isEmpty())
            val dataSize = 192000 * 60 * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(dataSize + 36).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(1).putInt(192000).putInt(384000).putShort(2).putShort(16)
                .put("data".toByteArray()).putInt(dataSize).array()
            input.outputStream().use { output ->
                output.write(header)
                val zeros = ByteArray(8192)
                var remaining = dataSize
                while (remaining > 0) { val size = minOf(remaining, zeros.size); output.write(zeros, 0, size); remaining -= size }
            }
            val task = launch(Dispatchers.IO) { AudioImport.run(context, Uri.fromFile(input), repo) }
            await { repo.audioDirectory.listFiles()!!.isNotEmpty() }
            task.cancelAndJoin()
            assertTrue(repo.state.value.imports.isEmpty())
            assertTrue(repo.audioDirectory.listFiles()!!.isEmpty())
        } finally { directory.deleteRecursively() }
    }
    @Test fun screenOffFourJobsKeepAdvancingAndJournalReportsIt() = runBlocking {
        val repo = (context.applicationContext as EchorelleApp).repository
        val original = repo.ready()
        val periods = listOf(48000L, 240000L, 960000L, 20160L)
        val jobs = periods.mapIndexed { i, p -> Job(UUID.randomUUID().toString(), "Background fixture $i", "tone-0", p, i == 3, 0f) }
        repo.update { it.copy(jobs = it.jobs + jobs) }
        val before = context.filesDir.listFiles()!!.map { it.name }.toSet()
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        try {
            for (job in jobs) { command(PlaybackService.START, job.id); await { PlaybackService.playback.value[job.id]?.mode == JobMode.PLAYING } }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            await { !context.getSystemService(PowerManager::class.java).isInteractive }
            val initial = jobs.map { PlaybackService.playback.value.getValue(it.id).frames }
            // Real elapsed time, deliberately unaccelerated. UI timers do not advance audio.
            val end = SystemClock.elapsedRealtime() + 65000
            while (SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(1000)
                for (job in jobs) assertEquals(JobMode.PLAYING, PlaybackService.playback.value[job.id]?.mode)
            }
            jobs.forEachIndexed { i, job ->
                assertTrue(PlaybackService.playback.value.getValue(job.id).frames > initial[i] + 60L * SAMPLE_RATE)
            }
            assertTrue(PlaybackService.playback.value.getValue(jobs[2].id).onsets >= 4)
            val journal = context.filesDir.listFiles()!!.first { it.name.startsWith("app-events-") && it.name !in before }
            val checkpoints = journal.readLines().map(::JSONObject).filter { it.getString("event") == "checkpoint" }
            assertTrue(checkpoints.size >= 5)
            assertTrue(checkpoints.any { !it.getJSONObject("details").getBoolean("interactive") })
            assertTrue(checkpoints.last().getJSONObject("details").getLong("consumed_frames") > 60L * SAMPLE_RATE)
        } finally {
            command(PlaybackService.STOP_ALL)
            repo.update { original }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        }
    }
}
