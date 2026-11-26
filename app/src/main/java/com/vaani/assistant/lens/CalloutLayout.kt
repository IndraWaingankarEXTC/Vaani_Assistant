package com.vaani.assistant.lens

/** Where one object's label sits on screen, in view pixels. */
data class Callout(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val above: Boolean
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * Places a label next to its object, and keeps it on screen.
 *
 * Above the box by default, because that is where the eye goes; below when there is no room up
 * there; slid left when the object is near the right edge. This is what makes the label look
 * attached to the thing rather than floating in a corner — and it is recomputed every frame, so it
 * travels with the object.
 */
object CalloutLayout {

    fun place(
        box: Detection,
        textWidth: Float,
        textHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
        gap: Float = 10f,
        margin: Float = 8f
    ): Callout {
        val width = min(textWidth, max(0f, canvasWidth - margin * 2))
        val height = textHeight

        val roomAbove = box.top - gap - height >= margin
        val above = roomAbove
        var top = if (roomAbove) box.top - gap - height else box.bottom + gap
        if (top + height > canvasHeight - margin) top = canvasHeight - margin - height
        if (top < margin) top = margin

        var left = box.left
        if (left + width > canvasWidth - margin) left = canvasWidth - margin - width
        if (left < margin) left = margin

        return Callout(left = left, top = top, width = width, height = height, above = above)
    }
}

private fun min(a: Float, b: Float): Float = if (a < b) a else b
private fun max(a: Float, b: Float): Float = if (a > b) a else b
