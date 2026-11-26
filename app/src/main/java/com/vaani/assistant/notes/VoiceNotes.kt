package com.vaani.assistant.notes

import android.content.Context
import com.vaani.assistant.core.TextRank
import com.vaani.assistant.core.Vlog
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Things you said to remember.
 *
 * Each note keeps the words (transcribed on the device) and, when there is one, the recording — so a
 * note is found by what was said rather than by when it was made.
 */
class VoiceNotes(context: Context) {

    private val file = File(context.applicationContext.filesDir, "notes.json")
    private val lock = Any()

    data class Note(val text: String, val at: Long, val audioPath: String?)

    fun add(text: String, audioPath: String?): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        synchronized(lock) {
            val all = read().toMutableList()
            all.add(0, Note(clean, System.currentTimeMillis(), audioPath))
            write(all.take(MAX))
        }
        return true
    }

    fun search(query: String): List<Note> = rank(query, read())

    fun latest(count: Int = 3): List<Note> = read().take(count)

    fun count(): Int = read().size

    fun clear(): Boolean = synchronized(lock) { file.delete() }

    private fun read(): List<Note> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Note(
                    text = o.optString("text"),
                    at = o.optLong("at"),
                    audioPath = o.optString("audio").ifBlank { null }
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun write(notes: List<Note>) {
        runCatching {
            val array = JSONArray()
            notes.forEach { n ->
                array.put(JSONObject().put("text", n.text).put("at", n.at).put("audio", n.audioPath ?: ""))
            }
            file.writeText(array.toString())
        }.onFailure { Vlog.e("Notes", "save failed: ${it.message}") }
    }

    companion object {
        private const val MAX = 500

        fun rank(query: String, notes: List<Note>, now: Long = System.currentTimeMillis()): List<Note> =
            notes
                .map { it to TextRank.score(query, it.text, now - it.at) }
                .filter { it.second > 0.2 }
                .sortedByDescending { it.second }
                .map { it.first }
    }
}
