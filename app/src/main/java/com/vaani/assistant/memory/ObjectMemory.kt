package com.vaani.assistant.memory

import android.content.Context
import com.vaani.assistant.core.TextRank
import com.vaani.assistant.core.Vlog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Where you left things.
 *
 * Point the lens at your keys, say "remember these are my keys", and later ask "where are my keys?"
 * — it answers with the label, the time, and the picture it took. One JPEG per entry in the app's
 * own directory; nothing is uploaded.
 */
class ObjectMemory(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "objects.json")
    private val images = File(appContext.filesDir, "objects").apply { mkdirs() }
    private val lock = Any()

    data class Remembered(val label: String, val note: String?, val at: Long, val imagePath: String?)

    fun remember(label: String, note: String?, jpeg: ByteArray?): Boolean {
        val clean = label.trim()
        if (clean.isEmpty()) return false
        val path = if (jpeg != null) writeImage(jpeg) else null
        synchronized(lock) {
            val all = read().toMutableList()
            all.add(0, Remembered(clean, note?.trim()?.ifBlank { null }, System.currentTimeMillis(), path))
            write(all.take(MAX))
        }
        return true
    }

    fun find(query: String): Remembered? = rank(query, read()).firstOrNull()

    fun all(): List<Remembered> = read()

    fun clear(): Boolean = synchronized(lock) {
        images.listFiles()?.forEach { it.delete() }
        file.delete()
    }

    private fun writeImage(jpeg: ByteArray): String? = runCatching {
        val target = File(images, "obj-${System.currentTimeMillis()}.jpg")
        target.writeBytes(jpeg)
        target.absolutePath
    }.onFailure { Vlog.e("Memory", "image failed: ${it.message}") }.getOrNull()

    private fun read(): List<Remembered> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Remembered(
                    label = o.optString("label"),
                    note = o.optString("note").ifBlank { null },
                    at = o.optLong("at"),
                    imagePath = o.optString("image").ifBlank { null }
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun write(entries: List<Remembered>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject().put("label", e.label).put("note", e.note ?: "")
                        .put("at", e.at).put("image", e.imagePath ?: "")
                )
            }
            file.writeText(array.toString())
        }.onFailure { Vlog.e("Memory", "save failed: ${it.message}") }
    }

    companion object {
        private const val MAX = 100

        /** Pure ranking, so the matching can be tested without a phone. */
        fun rank(
            query: String,
            items: List<Remembered>,
            now: Long = System.currentTimeMillis()
        ): List<Remembered> = items
            .map { it to TextRank.score(query, "${it.label} ${it.note ?: ""}", now - it.at) }
            .filter { it.second > 0.2 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
