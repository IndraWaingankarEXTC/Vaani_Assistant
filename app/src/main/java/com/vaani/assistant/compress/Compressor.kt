package com.vaani.assistant.compress

import kotlin.math.roundToInt

/**
 * Getting a file under a byte budget.
 *
 * "Compress this to 15 KB" has no closed-form answer: how small a JPEG gets depends on the picture.
 * So this searches. It binary-searches the encoder's quality setting, and if even the lowest usable
 * quality is still too big, it shrinks the image and searches again — returning the highest quality
 * that actually fits, rather than the smallest file it can make.
 *
 * The encoder is an interface so the search can be tested against a synthetic one, with no phone
 * and no real JPEGs involved.
 */
fun interface Encoder {
    /** Encode at [quality] 0..100 and [scale] 0..1 of the original dimensions. */
    fun encode(quality: Int, scale: Float): ByteArray
}

class Compressor(private val encoder: Encoder) {

    data class Result(val bytes: ByteArray, val quality: Int, val scale: Float) {
        val size: Int get() = bytes.size
    }

    /**
     * @param targetBytes the hard ceiling. The result never exceeds it, or is null if unreachable.
     * @param floorQuality below this, artefacts are worse than the size is worth.
     * @param minScale stop shrinking at this fraction of the original.
     */
    fun toTarget(
        targetBytes: Int,
        floorQuality: Int = 12,
        minScale: Float = 0.12f,
        steps: Int = 7
    ): Result? {
        if (targetBytes <= 0) return null

        // Cheap exit: if the best quality already fits, nothing else to do.
        val best = encoder.encode(MAX_QUALITY, 1f)
        if (best.size <= targetBytes) return Result(best, MAX_QUALITY, 1f)

        var scale = 1f
        while (scale >= minScale) {
            var low = floorQuality
            var high = MAX_QUALITY - 1
            var found: Result? = null

            repeat(steps) {
                if (low > high) return@repeat
                val quality = (low + high) / 2
                val bytes = encoder.encode(quality, scale)
                if (bytes.size <= targetBytes) {
                    // Fits: keep it, and try to spend the remaining budget on quality.
                    found = Result(bytes, quality, scale)
                    low = quality + 1
                } else {
                    high = quality - 1
                }
            }
            if (found != null) return found
            scale *= 0.72f
        }
        return null
    }
}

/** Byte budgets as people say them: "15 KB", "2 MB". */
object Sizes {

    fun parse(spoken: String): Int? {
        val text = spoken.lowercase().replace(" ", "")
        val match = Regex("(\\d+(?:\\.\\d+)?)(kb|k|mb|m|bytes?|b)").find(text) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2]
        val multiplier = when {
            unit.startsWith("m") -> 1024.0 * 1024.0
            unit.startsWith("k") -> 1024.0
            else -> 1.0
        }
        val bytes = (value * multiplier).roundToInt()
        return if (bytes > 0) bytes else null
    }

    fun human(bytes: Int): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes bytes"
    }
}

private const val MAX_QUALITY = 95
