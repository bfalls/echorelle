package com.echorelle.android

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    val repository = (application as EchorelleApp).repository
    val library = repository.state
    val playback = PlaybackService.playback
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    private val mutableImporting = MutableStateFlow(false)
    val importing = mutableImporting.asStateFlow()
    private var importTask: kotlinx.coroutines.Job? = null
    private val deleted = mutableMapOf<String, Job>()
    fun report(text: String) { mutableMessage.value = text }
    fun changeVisual(visual: Visual) = persist({ it.copy(visual = visual) })
    private fun persist(change: (Library) -> Library, after: () -> Unit = {}) {
        viewModelScope.launch {
            try { repository.update(change); after() }
            catch (e: Exception) { if (e is CancellationException) throw e; report("Could not save: ${e.message}") }
        }
    }
    fun save(job: Job, after: () -> Unit) {
        if ((playback.value[job.id]?.mode ?: JobMode.STOPPED) !in listOf(JobMode.STOPPED, JobMode.ERROR)) { report("Stop this job before editing it."); return }
        val sound = library.value.sounds.firstOrNull { it.id == job.soundId }
        val reason = Eligibility.reason(sound, job.period, job.match)
        if (reason != null) { report(reason); return }
        persist({ l -> require(l.jobs.size < 100 || l.jobs.any { it.id == job.id }) { "Limit: 100 saved jobs." }
            l.copy(jobs = l.jobs.filterNot { it.id == job.id } + job) }, after)
    }
    fun delete(job: Job, after: () -> Unit) {
        command(PlaybackService.STOP, job.id)
        persist({ it.copy(jobs = it.jobs.filterNot { j -> j.id == job.id }) }) { deleted[job.id] = job; after() }
    }
    fun undo(job: Job) { if (deleted.remove(job.id) == null) return; persist({ it.copy(jobs = it.jobs + job) }) }
    fun forgetUndo(job: Job) { deleted.remove(job.id) }
    fun removeSound(sound: Sound) {
        if (library.value.jobs.any { it.soundId == sound.id } || deleted.values.any { it.soundId == sound.id }) {
            report("This sound is used by a saved or recently deleted job."); return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.update { it.copy(imports = it.imports.filterNot { s -> s.id == sound.id }) }
                java.io.File(repository.audioDirectory, "${sound.id}.pcm").delete()
            } catch (e: Exception) { if (e is CancellationException) throw e; report("Could not remove sound: ${e.message}") }
        }
    }
    fun command(action: String, id: String = "") {
        val context = getApplication<Application>()
        val intent = Intent(context, PlaybackService::class.java).setAction(action).putExtra("id", id)
        if (action == PlaybackService.START || action == PlaybackService.RESUME) context.startForegroundService(intent) else context.startService(intent)
    }
    fun importAudio(uri: Uri) {
        if (mutableImporting.value) return
        mutableImporting.value = true
        importTask = viewModelScope.launch(Dispatchers.IO) {
            try {
                val sound = AudioImport.run(getApplication(), uri, repository)
                report("Imported ${sound.name}. Select it below; playback only starts with Preview or Start.")
            } catch (_: CancellationException) { report("Import canceled.") }
            catch (e: Exception) {
                val reason = (e.message ?: "Unsupported or damaged audio.").take(200)
                report("Import failed: $reason")
                // Failed choices stay visible and ineligible; no partially decoded asset is kept.
                runCatching { repository.update { if(it.imports.size >= 100) it else it.copy(imports = it.imports + Sound(UUID.randomUUID().toString(), "Unavailable import", "Import failed", 0, error = reason)) } }
            } finally { mutableImporting.value = false }
        }
    }
    fun cancelImport() { importTask?.cancel() }
}
