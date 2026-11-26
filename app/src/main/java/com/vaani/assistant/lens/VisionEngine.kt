package com.vaani.assistant.lens

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * On-device vision: image labels plus text. Both models ship inside the APK, so the lens works in
 * a basement with no signal — which is the only way an offline assistant can honestly offer this.
 *
 * Nothing here talks to a server, and no frame is written to disk.
 */
class VisionEngine {

    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.4f)
                .build()
        )
    }

    private val reader: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Identify whatever is inside [bitmap]: the labels say what kind of thing it is, the text says
     * which one. Returns on the main thread.
     */
    fun read(
        bitmap: Bitmap,
        onResult: (Reading) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val image = InputImage.fromBitmap(bitmap, 0)
        var labels: List<Pair<String, Float>> = emptyList()
        var lines: List<String> = emptyList()
        var pending = 2
        var failed: String? = null

        fun finish() {
            pending--
            if (pending > 0) return
            if (failed != null) {
                onFailure(failed!!)
            } else {
                onResult(DeviceReading.compose(labels, lines))
            }
        }

        labeler.process(image)
            .addOnSuccessListener { results ->
                labels = results.map { it.text to it.confidence }.sortedByDescending { it.second }
            }
            .addOnFailureListener { failed = it.message ?: "labelling failed" }
            .addOnCompleteListener { finish() }

        reader.process(image)
            .addOnSuccessListener { text ->
                lines = text.textBlocks.flatMap { block ->
                    block.lines.map { it.text }
                }.ifEmpty { text.text.split('\n') }
            }
            .addOnFailureListener { if (failed == null) failed = it.message ?: "reading failed" }
            .addOnCompleteListener { finish() }
    }

    fun close() {
        runCatching { labeler.close() }
        runCatching { reader.close() }
    }
}
