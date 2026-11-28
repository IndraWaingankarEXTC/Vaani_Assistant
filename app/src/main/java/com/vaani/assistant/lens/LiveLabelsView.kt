package com.vaani.assistant.lens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Draws every tracked object with its own callout, next to it.
 *
 * The callout is recomputed from the object's box every frame, so it travels with the object as
 * the phone or the object moves — and because each object carries its own identity, a new one
 * arriving simply gets a new callout while the others keep theirs.
 */
class LiveLabelsView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private var tracks: List<Track> = emptyList()
    private var imageW = 0
    private var imageH = 0

    private val boxPaint = Paint().apply {
        color = Color.parseColor("#7CE3C1")
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 13f * density
        isAntiAlias = true
    }
    private val subTextPaint = Paint().apply {
        color = Color.parseColor("#B8B8CC")
        textSize = 11f * density
        isAntiAlias = true
    }
    private val chipPaint = Paint().apply {
        color = Color.parseColor("#E61B1B3A")
        isAntiAlias = true
    }
    private val chipBorder = Paint().apply {
        color = Color.parseColor("#7CE3C1")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        isAntiAlias = true
    }
    private val scratch = RectF()

    fun submit(newTracks: List<Track>, imageWidth: Int, imageHeight: Int) {
        tracks = newTracks
        imageW = imageWidth
        imageH = imageHeight
        invalidate()
    }

    fun clear() {
        tracks = emptyList()
        invalidate()
    }

    /** View coordinates to the object under the finger, if any. */
    fun hitTestView(x: Float, y: Float): Track? {
        if (imageW <= 0 || imageH <= 0) return null
        val sx = width.toFloat() / imageW
        val sy = height.toFloat() / imageH
        val ix = x / sx
        val iy = y / sy
        return tracks
            .filter { ix >= it.box.left && ix <= it.box.right && iy >= it.box.top && iy <= it.box.bottom }
            .minByOrNull { it.box.area }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (tracks.isEmpty() || imageW <= 0 || imageH <= 0) return

        val sx = width.toFloat() / imageW
        val sy = height.toFloat() / imageH

        for (track in tracks) {
            val box = RectF(
                track.box.left * sx, track.box.top * sy,
                track.box.right * sx, track.box.bottom * sy
            )
            canvas.drawRoundRect(box, 6f * density, 6f * density, boxPaint)

            val title = track.caption
            val sub = buildString {
                track.detail?.let { append(it) }
                if (track.identified) {
                    if (isNotEmpty()) append(" · ")
                    append("${(track.confidence * 100).toInt()}%")
                }
            }

            val titleWidth = textPaint.measureText(title)
            val subWidth = if (sub.isEmpty()) 0f else subTextPaint.measureText(sub)
            val padX = 10f * density
            val padY = 7f * density
            val lineHeight = textPaint.textSize
            val subHeight = if (sub.isEmpty()) 0f else subTextPaint.textSize + 2f * density
            val contentWidth = maxOf(titleWidth, subWidth) + padX * 2
            val contentHeight = lineHeight + subHeight + padY * 2

            val callout = CalloutLayout.place(
                box = Detection(box.left, box.top, box.right, box.bottom),
                textWidth = contentWidth,
                textHeight = contentHeight,
                canvasWidth = width.toFloat(),
                canvasHeight = height.toFloat(),
                gap = 8f * density,
                margin = 6f * density
            )

            scratch.set(callout.left, callout.top, callout.right, callout.bottom)
            canvas.drawRoundRect(scratch, 8f * density, 8f * density, chipPaint)
            canvas.drawRoundRect(scratch, 8f * density, 8f * density, chipBorder)

            canvas.drawText(title, callout.left + padX, callout.top + padY + lineHeight * 0.85f, textPaint)
            if (sub.isNotEmpty()) {
                canvas.drawText(
                    sub,
                    callout.left + padX,
                    callout.top + padY + lineHeight + subHeight * 0.8f,
                    subTextPaint
                )
            }
        }
    }
}
