package com.echorelle.android

/** Opt-in debug test buffer. No allocation, file I/O or work when no test is capturing. */
internal object AudioOutputCapture {
    @Volatile var target: FloatArray?=null
    @Volatile var size=0
    val segmentNanos=LongArray(64)
    val segmentOffsets=IntArray(64)
    @Volatile var segments=0
    fun segmentStarted(now: Long) {
        if(target==null || segments>=segmentNanos.size)return
        segmentNanos[segments]=now;segmentOffsets[segments]=size;segments++
    }
    fun prepare() = Unit // Initialize the debug holder on the service thread, before rendering.
    fun accepted(buffer: FloatArray,offset: Int,count: Int) {
        val output=target?:return
        val copied=minOf(count,output.size-size)
        if(copied>0) {System.arraycopy(buffer,offset,output,size,copied);size+=copied}
    }
}

/** Debug-only measurements; never product text or a background polling task. */
internal object PlaybackWorkStats {
    val submitted=java.util.concurrent.atomic.AtomicLong()
    val publications=java.util.concurrent.atomic.AtomicLong()
    @Volatile var waiting=false
    @Volatile var wakeHeld=false
    @Volatile var alarmScheduled=false
    @Volatile var outputOpen=false
    @Volatile var lastLatenessNanos=0L
}
