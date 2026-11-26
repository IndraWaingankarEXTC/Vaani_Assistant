package com.vaani.assistant.lens

import kotlin.math.max
import kotlin.math.min

/** One thing the detector saw in one frame, in upright image coordinates. */
data class Detection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val label: String? = null
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
    val centreX: Float get() = (left + right) / 2f
    val centreY: Float get() = (top + bottom) / 2f
}

/** An object followed across frames, with whatever we have learned about it. */
data class Track(
    val id: Int,
    val box: Detection,
    val title: String?,
    val detail: String?,
    val confidence: Float,
    val firstSeen: Long,
    val lastSeen: Long,
    val identified: Boolean,
    val attempted: Boolean
) {
    /** What the callout says before anything has been read: the detector's own guess. */
    val caption: String get() = title ?: box.label ?: "object"
}

/**
 * Keeps a stable identity on each object as it moves, so its callout can follow it.
 *
 * ML Kit hands back a fresh list every frame with no guarantee of order or identity, so this does
 * its own association: each new detection is matched to the existing track it overlaps most (IoU),
 * and anything unmatched becomes a new object with a new id. Objects that vanish are dropped after
 * a short grace period, so a wobble does not create a duplicate.
 *
 * Pure logic: no camera, no Android, so the hard part is testable.
 */
class ObjectTracker(
    private val maxAgeMs: Long = 1_500,
    private val iouThreshold: Float = 0.20f,
    private val maxTracks: Int = 8
) {

    private var nextId = 1
    private val tracks = LinkedHashMap<Int, Track>()

    fun update(detections: List<Detection>, now: Long): List<Track> {
        // 1. Forget what has not been seen for a while.
        tracks.values.removeAll { now - it.lastSeen > maxAgeMs }

        val unmatched = detections.toMutableList()
        val usedIds = mutableSetOf<Int>()

        // 2. Match each detection to the track it overlaps most, best pairs first.
        val pairs = mutableListOf<Triple<Int, Detection, Float>>()
        for (detection in detections) {
            for (track in tracks.values) {
                val score = iou(track.box, detection)
                if (score >= iouThreshold) pairs.add(Triple(track.id, detection, score))
            }
        }
        for ((id, detection, _) in pairs.sortedByDescending { it.third }) {
            if (id in usedIds || detection !in unmatched) continue
            val existing = tracks[id] ?: continue
            tracks[id] = existing.copy(box = detection, lastSeen = now)
            usedIds.add(id)
            unmatched.remove(detection)
        }

        // 3. Whatever is left is something new.
        for (detection in unmatched) {
            if (tracks.size >= maxTracks) {
                val oldest = tracks.values.minByOrNull { it.lastSeen } ?: break
                tracks.remove(oldest.id)
            }
            val id = nextId++
            tracks[id] = Track(
                id = id,
                box = detection,
                title = null,
                detail = null,
                confidence = 0f,
                firstSeen = now,
                lastSeen = now,
                identified = false,
                attempted = false
            )
        }

        return tracks.values.sortedBy { it.id }
    }

    /** Store what the reader learned about one object. */
    fun identify(id: Int, reading: Reading, now: Long): Boolean {
        val existing = tracks[id] ?: return false
        val title = reading.title.takeIf { it != Reading.UNKNOWN }
        tracks[id] = existing.copy(
            title = title ?: existing.box.label,
            detail = reading.category ?: reading.model,
            confidence = reading.confidence,
            lastSeen = now,
            identified = true
        )
        return title != null
    }

    /**
     * The next object worth reading, claimed so it is not queued twice. Objects already tried are
     * not retried: if the label was unreadable from this angle, moving the phone creates a *new*
     * object, and the old one is left alone rather than hammering the recogniser every frame.
     */
    fun claimNextToIdentify(minAreaFraction: Float = 0.05f, frameArea: Float = 0f): Track? {
        val candidate = tracks.values.firstOrNull { track ->
            !track.identified && !track.attempted &&
                (frameArea <= 0f || track.box.area >= frameArea * minAreaFraction)
        } ?: return null
        tracks[candidate.id] = candidate.copy(attempted = true)
        return candidate
    }

    fun current(): List<Track> = tracks.values.sortedBy { it.id }

    /** Which callout did a tap land on? Topmost (smallest) box wins. */
    fun hitTest(x: Float, y: Float): Track? =
        tracks.values
            .filter { x >= it.box.left && x <= it.box.right && y >= it.box.top && y <= it.box.bottom }
            .minByOrNull { it.box.area }

    fun reset() {
        tracks.clear()
        nextId = 1
    }

    companion object {
        /** Intersection over union: how much two boxes agree. */
        fun iou(a: Detection, b: Detection): Float {
            val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
            val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
            val intersection = ix * iy
            if (intersection <= 0f) return 0f
            val union = a.area + b.area - intersection
            return if (union <= 0f) 0f else intersection / union
        }
    }
}
