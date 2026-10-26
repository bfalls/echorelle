package com.echorelle.android

import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.io.File

/** Small persistent control-event journal; PCM and file contents are never included. */
internal class ProbeJournal(directory: File, prefix: String = "events-") {
    val id = System.currentTimeMillis().toString()
    val file = File(directory, "$prefix$id.jsonl")

    @Synchronized fun record(event: String, fields: Map<String, Any?> = emptyMap()) {
        try {
            if (file.length() > 4L * 1024 * 1024 && event == "checkpoint") return
            val record = JSONObject().put("event", event)
                .put("elapsed_ns", SystemClock.elapsedRealtimeNanos())
                .put("wall_ms", System.currentTimeMillis())
                .put("run_id", id)
                .put("details", JSONObject(fields))
            file.appendText("$record\n")
        } catch (error: Exception) {
            // A diagnostic storage failure must never stop audio playback.
            Log.e("EchorelleProbe", "Event journal write failed", error)
        }
    }
}
