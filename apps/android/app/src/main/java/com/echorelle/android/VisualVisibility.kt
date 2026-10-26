package com.echorelle.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicLong

/** No visual workers or refresh clocks unless this activity is resumed and interactive. */
@Composable internal fun rememberVisualVisibility(): Boolean {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val power=remember(context) { context.getSystemService(PowerManager::class.java) }
    fun active()=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && power.isInteractive
    var visible by remember(lifecycle) { mutableStateOf(active()) }
    DisposableEffect(lifecycle,context) {
        val observer=LifecycleEventObserver { _,_ -> visible=active() }
        val receiver=object: BroadcastReceiver() {
            override fun onReceive(context: Context,intent: Intent) { visible=active() }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context,receiver,IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF);addAction(Intent.ACTION_SCREEN_ON)
        },ContextCompat.RECEIVER_NOT_EXPORTED)
        visible=active()
        onDispose { lifecycle.removeObserver(observer);context.unregisterReceiver(receiver) }
    }
    return visible
}

/** Small activity-owned cache survives recreation, but performs no background work itself. */
internal class VisualModels: ViewModel() {
    data class Key(val period: Long,val seed: Long)
    private val cubes=LinkedHashMap<Key,CubeVisual>(8,.75f,true)
    private val papers=mutableMapOf<PaperProject,SheetFolds>()
    private val glass=mutableMapOf<GlassArt,GlassSource>()
    fun getGlass(art: GlassArt)=glass[art]
    fun putGlass(art: GlassArt,source: GlassSource) { glass[art]=source }
    fun getPaper(project: PaperProject)=papers[project]
    fun putPaper(project: PaperProject,paper: SheetFolds) { papers[project]=paper }
    fun get(key: Key)=cubes[key]
    fun put(key: Key,cube: CubeVisual) {
        cubes[key]=cube
        while(cubes.size>6) cubes.remove(cubes.keys.first())
    }
}

/** Internal debug-only work counters for lifecycle tests; never displayed in the product. */
internal object VisualWorkStats {
    val draws=AtomicLong()
    val preparations=AtomicLong()
    val paperDraws=AtomicLong()
    val paperPreparations=AtomicLong()
    val glassDraws=AtomicLong()
    val glassPreparations=AtomicLong()
    @Volatile var lastGlassPeriod: Long?=null
    @Volatile var lastDrawnKey: VisualModels.Key?=null
    @Volatile var lastPaperProject: PaperProject?=null
}
