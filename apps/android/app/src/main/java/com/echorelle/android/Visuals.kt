package com.echorelle.android

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Interval completion read from audio observations; drawing never advances playback. */
@Composable fun JobVisual(visual: Visual, snapshot: JobPlayback, period: Long, demo: Boolean = false, visible: Boolean = true) {
    val resolver = LocalContext.current.contentResolver
    var scale by remember { mutableFloatStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val animateInterval = demo || period >= 5L * SAMPLE_RATE
    val active=visible && rememberVisualVisibility()
    val moving = active && scale > 0 && animateInterval && (demo || snapshot.mode == JobMode.PLAYING)
    // Compose knows this is infinite decorative drawing (including in UI tests). Reading it in
    // Canvas invalidates drawing at display refresh rate without recomposing the job list.
    val redraw = if (moving) rememberInfiniteTransition(label="Visual redraw").animateFloat(
        0f, 1f, infiniteRepeatable(tween(8000, easing=LinearEasing)), label="Silent drawing") else null

    val night = MaterialTheme.colorScheme.surface.luminance() < .3f
    val models: VisualModels=viewModel()
    val assets=LocalContext.current.assets
    val glassArt=GlassArt.forPeriod(period)
    var glassReady by remember { mutableStateOf<Pair<Long,StainedGlassVisual>?>(null) }
    LaunchedEffect(visual,active,period) {
        if(visual==Visual.GLASS && active && glassReady?.first!=period) {
            val source=models.getGlass(glassArt) ?: withContext(Dispatchers.Default) {
                val workContext=coroutineContext
                GlassSource.load(assets,glassArt) { workContext.ensureActive() }
            }.also{models.putGlass(glassArt,it)}
            val renderer=withContext(Dispatchers.Default) {
                val workContext=coroutineContext
                StainedGlassVisual(period,source) {workContext.ensureActive()}.also {
                    workContext.ensureActive()
                    if(BuildConfig.DEBUG)VisualWorkStats.glassPreparations.incrementAndGet()
                }
            }
            glassReady=period to renderer
        }
    }
    val stainedGlass=glassReady?.takeIf{it.first==period}?.second
    val project=PaperProject.forPeriod(period)
    var paperReady by remember { mutableStateOf<Pair<Long,OrigamiVisual>?>(null) }
    LaunchedEffect(visual,active,period) {
        if(visual==Visual.BIRD && active && paperReady?.first!=period) {
            val sheet=models.getPaper(project) ?: withContext(Dispatchers.Default) {
                val workContext=coroutineContext
                OrigamiPrograms.create(project) { workContext.ensureActive() }
            }.also{models.putPaper(project,it)}
            // Share only immutable geometry. Each card owns its mutable grain/crease bitmap.
            val paper=withContext(Dispatchers.Default) {
                OrigamiVisual(sheet,period).also {
                    ensureActive()
                    if(BuildConfig.DEBUG)VisualWorkStats.paperPreparations.incrementAndGet()
                }
            }
            paperReady=period to paper
        }
    }
    val paper=paperReady?.takeIf{it.first==period}?.second
    val cycle=if(demo)0 else VisualProgress.observed(snapshot,period,SystemClock.elapsedRealtimeNanos()).cycle
    val seed=snapshot.visualSeed xor (cycle * -7046029254386353131L)
    val key=VisualModels.Key(period,seed)
    var prepared by remember { mutableStateOf<Pair<VisualModels.Key,CubeVisual>?>(null) }
    LaunchedEffect(visual,active,key) {
        if(visual==Visual.CUBE && active && prepared?.first!=key) {
            val cube=models.get(key) ?: withContext(Dispatchers.Default) {
                val workContext=coroutineContext
                CubeVisual(period,seed) { workContext.ensureActive() }.also {
                    workContext.ensureActive()
                    if(BuildConfig.DEBUG) VisualWorkStats.preparations.incrementAndGet()
                }
            }.also { models.put(key,it) }
            prepared=key to cube
        }
    }
    val cube=prepared?.takeIf { it.first==key }?.second
    Canvas(Modifier.fillMaxWidth().height(if(demo)110.dp else if(visual==Visual.GLASS)210.dp else 160.dp).clearAndSetSemantics { testTag="interval-visual" }) {
        if(!active) return@Canvas
        val tick = redraw?.value ?: .38f
        val now = SystemClock.elapsedRealtimeNanos()
        val progress = if(demo) VisualProgress.atFraction(period, tick.toDouble())
            else VisualProgress.observed(snapshot, period, now)
        val shortLoop = !animateInterval
        val revealing=when(visual) {
            Visual.BIRD -> paper?.timing?.isRevealing(progress.whole)==true
            Visual.CUBE -> cube?.timing?.isRevealing(progress.whole)==true
            Visual.GLASS -> stainedGlass?.timing?.isRevealing(progress.whole)==true
        }
        val p = if(shortLoop || (scale==0f && revealing)) 1f
            else if(scale == 0f) (floor(progress.whole * 20) / 20).toFloat() else progress.whole.toFloat()
        drawIntoCanvas { canvas ->
            when(visual) {
                Visual.BIRD -> paper?.draw(canvas.nativeCanvas, size.width, size.height, p, night)
                Visual.GLASS -> stainedGlass?.draw(canvas.nativeCanvas,size.width,size.height,p,night,scale>0f && animateInterval)
                Visual.CUBE -> cube?.let {
                    it.draw(canvas.nativeCanvas, size.width, size.height, p, night)
                    if(BuildConfig.DEBUG) VisualWorkStats.lastDrawnKey=key
                }
            }
        }
    }
}
