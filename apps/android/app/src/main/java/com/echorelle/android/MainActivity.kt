package com.echorelle.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var pendingPlayback: Intent? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPlayback?.let { startForegroundService(it) }; pendingPlayback = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Home { action, id, sound, repeats, soften, volume ->
                    play(Intent(this, PlaybackService::class.java).setAction(action).putExtra("id", id)
                        .putExtra("sound", sound).putExtra("repeats", repeats).putExtra("soften", soften).putExtra("volume", volume))
                } }
            }
        }
    }
    private fun play(intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
            pendingPlayback = intent; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startForegroundService(intent)
    }
    override fun onStop() {
        super.onStop(); pendingPlayback = null
        if (PlaybackService.playback.value[PlaybackService.PREVIEW_ID]?.mode == JobMode.PLAYING)
            startService(Intent(this, PlaybackService::class.java).setAction(PlaybackService.STOP).putExtra("id", PlaybackService.PREVIEW_ID))
    }
}
private typealias Play = (String, String, String, Int, Boolean, Float) -> Unit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun Home(vm: HomeViewModel = viewModel(), play: Play) {
    val library by vm.library.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val visible=rememberVisualVisibility()
    val listState=rememberLazyListState()
    val visibleJobs by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet() } }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var chooser by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(message) { if (message.isNotEmpty()) { snackbar.showSnackbar(message); vm.report("") } }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) vm.importAudio(uri) }
    if (editorId != null) {
        Editor(library, library.jobs.firstOrNull { it.id == editorId }, importing, playback[PlaybackService.PREVIEW_ID], vm,
            onImport = { picker.launch(arrayOf("audio/*")) }, onClose = { editorId = null }, play = play)
        return
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        Column(Modifier.statusBarsPadding().padding(horizontal=16.dp, vertical=8.dp)) {
            Text("Echorelle", style=MaterialTheme.typography.headlineLarge)
            Text("Interval sounds & seamless loops", style=MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick={ chooser=true }, modifier=Modifier.semantics { contentDescription="Visual · ${library.visual.title}" }) { Text("Visual · ${library.visual.title}") }
                Button(onClick={ editorId="new" }, enabled=library.ready) { Text("Add") }
                if(playback.values.any { it.mode in listOf(JobMode.PLAYING,JobMode.PAUSED,JobMode.INTERRUPTED) })
                    OutlinedButton(onClick={vm.command(PlaybackService.STOP_ALL)}) { Text("Stop All") }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state=listState, contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if (library.jobs.isEmpty()) item {
                Text(if(library.ready) "Your sound. Your interval. Again." else "Opening your library…", style=MaterialTheme.typography.titleLarge)
                Text("Add a job, choose its timing, and save. Start it whenever you’re ready.")
            }
            if(library.message.isNotBlank()) item { Text(library.message, style=MaterialTheme.typography.bodySmall) }
            items(library.jobs, key={it.id}) { job ->
                val state = playback[job.id] ?: JobPlayback()
                val sound = library.sounds.firstOrNull { it.id == job.soundId }
                val active = state.mode in listOf(JobMode.PLAYING,JobMode.PAUSED,JobMode.INTERRUPTED)
                val cap = playback.filterKeys { it != PlaybackService.PREVIEW_ID }.values.count { it.mode in listOf(JobMode.PLAYING,JobMode.PAUSED,JobMode.INTERRUPTED) } >= MAX_ACTIVE
                val reason = Eligibility.reason(sound,job.period,job.match) ?: if(!active && cap) "Four jobs are active. Stop one before starting this job." else null
                Card(Modifier.fillMaxWidth().testTag("job-${job.id}")) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(job.name.ifBlank { "${sound?.name ?: "Unavailable sound"} · every ${Eligibility.seconds(job.period)}s" },style=MaterialTheme.typography.titleLarge)
                        Text("${sound?.name ?: "Unavailable sound"} · ${if(job.match) "Match · " else ""}${Eligibility.seconds(job.period)}s · ${(job.volume*100).toInt()}% volume")
                        Text(when(state.mode) {
                            JobMode.STOPPED -> "Stopped"; JobMode.PLAYING -> "Playing"; JobMode.PAUSED -> "Paused"
                            JobMode.INTERRUPTED -> "Interrupted"; JobMode.ERROR -> "Error"
                        },modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                        if(state.reason.isNotBlank()) Text(state.reason)
                        JobVisual(library.visual,state,job.period,visible=visible && job.id in visibleJobs)
                        if(active) Text(if(job.match) "Continuous loop" else "Next sound in ${String.format(Locale.ROOT,"%.1f",(job.period-state.phase).toDouble()/SAMPLE_RATE)}s")
                        if(reason!=null) Text(reason,color=MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(enabled=active || reason==null,onClick={
                                if(state.mode==JobMode.PLAYING) vm.command(PlaybackService.PAUSE,job.id)
                                else play(if(active) PlaybackService.RESUME else PlaybackService.START,job.id,"",1,false,job.volume)
                            },modifier=Modifier.semantics { contentDescription="${if(state.mode==JobMode.PLAYING) "Pause" else if(active) "Resume" else "Start"} ${job.name.ifBlank { sound?.name ?: "job" }}" }) {
                                Text(if(state.mode==JobMode.PLAYING) "Pause" else if(active) "Resume" else "Start")
                            }
                            OutlinedButton(onClick={vm.command(PlaybackService.STOP,job.id)},enabled=active || state.mode==JobMode.ERROR,
                                modifier=Modifier.semantics{contentDescription="Stop ${job.name.ifBlank{sound?.name ?: "job"}}"}) { Text("Stop") }
                            TextButton(onClick={editorId=job.id},enabled=!active,
                                modifier=Modifier.semantics{contentDescription="Edit ${job.name.ifBlank{sound?.name ?: "job"}}"}) { Text("Edit") }
                            TextButton(onClick={vm.delete(job) {
                                scope.launch {
                                    val result=snackbar.showSnackbar("Job deleted",actionLabel="Undo",duration=SnackbarDuration.Short)
                                    if(result==SnackbarResult.ActionPerformed) vm.undo(job) else vm.forgetUndo(job)
                                }
                            }}) { Text("Delete") }
                        }
                        if(active) Text("Stop to edit timing or audio.",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    if(chooser) ModalBottomSheet(onDismissRequest={chooser=false}) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Choose your visual",style=MaterialTheme.typography.headlineSmall)
            Text("Silent previews · applies to every job")
            Visual.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { visual ->
                        OutlinedCard(onClick={vm.changeVisual(visual);chooser=false},modifier=Modifier.weight(1f).padding(vertical=4.dp)
                            .semantics { selected=library.visual==visual; contentDescription=visual.title }) {
                            Column(Modifier.padding(8.dp)) {
                                JobVisual(visual,JobPlayback(),SAMPLE_RATE*(if(visual==Visual.BIRD)8L else 60L),demo=true,visible=visible)
                                Text("${if(library.visual==visual) "✓ " else ""}${visual.title}")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Editor(library: Library, original: Job?, importing: Boolean, preview: JobPlayback?, vm: HomeViewModel,
    onImport: () -> Unit, onClose: () -> Unit, play: Play) {
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var interval by rememberSaveable { mutableStateOf(original?.let { Eligibility.seconds(it.period) } ?: "") }
    var match by rememberSaveable { mutableStateOf(original?.match ?: false) }
    var soundId by rememberSaveable { mutableStateOf(original?.soundId ?: "tone-0") }
    var volume by rememberSaveable { mutableFloatStateOf(original?.volume ?: .8f) }
    var soften by rememberSaveable { mutableStateOf(original?.soften ?: false) }
    val sound=library.sounds.firstOrNull { it.id==soundId }
    val period=if(match) sound?.frames?.toLong() else Eligibility.parse(interval)
    val reason=Eligibility.reason(sound,period,match)
    val message by vm.message.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { vm.command(PlaybackService.STOP,PlaybackService.PREVIEW_ID); vm.cancelImport() } }
    androidx.activity.compose.BackHandler { onClose() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if(original==null) "New job" else "Edit job",style=MaterialTheme.typography.headlineMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(onClick=onClose) { Text("Cancel") }
                Button(enabled=reason==null && !importing,onClick={
                    vm.save(Job(original?.id ?: UUID.randomUUID().toString(),name.trim(),soundId,period!!,match,volume,soften),onClose)
                }) { Text("Save job") }
            }
            Text(reason ?: "Ready to save. Saving does not start playback.",color=if(reason!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if(message.isNotBlank()) Text(message)
            if(preview!=null && preview.mode==JobMode.PLAYING) Text("Preview playing",modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
            if(preview?.mode==JobMode.ERROR) Text(preview.reason,color=MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name,{name=it.take(120)},label={Text("Name (optional)")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(if(match && period!=null) Eligibility.seconds(period) else interval,{interval=it.take(32)},
                label={Text("Interval in seconds")},readOnly=match,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                modifier=Modifier.fillMaxWidth(),supportingText={Text("Time between sounds, including the sound itself.")})
            Row { Checkbox(match,{checked -> if(!checked && period!=null) interval=Eligibility.seconds(period);match=checked},modifier=Modifier.semantics { contentDescription="Match interval to sound length" });Text("Match interval to sound length — seamless loop",Modifier.padding(top=12.dp)) }
            if(!match && period!=null && period<SAMPLE_RATE) Text("Match was turned off. The retained ${Eligibility.seconds(period)}s is below the 1-second manual minimum.",color=MaterialTheme.colorScheme.error)
            Text("Volume · ${(volume*100).toInt()}%")
            Slider(volume,{volume=it},modifier=Modifier.semantics { contentDescription="Job volume" })
            Row { Checkbox(soften,{soften=it},modifier=Modifier.semantics { contentDescription="Smooth the joins between repeats" });Text("Smooth the joins between repeats",Modifier.padding(top=12.dp)) }
            Text("Seam preview plays the sound three times so you can hear how it joins.",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=onImport,enabled=!importing) { Text("Import audio") }
                if(importing) OutlinedButton(onClick={vm.cancelImport()}) { Text("Cancel import") }
                if(preview?.mode==JobMode.PLAYING) OutlinedButton(onClick={vm.command(PlaybackService.STOP,PlaybackService.PREVIEW_ID)}) { Text("Stop preview") }
            }
            if(importing) Text("Adding your sound…",style=MaterialTheme.typography.bodySmall)
            library.sounds.forEach { choice ->
                val why=Eligibility.reason(choice,if(match) choice.frames.toLong() else period,match)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(choice.name,style=MaterialTheme.typography.titleMedium)
                        Text("${if(choice.frames>0) "${Eligibility.seconds(choice.frames.toLong())}s · " else ""}${if(choice.id.startsWith("tone-")) choice.description else "Saved on this device"}")
                        if(why!=null) Text(why,color=MaterialTheme.colorScheme.error)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={soundId=choice.id},enabled=why==null,
                                modifier=Modifier.semantics { selected=soundId==choice.id;contentDescription="Select ${choice.name}" }) { Text(if(soundId==choice.id) "Selected" else "Select") }
                            OutlinedButton(onClick={play(PlaybackService.PREVIEW,"",choice.id,1,soften,volume)},enabled=choice.error==null && choice.frames>0,
                                modifier=Modifier.semantics {contentDescription="Preview ${choice.name}"}) {Text("Preview")}
                            TextButton(onClick={play(PlaybackService.PREVIEW,"",choice.id,3,soften,volume)},enabled=choice.error==null && choice.frames>0,
                                modifier=Modifier.semantics {contentDescription="Seam preview ${choice.name}"}) {Text("Seam preview")}
                            if(!choice.id.startsWith("tone-")) TextButton(onClick={vm.removeSound(choice)},
                                enabled=library.jobs.none{it.soundId==choice.id}) { Text("Remove import") }
                        }
                        if(!choice.id.startsWith("tone-") && library.jobs.any{it.soundId==choice.id}) Text("Used by a saved job",style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
