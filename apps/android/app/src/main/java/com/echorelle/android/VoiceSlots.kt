package com.echorelle.android
import java.util.concurrent.atomic.AtomicReferenceArray
internal class VoiceSlots {
    private val values = AtomicReferenceArray<JobVoice?>(5)
    val indices = 0..4
    operator fun get(index: Int): JobVoice? = values.get(index)
    operator fun set(index: Int, value: JobVoice?) = values.set(index, value)
    inline fun any(predicate: (JobVoice?) -> Boolean): Boolean { for (i in indices) if(predicate(get(i))) return true; return false }
    inline fun none(predicate: (JobVoice?) -> Boolean) = !any(predicate)
    inline fun count(predicate: (JobVoice?) -> Boolean): Int { var n=0; for(i in indices) if(predicate(get(i))) n++; return n }
    inline fun indexOfFirst(predicate: (JobVoice?) -> Boolean): Int { for(i in indices) if(predicate(get(i))) return i; return -1 }
}
