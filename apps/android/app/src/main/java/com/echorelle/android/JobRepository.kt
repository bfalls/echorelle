package com.echorelle.android

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EchorelleApp : Application() {
    val repository by lazy { JobRepository(this) }
}

/** Small versioned local document. AtomicFile keeps the last complete version on write failure. */
class JobRepository(context: Context) {
    private val directory = context.filesDir
    val audioDirectory = File(directory, "audio").apply { mkdirs() }
    private val file = AtomicFile(File(directory, "library-v1.json"))
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(Library())
    val state = mutable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { load() } }
    suspend fun ready() = state.first { it.ready }
    private suspend fun load() = mutex.withLock {
        var warning = ""
        var readable = true
        val library = try {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) Library()
            else file.openRead().use {
                require(it.channel.size() <= 1024 * 1024) { "Library document is too large" }
                decode(it.readBytes().toString(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            readable = false
            // Retain the damaged document for recovery rather than silently overwriting it.
            runCatching { file.openRead().use { input -> File(directory, "library-recovery-${System.currentTimeMillis()}.json").outputStream().use(input::copyTo) } }
            warning = "Your saved sounds couldn’t be opened. Your files have been kept."
            Library()
        }
        // Only files unreferenced by a completed document are removed after a process interruption.
        val ids = library.imports.filter { it.error == null }.map { "${it.id}.pcm" }.toSet()
        if (readable) audioDirectory.listFiles()?.filter { it.name !in ids }?.forEach { it.delete() }
        mutable.value = library.copy(ready = true, message = warning)
    }
    suspend fun update(change: (Library) -> Library) {
        ready()
        mutex.withLock {
            val next = change(mutable.value)
            withContext(Dispatchers.IO + NonCancellable) {
                val out = file.startWrite()
                try { out.write(encode(next).toByteArray()); file.finishWrite(out) }
                catch (e: Exception) { file.failWrite(out); throw e }
                mutable.value = next
            }
        }
    }
    fun pcm(sound: Sound): FloatArray {
        require(sound.error == null && sound.frames in 1..MAX_DECODED_FRAMES)
        if (sound.id.startsWith("tone-")) return Tones.all[sound.id.removePrefix("tone-").toInt()].pcm
        val path = File(audioDirectory, "${sound.id}.pcm")
        require(path.length() == sound.frames * 4L) { "Local audio is missing or damaged. Import it again." }
        return path.inputStream().buffered().use { input ->
            java.io.DataInputStream(input).use { data -> FloatArray(sound.frames) { data.readFloat().also { require(it.isFinite() && it in -1f..1f) { "Invalid PCM" } } } }
        }
    }
    companion object {
        fun encode(l: Library): String = JSONObject().put("version", 1).put("visual", l.visual.name)
            .put("jobs", JSONArray(l.jobs.map { j -> JSONObject().put("id", j.id).put("name", j.name).put("sound", j.soundId)
                .put("period", j.period).put("match", j.match).put("volume", j.volume).put("soften", j.soften) }))
            .put("imports", JSONArray(l.imports.map { s -> JSONObject().put("id", s.id).put("name", s.name).put("description", s.description)
                .put("frames", s.frames).put("sourceFrames", s.sourceFrames).put("sourceRate", s.sourceRate).put("error", s.error) })).toString()
        fun decode(text: String): Library {
            val root = JSONObject(text)
            require(root.getInt("version") == 1) { "Unsupported library version" }
            val imports = root.getJSONArray("imports").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { s ->
                Sound(s.getString("id"), s.getString("name"), s.getString("description"), s.getInt("frames"),
                    s.getLong("sourceFrames"), s.getInt("sourceRate"), if (s.has("error") && !s.isNull("error")) s.getString("error") else null)
            } } }
            require(imports.size <= 100 && imports.all { it.id.matches(Regex("[a-f0-9-]{36}")) && it.frames in 0..MAX_DECODED_FRAMES })
            val jobs = root.getJSONArray("jobs").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { j ->
                Job(j.getString("id"), j.getString("name"), j.getString("sound"), j.getLong("period"), j.getBoolean("match"), j.getDouble("volume").toFloat(), j.optBoolean("soften"))
            } } }
            require(jobs.size <= 100 && jobs.map { it.id }.distinct().size == jobs.size && jobs.all { it.volume in 0f..1f && it.period in 1..MAX_PERIOD })
            return Library(jobs, imports, Visual.fromSaved(root.getString("visual")))
        }
    }
}
