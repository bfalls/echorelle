package com.echorelle.android

import android.os.PowerManager
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class VisualLifecycleTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun twoOrigamiCardsKeepTheirMaterialStateIndependent() {
        val period=1265L*SAMPLE_RATE
        val first=mutableStateOf(JobPlayback(JobMode.PAUSED,phase=period/5))
        ui.runOnUiThread {
            VisualWorkStats.lastPaperProject=null
            ui.activity.setContent { MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    JobVisual(Visual.BIRD,first.value,period)
                    JobVisual(Visual.BIRD,JobPlayback(JobMode.PAUSED,phase=period*4/5),period)
                }
            } }
        }
        ui.waitUntil(10000){VisualWorkStats.lastPaperProject==PaperProject.SHELL}
        ui.waitForIdle()
        fun capture()=ui.onAllNodesWithTag("interval-visual")[1].captureToImage().asAndroidBitmap()
        val second=capture()
        ui.runOnIdle { first.value=first.value.copy(phase=period*3/5) }
        assertTrue("Advancing another card must not replace this card's crease material",second.sameAs(capture()))
    }
    @Test fun origamiAlsoStopsWhenHiddenAndSeeksTheCurrentFoldOnWake() {
        val period=1265L*SAMPLE_RATE
        val snapshot=mutableStateOf(JobPlayback(JobMode.PLAYING,phase=period/4,frames=period/4))
        ui.runOnUiThread {
            VisualWorkStats.lastPaperProject=null
            ui.activity.setContent { MaterialTheme { JobVisual(Visual.BIRD,snapshot.value,period) } }
        }
        ui.waitUntil(10000){VisualWorkStats.lastPaperProject==PaperProject.SHELL}
        fun capture()=ui.onNodeWithTag("interval-visual").captureToImage().asAndroidBitmap()
        val before=capture()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
        try {
            shell("input keyevent 223");SystemClock.sleep(750)
            assertFalse(ui.activity.getSystemService(PowerManager::class.java).isInteractive)
            val draws=VisualWorkStats.paperDraws.get();val builds=VisualWorkStats.paperPreparations.get()
            ui.runOnUiThread { snapshot.value=JobPlayback(JobMode.PAUSED,phase=period*9/10,frames=500*period+period*9/10) }
            SystemClock.sleep(750)
            assertEquals(draws,VisualWorkStats.paperDraws.get());assertEquals(builds,VisualWorkStats.paperPreparations.get())
            shell("input keyevent 224");shell("wm dismiss-keyguard")
            ui.waitUntil(10000){VisualWorkStats.paperDraws.get()>draws}
            val after=capture()
            assertFalse(before.sameAs(after));assertEquals(builds,VisualWorkStats.paperPreparations.get())
            SystemClock.sleep(150);assertTrue(after.sameAs(capture()))
        } finally { shell("input keyevent 224");shell("wm dismiss-keyguard") }
    }
    @Test fun dayLongCubeBuildAndSparseSeekReportActualLatency() {
        val start=SystemClock.elapsedRealtimeNanos()
        val cube=CubeSequence(MAX_PERIOD,109)
        val buildMs=(SystemClock.elapsedRealtimeNanos()-start)/1e6
        val seeks=DoubleArray(100) { i ->
            val time=SystemClock.elapsedRealtimeNanos()
            cube.stateAt((i*719)%cube.turns.size)
            assertTrue(cube.lastSeekTurns<128)
            (SystemClock.elapsedRealtimeNanos()-time)/1e6
        }.sorted()
        val data=org.json.JSONObject().put("build_ms",buildMs).put("seek_p95_ms",seeks[94])
            .put("seek_max_ms",seeks.last()).put("checkpoint_bytes",cube.checkpointBytes)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=java.io.File(context.getExternalFilesDir(null),"power-review").apply{mkdirs()}
        java.io.File(directory,"cube-latency.json").writeText(data.toString(2))
        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply{putString("cubeLatency",data.toString())})
    }
    @Test fun screenOffDoesNoCubeWorkAndWakeSeeksOnlyTheCurrentInterval() {
        val period=60L*SAMPLE_RATE
        val snapshot=mutableStateOf(JobPlayback(JobMode.PLAYING,phase=period/4,frames=period/4,visualSeed=124))
        ui.runOnUiThread { ui.activity.setContent { MaterialTheme { JobVisual(Visual.CUBE,snapshot.value,period) } } }
        ui.waitUntil(10000){VisualWorkStats.lastDrawnKey==VisualModels.Key(period,124)}
        fun capture()=ui.onNodeWithTag("interval-visual").captureToImage().asAndroidBitmap()
        val before=capture()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).close() }
        try {
            shell("input keyevent 223")
            SystemClock.sleep(750)
            assertFalse(ui.activity.getSystemService(PowerManager::class.java).isInteractive)
            val draws=VisualWorkStats.draws.get();val builds=VisualWorkStats.preparations.get()
            // Simulate 500 missed intervals, without replaying or preparing any of them.
            ui.runOnUiThread { snapshot.value=JobPlayback(JobMode.PAUSED,phase=period*3/4,frames=500*period+period*3/4,visualSeed=124) }
            SystemClock.sleep(1000)
            assertEquals(draws,VisualWorkStats.draws.get())
            assertEquals(builds,VisualWorkStats.preparations.get())
            shell("input keyevent 224");shell("wm dismiss-keyguard")
            ui.waitUntil(10000){VisualWorkStats.preparations.get()>builds}
            assertEquals(builds+1,VisualWorkStats.preparations.get())
            val after=capture()
            assertFalse(before.sameAs(after))
            SystemClock.sleep(150)
            assertTrue(after.sameAs(capture()))
        } finally { shell("input keyevent 224");shell("wm dismiss-keyguard") }
    }
}
