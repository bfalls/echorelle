package com.echorelle.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before

class SoundPickerTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Before fun allowTestNotifications() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant com.echorelle.android android.permission.POST_NOTIFICATIONS").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { stream -> stream.readBytes() }
        }
    }
    @Test fun shorteningIntervalKeepsIneligibleImportedSoundVisible() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as EchorelleApp).repository
        val original=runBlocking{repo.ready()}
        val sound=Sound(java.util.UUID.randomUUID().toString(),"Two-second fixture","Test import",96000)
        try {
            runBlocking{repo.update{it.copy(imports=it.imports+sound)}}
            ui.onNodeWithText("Add").performClick()
            ui.onNode(hasSetTextAction() and hasText("Interval in seconds")).performScrollTo().performTextReplacement("2")
            ui.onNodeWithContentDescription("Select Two-second fixture").performScrollTo().performClick()
            ui.onNodeWithText("Save job").assertIsEnabled()
            ui.onNode(hasSetTextAction() and hasText("Interval in seconds")).performScrollTo().performTextReplacement("1")
            ui.onNodeWithText("Save job").assertIsNotEnabled()
            ui.onAllNodesWithText("2.0s sound exceeds your 1.0s interval.").assertCountEquals(2)
            ui.onNodeWithContentDescription("Select Two-second fixture").performScrollTo().assertIsNotEnabled()
            ui.onNodeWithContentDescription("Match interval to sound length").performScrollTo().performClick()
            ui.onNodeWithText("Save job").assertIsEnabled()
            ui.onNodeWithText("Cancel").performClick()
        } finally {runBlocking{repo.update{original}}}
    }
    @Test fun matchRevalidationPreviewExitAndLiveReattachment() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as EchorelleApp).repository
        val original=runBlocking{repo.ready()}
        try {
            ui.onNodeWithText("Add").performClick()
            ui.onNodeWithContentDescription("Match interval to sound length").performScrollTo().performClick()
            ui.onNodeWithText("Save job").assertIsEnabled()
            ui.onNodeWithContentDescription("Select Warm Bell").performScrollTo().performClick()
            ui.onNodeWithContentDescription("Match interval to sound length").performScrollTo().performClick()
            ui.onNodeWithText("Save job").assertIsNotEnabled()
            ui.onNodeWithText("Match was turned off.", substring=true).performScrollTo().assertExists()
            ui.onNode(hasSetTextAction() and hasText("Interval in seconds")).performTextReplacement("1")
            ui.onNodeWithText("Save job").assertIsEnabled()
            ui.onNodeWithContentDescription("Seam preview Warm Bell").performScrollTo().performClick()
            ui.waitUntil(5000){PlaybackService.playback.value[PlaybackService.PREVIEW_ID]?.mode==JobMode.PLAYING}
            ui.onNodeWithText("Cancel").performClick()
            ui.waitUntil(5000){PlaybackService.playback.value[PlaybackService.PREVIEW_ID]?.mode != JobMode.PLAYING}
            val fixture=Job(java.util.UUID.randomUUID().toString(),"Reattach fixture","tone-0",48000,false,0f)
            runBlocking{repo.update{it.copy(jobs=it.jobs+fixture)}}
            ui.onNodeWithContentDescription("Start Reattach fixture").performScrollTo().performClick()
            ui.waitUntil(5000){(PlaybackService.playback.value[fixture.id]?.frames ?: 0)>48000}
            val before=PlaybackService.playback.value.getValue(fixture.id).frames
            ui.activityRule.scenario.recreate()
            ui.onNodeWithContentDescription("Pause Reattach fixture").performScrollTo().assertIsEnabled()
            assertTrue(PlaybackService.playback.value.getValue(fixture.id).frames>=before)
            ui.onNodeWithContentDescription("Stop Reattach fixture").performClick()
            ui.waitUntil(5000){PlaybackService.playback.value[fixture.id]?.mode != JobMode.PLAYING}
        } finally {
            context.startService(android.content.Intent(context,PlaybackService::class.java).setAction(PlaybackService.STOP_ALL))
            runBlocking{repo.update{original}}
        }
    }
    @Test fun noDefaultTimingExplicitPreviewSaveDeleteUndoAndChooser() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as EchorelleApp).repository
        val original=runBlocking{repo.ready()}
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant com.echorelle.android android.permission.POST_NOTIFICATIONS").close()
        try {
            ui.onAllNodesWithText("provisional",substring=true,ignoreCase=true).assertCountEquals(0)
            ui.onAllNodesWithText("testing is still pending",substring=true).assertCountEquals(0)
            ui.onAllNodesWithText("Saved jobs open stopped",substring=true).assertCountEquals(0)
            ui.onNodeWithText("Add").performClick()
            ui.onAllNodesWithText("decoded",substring=true,ignoreCase=true).assertCountEquals(0)
            ui.onAllNodesWithText("engine",substring=true,ignoreCase=true).assertCountEquals(0)
            ui.onNodeWithText("Save job").assertIsNotEnabled()
            ui.onNodeWithContentDescription("Select Warm Bell").performScrollTo().assertIsNotEnabled()
            ui.onNodeWithContentDescription("Preview Soft Ding").performScrollTo().performClick()
            ui.waitUntil(8000){PlaybackService.playback.value[PlaybackService.PREVIEW_ID]?.mode==JobMode.PLAYING}
            ui.waitUntil(8000){PlaybackService.playback.value[PlaybackService.PREVIEW_ID]?.mode==JobMode.STOPPED}
            ui.onNode(hasSetTextAction() and hasText("Name (optional)")).performScrollTo().performTextReplacement("UI fixture")
            ui.onNode(hasSetTextAction() and hasText("Interval in seconds")).performScrollTo().performTextReplacement("1")
            ui.onNodeWithText("Save job").assertIsEnabled().performClick()
            ui.waitUntil(5000){repo.state.value.jobs.any{it.name=="UI fixture"}}
            assertFalse(PlaybackService.playback.value.values.any{it.mode==JobMode.PLAYING})
            ui.onNodeWithContentDescription("Visual · ${repo.state.value.visual.title}").performClick()
            for(removed in listOf("The Loom","Magnetic Garden","Ink Creature","Constellation","Paper Landscape")) {
                ui.onNodeWithContentDescription(removed).assertDoesNotExist()
            }
            ui.onNodeWithContentDescription("Origami").performScrollTo().performClick()
            ui.waitUntil(5000){repo.state.value.visual==Visual.BIRD}
            ui.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                java.io.File(context.getExternalFilesDir(null), "ui-home.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            for (visual in Visual.entries) {
                ui.onNodeWithContentDescription("Visual · ${repo.state.value.visual.title}").performClick()
                ui.onNodeWithContentDescription(visual.title).performScrollTo().performClick()
                ui.waitUntil(5000){repo.state.value.visual==visual}
            }
            ui.activityRule.scenario.recreate()
            ui.onNodeWithContentDescription("Visual · Cube").assertExists()
            ui.onNodeWithText("UI fixture").performScrollTo().assertExists()
            val job=repo.state.value.jobs.first{it.name=="UI fixture"}
            ui.onNode(hasText("Delete") and hasAnyAncestor(hasTestTag("job-${job.id}"))).performScrollTo().performClick()
            ui.onNodeWithText("Undo").performClick()
            ui.waitUntil(5000){repo.state.value.jobs.any{it.id==job.id}}
            assertTrue(PlaybackService.playback.value[job.id]?.mode != JobMode.PLAYING)
        } finally {runBlocking{repo.update{original}}}
    }
}
