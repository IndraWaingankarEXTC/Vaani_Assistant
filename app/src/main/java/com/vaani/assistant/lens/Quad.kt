package com.vaani.assistant.lens

import kotlin.math.hypot
import kotlin.math.max

/**
 * The four-corner selection, as plain geometry.
 *
 * A user drags four handles in any order and any rotation; the perspective crop needs them in a
 * known order (top-left, top-right, bottom-right, bottom-left) and needs to know how big the
 * straightened image should be.
 */
object Quad {

    /** Reorder four x,y pairs into TL, TR, BR, BL. */
    fun ordered(points: FloatArray): FloatArray {
        require(points.size == 8) { "expected 4 points (8 floats), got ${points.size / 2}" }
        val indices = (0 until 4).sortedBy { points[it * 2 + 1] }
        val top = indices.take(2).sortedBy { points[it * 2] }
        val bottom = indices.drop(2).sortedBy { points[it * 2] }
        val tl = top[0]; val tr = top[1]; val bl = bottom[0]; val br = bottom[1]
        return floatArrayOf(
            points[tl * 2], points[tl * 2 + 1],
            points[tr * 2], points[tr * 2 + 1],
            points[br * 2], points[br * 2 + 1],
            points[bl * 2], points[bl * 2 + 1]
        )
    }

    /** How large the straightened crop should be, from the average edge lengths. */
    fun outputSize(points: FloatArray, minSide: Int = 48, maxSide: Int = 2048): Pair<Int, Int> {
        val q = ordered(points)
        val topWidth = distance(q[0], q[1], q[2], q[3])
        val bottomWidth = distance(q[6], q[7], q[4], q[5])
        val leftHeight = distance(q[0], q[1], q[6], q[7])
        val rightHeight = distance(q[2], q[3], q[4], q[5])
        val w = ((topWidth + bottomWidth) / 2f).toInt().coerceIn(minSide, maxSide)
        val h = ((leftHeight + rightHeight) / 2f).toInt().coerceIn(minSide, maxSide)
        return w to h
    }

    fun area(points: FloatArray): Float {
        val q = ordered(points)
        var sum = 0f
        for (i in 0 until 4) {
            val x1 = q[i * 2]; val y1 = q[i * 2 + 1]
            val j = (i + 1) % 4
            val x2 = q[j * 2]; val y2 = q[j * 2 + 1]
            sum += x1 * y2 - x2 * y1
        }
        return kotlin.math.abs(sum) / 2f
    }

    fun isUsable(points: FloatArray, minAreaFraction: Float = 0.02f, frameArea: Float): Boolean {
        if (area(points) < frameArea * minAreaFraction) return false
        // Reject a collapsed selection: four *distinct points*, not four distinct numbers.
        val q = ordered(points)
        val corners = (0 until 4).map { q[it * 2] to q[it * 2 + 1] }
        return corners.distinct().size == 4
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()

    /** Corner handle size, scaled to the screen so it is finger-sized on any device. */
    fun handleRadius(density: Float): Float = max(18f, 22f * density)
}
