package com.vaani.assistant.compress

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.content.Context
import java.io.ByteArrayOutputStream

/**
 * Shrinking a PDF to a byte budget.
 *
 * Android has no PDF re-writer, so the pages are re-drawn: rendered to bitmaps at a decreasing
 * scale, re-encoded as JPEG, and assembled into a new PDF. That is what online "compress PDF"
 * tools do too, and it has the same cost — **text stops being selectable**, because each page
 * becomes a picture. A PDF that is already mostly images barely shrinks; one full of text shrinks a
 * lot and looks fine on screen.
 */
class PdfCompressor(private val context: Context, private val uri: Uri) {

    data class Result(val bytes: ByteArray, val renderScale: Float, val quality: Int)

    fun toTarget(targetBytes: Int): Result? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            PdfRenderer(descriptor).use { renderer ->
                for (renderScale in RENDER_SCALES) {
                    for (quality in QUALITIES) {
                        val bytes = build(renderer, renderScale, quality) ?: continue
                        if (bytes.size <= targetBytes) return Result(bytes, renderScale, quality)
                    }
                }
            }
        }
        return null
    }

    private fun build(renderer: PdfRenderer, renderScale: Float, quality: Int): ByteArray? = runCatching {
        val document = PdfDocument()
        for (index in 0 until renderer.pageCount) {
            renderer.openPage(index).use { page ->
                val width = (page.width * renderScale).toInt().coerceAtLeast(64)
                val height = (page.height * renderScale).toInt().coerceAtLeast(64)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val jpeg = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                    stream.toByteArray()
                }
                bitmap.recycle()

                val info = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                val outPage = document.startPage(info)
                val image = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
                if (image != null) {
                    outPage.canvas.drawBitmap(image, 0f, 0f, null)
                    image.recycle()
                }
                document.finishPage(outPage)
            }
        }
        ByteArrayOutputStream().use { stream ->
            document.writeTo(stream)
            document.close()
            stream.toByteArray()
        }
    }.getOrNull()

    private companion object {
        val RENDER_SCALES = floatArrayOf(2.0f, 1.5f, 1.0f, 0.75f, 0.5f)
        val QUALITIES = intArrayOf(80, 60, 40, 25)
    }
}
