package com.vaani.assistant.convert

/**
 * Working out what a conversion should produce, before touching any pixels.
 *
 * Android has no Office rendering engine, so this covers what the platform can actually do on its
 * own: images to PDF, text to PDF, and PDF pages back to images.
 */
object ConvertPlan {

    enum class Kind { IMAGES_TO_PDF, TEXT_TO_PDF, PDF_TO_IMAGES, UNSUPPORTED }

    fun kindFor(fileName: String): Kind {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".pdf") -> Kind.PDF_TO_IMAGES
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") -> Kind.IMAGES_TO_PDF
            lower.endsWith(".txt") || lower.endsWith(".md") -> Kind.TEXT_TO_PDF
            else -> Kind.UNSUPPORTED
        }
    }

    /** The output file name: same stem, new extension, never overwriting the input. */
    fun outputName(input: String, newExtension: String): String {
        val stem = input.substringBeforeLast('.', input)
        return "$stem.$newExtension"
    }

    /** A4 at 72 dpi, which is what PdfDocument works in. */
    val pageWidth = 595
    val pageHeight = 842

    /**
     * Where one image sits on one page, preserving aspect ratio and centring it. Returns
     * (left, top, width, height) in points.
     */
    fun fitInside(
        imageWidth: Int,
        imageHeight: Int,
        pageW: Int = pageWidth,
        pageH: Int = pageHeight,
        margin: Int = 24
    ): IntArray {
        if (imageWidth <= 0 || imageHeight <= 0) return intArrayOf(margin, margin, 1, 1)
        val availableW = (pageW - margin * 2).toFloat()
        val availableH = (pageH - margin * 2).toFloat()
        val scale = minOf(availableW / imageWidth, availableH / imageHeight)
        val w = (imageWidth * scale).toInt().coerceAtLeast(1)
        val h = (imageHeight * scale).toInt().coerceAtLeast(1)
        val left = ((pageW - w) / 2)
        val top = ((pageH - h) / 2)
        return intArrayOf(left, top, w, h)
    }

    /** How many lines of text fit on a page at a given size, allowing for leading. */
    fun linesPerPage(fontSize: Float = 11f, leading: Float = 1.4f, pageH: Int = pageHeight, margin: Int = 24): Int {
        val usable = pageH - margin * 2
        val lineHeight = fontSize * leading
        return (usable / lineHeight).toInt().coerceAtLeast(1)
    }
}
