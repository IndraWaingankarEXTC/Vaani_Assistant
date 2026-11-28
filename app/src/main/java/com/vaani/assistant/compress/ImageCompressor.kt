package com.vaani.assistant.compress

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Drives the size search with a real JPEG encoder. */
class ImageCompressor(private val bitmap: Bitmap) {

    fun toTarget(targetBytes: Int): Compressor.Result? {
        val encoder = Encoder { quality, scale ->
            val width = (bitmap.width * scale).toInt().coerceAtLeast(8)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(8)
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, width, height, true) else bitmap
            val out = ByteArrayOutputStream()
            runCatching { scaled.compress(Bitmap.CompressFormat.JPEG, quality, out) }
            if (scaled !== bitmap) scaled.recycle()
            out.toByteArray()
        }
        return Compressor(encoder).toTarget(targetBytes)
    }
}
