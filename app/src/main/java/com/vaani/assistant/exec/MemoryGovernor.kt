package com.vaani.assistant.exec

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import com.vaani.assistant.core.Prefs
import com.vaani.assistant.core.Vlog

/**
 * Keeps Vaani inside a 3 GB budget: 2 GB that the phone really has, plus 1 GB that we are allowed
 * to occupy only while it is reclaimable (mmap-ed model weights and page cache). Nothing here is
 * a hard reservation — Android can always take it back — so the rule is: hold the big model only
 * while there is comfortable headroom, and let go the instant the system says it is tight.
 */
class MemoryGovernor(private val ctx: Context) {

    data class Stats(
        val usedMb: Long,
        val availableMb: Long,
        val totalMb: Long,
        val lowMemory: Boolean,
        val appHeapMb: Long,
        val modelResident: Boolean
    ) {
        fun summary(): String =
            "app ${appHeapMb}MB · free ${availableMb}MB / ${totalMb}MB" +
                if (modelResident) " · model resident" else ""
    }

    private val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager

    fun stats(modelResident: Boolean = false): Stats {
        val info = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(info) }
        val runtime = Runtime.getRuntime()
        val heap = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        return Stats(
            usedMb = (info.totalMem - info.availMem) / (1024 * 1024),
            availableMb = info.availMem / (1024 * 1024),
            totalMb = info.totalMem / (1024 * 1024),
            lowMemory = info.lowMemory,
            appHeapMb = heap,
            modelResident = modelResident
        )
    }

    /** Headroom to keep a ~1 GB model mapped, leaving room for the rest of the phone. */
    fun canHoldModel(): Boolean {
        val s = stats()
        if (s.lowMemory) return false
        return s.availableMb >= RESIDENT_FLOOR_MB
    }

    /** True when the caller should release the model now. */
    fun shouldRelease(): Boolean {
        val s = stats()
        return s.lowMemory || s.availableMb < RELEASE_FLOOR_MB
    }

    /** Map onTrimMemory levels onto a release decision. */
    fun onTrim(level: Int): Boolean {
        val release = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        if (release) Vlog.i("Mem", "trim level $level, releasing model")
        return release
    }

    private companion object {
        /** Below this much free RAM the model is not worth holding. */
        const val RELEASE_FLOOR_MB = 600L
        /** Above this much free RAM it is comfortable to map a 1 GB model. */
        const val RESIDENT_FLOOR_MB = 1_000L
    }
}
