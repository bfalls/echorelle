package com.echorelle.android

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.media.*
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Semaphore

/** The service owns runtime. UI, persistence and animation never advance the sample timeline. */
@SuppressLint("WakelockTimeout")
class PlaybackService : Service() {
    companion object {
        private val mutable = MutableStateFlow<Map<String, JobPlayback>>(emptyMap())
        val playback = mutable.asStateFlow()
        const val START = "com.echorelle.START"
        const val PREVIEW = "com.echorelle.PREVIEW"
        const val STOP = "com.echorelle.STOP"
        const val PAUSE = "com.echorelle.PAUSE"
        const val RESUME = "com.echorelle.RESUME"
        const val STOP_ALL = "com.echorelle.STOP_ALL"
        const val PREVIEW_ID = "preview"
    }
    private data class Command(val action: String, val id: String, val voice: JobVoice? = null, val reason: String = "")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val incoming = Channel<Intent>(32)
    private val commands = ConcurrentLinkedQueue<Command>()
    private val wake = Semaphore(0)
    private val alive = AtomicBoolean(true)
    private val slots = VoiceSlots()
    private val reasons = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val errors = mutableMapOf<String, JobPlayback>() // Main thread only.
    private var worker: Thread? = null
    private lateinit var audio: AudioManager
    private lateinit var session: MediaSession
    private var focus: AudioFocusRequest? = null
    private var hasFocus = false
    private var foreground = false
    private var handling = false
    private var receivedCommand = false
    private var idleSince = 0L
    private var initialEnvironment: Map<String, Any?> = emptyMap()
    @Volatile private var queuedFrames = 0L
    private var lastPlaying = false
    private var journal: ProbeJournal? = null
    @Volatile private var rendered = 0L
    @Volatile private var consumed = 0L
    @Volatile private var underruns = 0
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val repository get() = (application as EchorelleApp).repository
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { enqueue(Command(PAUSE, "", reason = "Headphones disconnected. Tap Resume.")) }
    }
    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("playback", "Playback controls", NotificationManager.IMPORTANCE_LOW))
        session = MediaSession(this, "Echorelle").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPause() { enqueue(Command(PAUSE, "")) }
                override fun onPlay() { incoming.trySend(Intent().setAction(RESUME)) }
                override fun onStop() { enqueue(Command(STOP_ALL, "")) }
            }); isActive = true
        }
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change != AudioManager.AUDIOFOCUS_GAIN) {
                    hasFocus = false
                    enqueue(Command(PAUSE, "", reason = "Audio focus lost. Tap Resume when ready."))
                }
            }, Handler(Looper.getMainLooper())).build()
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        worker = Thread({ stream() }, "EchorelleAudio").apply { start() }
        scope.launch { for (intent in incoming) handle(intent) }
        scope.launch {
            var tick = 0
            while (isActive) {
                publish()
                if (tick++ % 100 == 0 && BuildConfig.DEBUG) {
                    val fields = environment() + mapOf("rendered_frames" to rendered, "consumed_frames" to consumed,
                        "underruns" to underruns, "jobs" to org.json.JSONObject(playback.value.mapValues { (_, s) ->
                            org.json.JSONObject(mapOf("mode" to s.mode.name, "phase" to s.phase, "frames" to s.frames, "onsets" to s.onsets, "reason" to s.reason)) }))
                    withContext(Dispatchers.IO) { journal?.record("checkpoint", fields) }
                }
                delay(100)
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        receivedCommand = true
        if (intent?.action == START || intent?.action == PREVIEW || intent?.action == RESUME) ensureForeground()
        if (intent != null && !incoming.trySend(intent).isSuccess) {
            errors[intent.getStringExtra("id") ?: ""] = JobPlayback(JobMode.ERROR, reason = "Too many commands. Try again.")
        }
        return START_NOT_STICKY
    }
    private suspend fun handle(intent: Intent) {
        val action = intent.action ?: return
        handling = true
        val id = intent.getStringExtra("id") ?: ""
        try {
            repository.ready()
            if (action == START || action == PREVIEW) {
                val library = repository.state.value
                val sound: Sound
                val job: Job
                if (action == PREVIEW) {
                    sound = library.sounds.first { it.id == intent.getStringExtra("sound") }
                    job = Job(PREVIEW_ID, "Preview", sound.id, sound.frames.toLong(), true,
                        intent.getFloatExtra("volume", .8f), intent.getBooleanExtra("soften", false))
                } else {
                    job = library.jobs.first { it.id == id }
                    sound = library.sounds.first { it.id == job.soundId }
                    Eligibility.reason(sound, job.period, job.match)?.let { error(it) }
                    if (slots.any { it?.job?.id == id && it.mode != JobMode.STOPPED && it.mode != JobMode.ERROR }) return
                    require(slots.count { it != null && it.job.id != PREVIEW_ID && it.mode != JobMode.STOPPED && it.mode != JobMode.ERROR } < MAX_ACTIVE) { "Four jobs are already active. Stop one first." }
                }
                val pcm = withContext(Dispatchers.IO) { repository.pcm(sound) }
                require(requestFocus()) { "Audio focus unavailable. Tap Start or Resume when ready." }
                if (journal == null && BuildConfig.DEBUG) withContext(Dispatchers.IO) {
                    // Bounded diagnostics: keep the latest 20 full-app sessions, separate from old probe evidence.
                    filesDir.listFiles()?.filter { it.name.startsWith("app-events-") }?.sortedByDescending { it.lastModified() }?.drop(19)?.forEach { it.delete() }
                    journal = ProbeJournal(filesDir, "app-events-")
                    initialEnvironment = environment()
                    journal?.record("session_start", initialEnvironment + mapOf("app_version" to BuildConfig.VERSION_NAME))
                }
                withContext(Dispatchers.IO) { journal?.record("start_requested", mapOf("job" to job.id,
                    "sound" to sound.name, "period_frames" to job.period, "clip_frames" to sound.frames,
                    "sample_rate" to SAMPLE_RATE, "volume" to job.volume, "preview" to (action == PREVIEW))) }
                errors.remove(job.id)
                if (worker?.isAlive != true) worker = Thread({ stream() }, "EchorelleAudio").apply { start() }
                enqueue(Command(START, job.id, JobVoice(job, pcm, if (action == PREVIEW) intent.getIntExtra("repeats", 1) else 0)))
            } else if (action == RESUME) {
                require(requestFocus()) { "Audio focus unavailable. Tap Resume when ready." }
                if (id.isEmpty()) errors.clear() else errors.remove(id)
                enqueue(Command(RESUME, id))
            } else {
                if (action == STOP_ALL) errors.clear() else if (action == STOP) errors.remove(id)
                enqueue(Command(action, id))
            }
            withContext(Dispatchers.IO) { journal?.record("command", mapOf("action" to action, "id" to id)) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val reason = e.message ?: "Playback could not start."
            if (action == RESUME) {
                for (n in slots.indices) slots[n]?.let { if (id.isEmpty() || it.job.id == id) reasons[it.job.id] = reason }
            } else errors[if (action == PREVIEW) PREVIEW_ID else id] = JobPlayback(JobMode.ERROR, reason = reason)
        } finally { handling = false; publish() }
    }
    private fun enqueue(command: Command) {
        if (commands.size >= 32) return
        commands.add(command)
        if (wake.availablePermits() == 0) wake.release()
        scope.launch(Dispatchers.IO) { journal?.record("runtime_command", mapOf("action" to command.action, "id" to command.id, "reason" to command.reason)) }
    }
    private fun requestFocus(): Boolean {
        ensureForeground()
        if (!hasFocus) hasFocus = audio.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }
    private fun ensureForeground() { if (!foreground) { startForeground(1, notification()); foreground = true } }

    private fun stream() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = FloatArray(240)
        val lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Echorelle:Playback")
        var output: AudioTrack? = null
        var epochRendered = 0L
        var offset = buffer.size
        var lastHead = 0L; var headWrap = 0L
        var consumedBase = 0L
        var trackUnderruns = 0
        var lastProgress = SystemClock.elapsedRealtime()
        fun head(): Long {
            val value = (output?.playbackHeadPosition?.toLong() ?: 0) and 0xffffffffL
            if (value < lastHead) headWrap += 1L shl 32
            lastHead = value
            return value + headWrap
        }
        try {
            while (alive.get()) {
                if (commands.isNotEmpty() && offset == buffer.size) {
                    wake.tryAcquire()
                    // Commands change the next render block; another job's queued PCM is never flushed.
                    while (true) {
                        val c = commands.poll() ?: break
                        if (c.action == START) {
                            val old = slots.indexOfFirst { it?.job?.id == c.id }
                            if (old >= 0 && c.id != PREVIEW_ID && slots[old]?.mode != JobMode.STOPPED && slots[old]?.mode != JobMode.ERROR) continue
                            val empty = slots.indexOfFirst { it == null || it.mode == JobMode.STOPPED || it.mode == JobMode.ERROR }
                            val index = if (old >= 0) old else empty
                            val count = slots.count { it != null && it.job.id != PREVIEW_ID && it.mode != JobMode.STOPPED && it.mode != JobMode.ERROR }
                            if (index >= 0 && (c.id == PREVIEW_ID || old >= 0 || count < MAX_ACTIVE)) {
                                slots[index] = c.voice; reasons.remove(c.id)
                            } else reasons[c.id] = "Four jobs are already active. Stop one first."
                        } else for (n in slots.indices) slots[n]?.let { v ->
                            if (c.id.isEmpty() || c.id == v.job.id || c.action == STOP_ALL) {
                                when (c.action) {
                                    PAUSE -> if (v.mode == JobMode.PLAYING) {
                                        v.mode = if (c.reason.isEmpty()) JobMode.PAUSED else JobMode.INTERRUPTED
                                        if (v.job.id == PREVIEW_ID) { v.mode = JobMode.STOPPED; v.elapsed = 0 }
                                        reasons[v.job.id] = c.reason
                                    }
                                    RESUME -> if (v.mode == JobMode.PAUSED || v.mode == JobMode.INTERRUPTED) { v.mode = JobMode.PLAYING; v.ramp = 0; reasons.remove(v.job.id) }
                                    STOP, STOP_ALL -> { v.stopping = v.mode == JobMode.PLAYING; v.ramp = 0
                                        if (!v.stopping) { v.mode = JobMode.STOPPED; v.elapsed = 0 }; reasons.remove(v.job.id) }
                                }
                            }
                        }
                    }


                }
                if (slots.none { it?.mode == JobMode.PLAYING }) {
                    if (output != null && head() < epochRendered) {
                        check(SystemClock.elapsedRealtime() - lastProgress < 3000) { "Audio output stopped consuming frames." }
                        Thread.sleep(2); continue
                    }
                    if (output != null) { consumedBase += head(); consumed = consumedBase }
                    output?.release(); output = null; queuedFrames = 0; epochRendered = 0; lastHead = 0; headWrap = 0; offset = buffer.size
                    if (lock.isHeld) lock.release()
                    wake.acquire(); continue
                }
                if (output == null) {
                    val minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
                    check(minimum > 0) { "48 kHz audio output unavailable." }
                    output = AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum, 3840)).build()
                    check(output.state == AudioTrack.STATE_INITIALIZED)
                    lock.acquire(); output.play()
                    trackUnderruns = 0
                    lastProgress = SystemClock.elapsedRealtime()
                }
                if (offset == buffer.size) {
                    // Skip per-sample mixing only when every voice's entire block is silent.
                    // Cursors advance by the same exact frame count; PCM output still clocks it.
                    var silent=true
                    for(n in slots.indices) {
                        val voice=slots[n]
                        if(voice!=null && !voice.canAdvanceSilence(buffer.size)) { silent=false;break }
                    }
                    if(silent) {
                        buffer.fill(0f)
                        for(n in slots.indices) slots[n]?.advanceSilence(buffer.size)
                    } else {
                    for (i in buffer.indices) {
                        var sum = 0f
                        for (n in slots.indices) { val voice = slots[n]; if (voice != null) sum += voice.sample() }
                        // Transparent below 0.8; a continuous soft knee bounds overlapping peaks.
                        val magnitude = kotlin.math.abs(sum)
                        buffer[i] = if (magnitude <= .8f) sum else {
                            val excess = magnitude - .8f
                            kotlin.math.sign(sum) * (.8f + .2f * excess / (excess + .2f))
                        }
                    }
                    }
                    epochRendered += buffer.size; rendered += buffer.size; offset = 0
                }
                val written = output.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                check(written >= 0) { "Audio output error $written" }
                if (written > 0) lastProgress = SystemClock.elapsedRealtime()
                else check(SystemClock.elapsedRealtime() - lastProgress < 3000) { "Audio output stalled." }
                offset += written
                val played = head()
                consumed = consumedBase + played
                queuedFrames = (epochRendered - played).coerceAtLeast(0)
                val observedUnderruns = output.underrunCount
                underruns += (observedUnderruns - trackUnderruns).coerceAtLeast(0)
                trackUnderruns = observedUnderruns
                if (written == 0) Thread.sleep(2)
            }
        } catch (_: InterruptedException) {
            // Service teardown only; definitions never automatically restart.
        } catch (e: Exception) {
            for (n in slots.indices) slots[n]?.let { it.mode = JobMode.ERROR; reasons[it.job.id] = e.message ?: "Audio output failed." }
        } finally {
            try { output?.release() } finally { if (lock.isHeld) lock.release() }
        }
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun publish() {
        val snapshots = mutableMapOf<String, JobPlayback>()
        for (n in slots.indices) slots[n]?.let { v ->
            val frames = if (v.mode == JobMode.PLAYING) (v.elapsed - queuedFrames).coerceAtLeast(0) else v.elapsed
            snapshots[v.job.id] = JobPlayback(v.mode, frames % v.period, frames, reasons[v.job.id] ?: "", if (frames == 0L) 0 else (frames - 1) / v.period + 1,
                if (v.mode == JobMode.PLAYING) SystemClock.elapsedRealtimeNanos() else 0, v.visualSeed)
        }
        snapshots.putAll(errors)
        reasons.filterKeys { it !in snapshots }.forEach { (id, reason) -> snapshots[id] = JobPlayback(JobMode.ERROR, reason = reason) }
        mutable.value = snapshots.toMap()
        val playing = snapshots.values.any { it.mode == JobMode.PLAYING }
        if (!playing && !handling && commands.isEmpty() && incoming.isEmpty) {
            if (hasFocus) { audio.abandonAudioFocusRequest(focus!!); hasFocus = false }
            if (foreground) { stopForeground(STOP_FOREGROUND_DETACH); foreground = false }
            if (receivedCommand && snapshots.values.none { it.mode == JobMode.PAUSED || it.mode == JobMode.INTERRUPTED || it.mode == JobMode.ERROR }) {
                if (idleSince == 0L) idleSince = SystemClock.elapsedRealtime()
                if (SystemClock.elapsedRealtime() - idleSince > 1500) stopSelf()
            }
        } else {
            idleSince = 0L
        }
        if (playing != lastPlaying) {
            session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f).build())
            getSystemService(NotificationManager::class.java).notify(1, notification()); lastPlaying = playing
        }
    }
    private fun notification(): Notification {
        val playing = playback.value.values.any { it.mode == JobMode.PLAYING }
        fun action(name: String) = PendingIntent.getService(this, name.hashCode(), Intent(this, PlaybackService::class.java).setAction(name), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, "playback").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Echorelle")
            .setContentText(if (playing) "Interval sounds are playing" else "Playback paused or stopped")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(playing).addAction(Notification.Action.Builder(null, if (playing) "Pause all" else "Resume", action(if (playing) PAUSE else RESUME)).build())
            .addAction(Notification.Action.Builder(null, "Stop all", action(STOP_ALL)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1)).build()
    }
    override fun onDestroy() {
        val recorder = journal
        val summary = environment() + mapOf("initial_environment" to org.json.JSONObject(initialEnvironment),
            "rendered_frames" to rendered, "consumed_frames" to consumed, "underruns" to underruns,
            "reason" to "service_destroyed", "audible_output_verified" to false)
        Thread({ recorder?.record("session_end", summary) }, "EchorelleJournalClose").start()
        alive.set(false); worker?.interrupt(); scope.cancel(); incoming.close()
        focus?.let { audio.abandonAudioFocusRequest(it) }; unregisterReceiver(noisy); session.release()
        mutable.value = emptyMap(); stopForeground(STOP_FOREGROUND_REMOVE)
        // No PCM files or jobs are removed here.
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onTaskRemoved(rootIntent: Intent?) { scope.launch(Dispatchers.IO) { journal?.record("task_removed") }; super.onTaskRemoved(rootIntent) }
    private fun environment(): Map<String, Any?> {
        val power = getSystemService(PowerManager::class.java)
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        return mapOf("battery_percent" to if (scale > 0 && level >= 0) level * 100.0 / scale else null,
            "power_connected" to ((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0),
            "interactive" to power.isInteractive, "device_idle" to power.isDeviceIdleMode, "power_save" to power.isPowerSaveMode,
            "keyguard_locked" to getSystemService(KeyguardManager::class.java).isKeyguardLocked)
    }
}
