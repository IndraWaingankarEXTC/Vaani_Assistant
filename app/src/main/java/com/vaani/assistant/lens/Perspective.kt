package com.vaani.assistant.lens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Straightens the four-corner selection into a rectangle.
 *
 * This is what makes the crop worth doing: photographed at an angle, a label is unreadable to OCR;
 * warped flat, it reads cleanly. `Matrix.setPolyToPoly` with four points gives the homography.
 */
object Perspective {

    fun warp(source: Bitmap, quad: FloatArray): Bitmap? {
        if (source.width < 2 || source.height < 2) return null
        val (outW, outH) = Quad.outputSize(quad)
        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        val matrix = Matrix()
        val mapped = matrix.setPolyToPoly(Quad.ordered(quad), 0, dst, 0, 4)
        if (!mapped) return null

        return runCatching {
            val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            out
        }.getOrNull()
    }
}
