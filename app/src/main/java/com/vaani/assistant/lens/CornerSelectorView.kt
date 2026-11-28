package com.vaani.assistant.lens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * The frozen frame with four draggable corners.
 *
 * The user puts the handles on the corners of the device — or of its label, which is often the
 * more useful box — and the quad is what gets straightened and read.
 */
class CornerSelectorView(context: Context) : View(context) {

    private var frame: Bitmap? = null
    private val quad = FloatArray(8)
    private var dragging = -1

    private val dim = Paint().apply { color = Color.parseColor("#99000000") }
    private val line = Paint().apply {
        color = Color.parseColor("#7CE3C1")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val handleFill = Paint().apply {
        color = Color.parseColor("#7CE3C1")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handleRing = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val path = Path()
    private val scratch = RectF()
    private val radius = Quad.handleRadius(resources.displayMetrics.density)

    /** Where the bitmap sits inside this view, so view coordinates can become bitmap coordinates. */
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setFrame(bitmap: Bitmap) {
        frame = bitmap
        layoutFrame()
        resetQuad()
        invalidate()
    }

    fun clearFrame() {
        frame = null
        invalidate()
    }

    private fun layoutFrame() {
        val bmp = frame ?: return
        if (width == 0 || height == 0) return
        scale = minOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        offsetX = (width - bmp.width * scale) / 2f
        offsetY = (height - bmp.height * scale) / 2f
    }

    private fun resetQuad() {
        val bmp = frame ?: return
        val inset = 0.18f
        val l = offsetX + bmp.width * scale * inset
        val t = offsetY + bmp.height * scale * inset
        val r = offsetX + bmp.width * scale * (1 - inset)
        val b = offsetY + bmp.height * scale * (1 - inset)
        setCorners(l, t, r, t, r, b, l, b)
    }

    fun setCorners(l: Float, t: Float, r: Float, tr: Float, br: Float, bb: Float, bl: Float, bt: Float) {
        quad[0] = l; quad[1] = t
        quad[2] = r; quad[3] = tr
        quad[4] = br; quad[5] = bb
        quad[6] = bl; quad[7] = bt
        invalidate()
    }

    /** The selection in bitmap pixels, ordered TL, TR, BR, BL. */
    fun quadInBitmap(): FloatArray? {
        val bmp = frame ?: return null
        if (scale <= 0f) return null
        val out = FloatArray(8)
        for (i in 0 until 4) {
            out[i * 2] = ((quad[i * 2] - offsetX) / scale).coerceIn(0f, bmp.width.toFloat())
            out[i * 2 + 1] = ((quad[i * 2 + 1] - offsetY) / scale).coerceIn(0f, bmp.height.toFloat())
        }
        return Quad.ordered(out)
    }

    fun frameArea(): Float {
        val bmp = frame ?: return 1f
        return bmp.width.toFloat() * bmp.height
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutFrame()
        if (frame != null) resetQuad()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frame ?: return

        // The photo, dimmed everywhere except inside the selection.
        scratch.set(offsetX, offsetY, offsetX + bmp.width * scale, offsetY + bmp.height * scale)
        canvas.drawBitmap(bmp, null, scratch, null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)

        path.reset()
        path.moveTo(quad[0], quad[1])
        path.lineTo(quad[2], quad[3])
        path.lineTo(quad[4], quad[5])
        path.lineTo(quad[6], quad[7])
        path.close()

        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, null, scratch, null)
        canvas.restore()

        canvas.drawPath(path, line)
        for (i in 0 until 4) {
            val x = quad[i * 2]
            val y = quad[i * 2 + 1]
            canvas.drawCircle(x, y, radius, handleFill)
            canvas.drawCircle(x, y, radius, handleRing)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (frame == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestCorner(event.x, event.y)
                return dragging >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging < 0) return false
                quad[dragging * 2] = event.x.coerceIn(0f, width.toFloat())
                quad[dragging * 2 + 1] = event.y.coerceIn(0f, height.toFloat())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = -1
                return true
            }
        }
        return false
    }

    private fun nearestCorner(x: Float, y: Float): Int {
        var best = -1
        var bestDistance = radius * 2.2f
        for (i in 0 until 4) {
            val d = hypot(x - quad[i * 2], y - quad[i * 2 + 1])
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }
}
