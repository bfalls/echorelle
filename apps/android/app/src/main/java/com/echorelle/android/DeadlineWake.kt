package com.echorelle.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock

/** One alarm for the earliest active timer. No persisted job execution or permission prompts. */
internal class DeadlineWake(context: Context,private val signal: ()->Unit) {
    private val context=context.applicationContext
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val lock=context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Echorelle:Playback").apply{setReferenceCounted(false)}
    private var alarm: PendingIntent?=null
    private var generation=0L
    init { receiver=java.lang.ref.WeakReference(this) }
    @Synchronized fun acquire() {if(!lock.isHeld)lock.acquire();if(BuildConfig.DEBUG)PlaybackWorkStats.wakeHeld=true}
    @Synchronized fun release() {if(lock.isHeld)lock.release();if(BuildConfig.DEBUG)PlaybackWorkStats.wakeHeld=false}
    @Synchronized fun schedule(deadlineNanos: Long): Boolean {
        cancel()
        if(Build.VERSION.SDK_INT>=31 && !alarms.canScheduleExactAlarms())return false
        val until=deadlineNanos-SystemClock.elapsedRealtimeNanos()
        if(until<5_000_000_000L)return false
        val intent=Intent(context,PlaybackWakeReceiver::class.java).putExtra("generation",generation)
        val pending=PendingIntent.getBroadcast(context,71,intent,PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val show=PendingIntent.getActivity(context,72,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return try {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis()+(until+999_999)/1_000_000,show),pending)
            alarm=pending;true
        } catch (_: SecurityException) {pending.cancel();false}
    }
    @Synchronized fun cancel() {generation++;alarm?.let{alarms.cancel(it);it.cancel()};alarm=null}
    @Synchronized private fun wake(token: Long) {
        if(token!=generation || alarm==null)return
        // Receiver-to-worker handoff: acquire before Android releases its broadcast wake lock.
        acquire();signal()
    }
    @Synchronized fun close() {cancel();release();if(receiver?.get()===this)receiver=null}
    companion object {
        @Volatile private var receiver: java.lang.ref.WeakReference<DeadlineWake>?=null
        fun receive(token: Long) {receiver?.get()?.wake(token)}
    }
}

class PlaybackWakeReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {DeadlineWake.receive(intent.getLongExtra("generation",-1))}
}
