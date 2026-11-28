package com.vaani.assistant.convert

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.vaani.assistant.compress.ImageCompressor
import com.vaani.assistant.compress.PdfCompressor
import com.vaani.assistant.compress.Sizes
import com.vaani.assistant.core.Vlog
import java.io.File
import java.io.FileOutputStream

/**
 * Conversions the phone can do by itself.
 *
 * Images to PDF, text to PDF, and PDF pages back to images — all with the platform's own
 * `PdfDocument` and `PdfRenderer`, so there is nothing to download and nothing sent anywhere.
 *
 * Office formats are deliberately absent: Android has no renderer for them, and a converter that
 * silently mangles your slides is worse than one that says it cannot.
 */
class ConverterActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var compressing = false
    private var targetBytes = 15 * 1024

    companion object {
        const val EXTRA_COMPRESS = "compress"
        const val EXTRA_TARGET = "target_bytes"
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            status.text = "Nothing chosen."
            return@registerForActivityResult
        }
        convert(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        compressing = intent.getBooleanExtra(EXTRA_COMPRESS, false)
        targetBytes = intent.getIntExtra(EXTRA_TARGET, 15 * 1024)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Convert a file"
            textSize = 22f
            setTextColor(Color.BLACK)
        }
        status = TextView(this).apply {
            text = if (compressing) {
                "Compressing to under ${targetBytes / 1024} KB. Pick a file."
            } else {
                "Pick an image, a text file or a PDF.\n" +
                    "Images and text become a PDF; a PDF becomes images.\n" +
                    "Or tap a size above to compress instead."
            }
            textSize = 15f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(16), 0, dp(24))
        }
        val sizeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf(15, 50, 100, 500).forEach { kb ->
            sizeRow.addView(Button(this).apply {
                text = "$kb KB"
                setOnClickListener {
                    compressing = true
                    targetBytes = kb * 1024
                    status.text = "Compressing to under $kb KB. Pick a file."
                }
            })
        }
        val pick = Button(this).apply {
            text = "Choose a file"
            setOnClickListener { picker.launch(arrayOf("image/*", "text/*", "application/pdf")) }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        root.addView(title)
        root.addView(status)
        root.addView(sizeRow)
        root.addView(pick)
        root.addView(close)
        setContentView(root)
    }

    private fun convert(uri: Uri) {
        val name = displayName(uri) ?: "file"
        val kind = ConvertPlan.kindFor(name)
        status.text = "Working on $name…"

        if (compressing) {
            compress(uri, name, kind)
            return
        }

        val result = runCatching {
            when (kind) {
                ConvertPlan.Kind.IMAGES_TO_PDF -> imagesToPdf(uri, ConvertPlan.outputName(name, "pdf"))
                ConvertPlan.Kind.TEXT_TO_PDF -> textToPdf(uri, ConvertPlan.outputName(name, "pdf"))
                ConvertPlan.Kind.PDF_TO_IMAGES -> pdfToImages(uri, name.substringBeforeLast('.'))
                ConvertPlan.Kind.UNSUPPORTED -> null
            }
        }

        status.text = when {
            result.isFailure -> "That failed: ${result.exceptionOrNull()?.message}"
            result.getOrNull() == null ->
                "I can only convert images, text and PDFs. Office files need a rendering engine Android does not have."
            else -> "Done — saved to Downloads:\n${result.getOrNull()}"
        }
    }

    // ------------------------------------------------------------------ compressing

    private fun compress(uri: Uri, name: String, kind: ConvertPlan.Kind) {
        status.text = "Compressing $name to under ${Sizes.human(targetBytes)}…"
        val outcome = runCatching {
            when (kind) {
                ConvertPlan.Kind.IMAGES_TO_PDF -> {
                    val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        ?: return@runCatching null
                    val result = ImageCompressor(bitmap).toTarget(targetBytes)
                    bitmap.recycle()
                    result?.let {
                        val outName = "${name.substringBeforeLast('.')}-compressed.jpg"
                        saveBytes(it.bytes, outName, "image/jpeg")?.let { saved ->
                            "$saved — ${Sizes.human(it.size)} at quality ${it.quality}, " +
                                "scale ${(it.scale * 100).toInt()}%"
                        }
                    }
                }
                ConvertPlan.Kind.PDF_TO_IMAGES -> {
                    val result = PdfCompressor(this, uri).toTarget(targetBytes) ?: return@runCatching null
                    val outName = "${name.substringBeforeLast('.')}-compressed.pdf"
                    saveBytes(result.bytes, outName, "application/pdf")?.let { saved ->
                        "$saved — ${Sizes.human(result.bytes.size)}"
                    }
                }
                else -> null
            }
        }
        status.text = when {
            outcome.isFailure -> "That failed: ${outcome.exceptionOrNull()?.message}"
            outcome.getOrNull() == null ->
                "I couldn't get it under ${Sizes.human(targetBytes)}. Try a bigger budget."
            else -> "Done — saved to Downloads:\n${outcome.getOrNull()}"
        }
    }

    private fun saveBytes(bytes: ByteArray, outName: String, mime: String): String? {
        val target = targetUri(outName, mime) ?: return null
        return runCatching {
            contentResolver.openOutputStream(target)?.use { it.write(bytes) }
            outName
        }.getOrNull()
    }

    // ------------------------------------------------------------------ images → pdf

    private fun imagesToPdf(uri: Uri, outName: String): String? {
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val document = PdfDocument()
        val page = document.startPage(
            PdfDocument.PageInfo.Builder(ConvertPlan.pageWidth, ConvertPlan.pageHeight, 1).create()
        )
        val box = ConvertPlan.fitInside(bitmap.width, bitmap.height)
        page.canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(
                box[0].toFloat(), box[1].toFloat(),
                (box[0] + box[2]).toFloat(), (box[1] + box[3]).toFloat()
            ),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        document.finishPage(page)
        val saved = savePdf(document, outName)
        document.close()
        bitmap.recycle()
        return saved
    }

    // ------------------------------------------------------------------ text → pdf

    private fun textToPdf(uri: Uri, outName: String): String? {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
        val document = PdfDocument()
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isAntiAlias = true
        }
        val perPage = ConvertPlan.linesPerPage(fontSize = paint.textSize)
        val margin = 24f
        val leading = paint.textSize * 1.4f

        text.lines().chunked(perPage).forEachIndexed { index, lines ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(ConvertPlan.pageWidth, ConvertPlan.pageHeight, index + 1).create()
            )
            lines.forEachIndexed { row, line ->
                page.canvas.drawText(line, margin, margin + paint.textSize + row * leading, paint)
            }
            document.finishPage(page)
        }
        val saved = savePdf(document, outName)
        document.close()
        return saved
    }

    // ------------------------------------------------------------------ pdf → images

    private fun pdfToImages(uri: Uri, stem: String): String {
        val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val saved = mutableListOf<String>()
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
            PdfRenderer(descriptor).use { renderer ->
                for (page in 0 until renderer.pageCount) {
                    renderer.openPage(page).use { p ->
                        val bitmap = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        saveBitmap(bitmap, "$stem-page${page + 1}.png")?.let(saved::add)
                        bitmap.recycle()
                    }
                }
            }
        }
        return saved.joinToString(", ")
    }

    // ------------------------------------------------------------------ saving

    private fun savePdf(document: PdfDocument, outName: String): String? {
        val target = targetUri(outName, "application/pdf") ?: return null
        return runCatching {
            contentResolver.openOutputStream(target)?.use { document.writeTo(it) }
            outName
        }.onFailure { Vlog.e("Convert", "save failed: ${it.message}") }.getOrNull()
    }

    private fun saveBitmap(bitmap: Bitmap, outName: String): String? {
        val target = targetUri(outName, "image/png") ?: return null
        return runCatching {
            contentResolver.openOutputStream(target)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            outName
        }.onFailure { Vlog.e("Convert", "save failed: ${it.message}") }.getOrNull()
    }

    /** Downloads on Android 10+, the app's own folder below that. */
    private fun targetUri(name: String, mime: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } else {
            val dir = getExternalFilesDir(null) ?: filesDir
            Uri.fromFile(File(dir, name))
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
