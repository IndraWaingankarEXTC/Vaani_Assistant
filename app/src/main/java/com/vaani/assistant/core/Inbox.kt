package com.vaani.assistant.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A local inbox for things you grab in a hurry: a link you copied, a note you dictated, a phone
 * number someone read out. It never leaves the phone — a file in the app's own directory.
 */
class Inbox(context: Context) {

    private val file = File(context.filesDir, "inbox.json")
    private val lock = Any()

    data class Entry(val text: String, val at: Long, val kind: String)

    fun add(text: String, kind: String = "note"): Boolean {
        val clean = text.trim()
        if (clean.isEmpty()) return false
        synchronized(lock) {
            val all = read().toMutableList()
            all.add(0, Entry(clean, System.currentTimeMillis(), kind))
            write(all.take(MAX))
        }
        return true
    }

    fun all(): List<Entry> = synchronized(lock) { read() }

    fun latest(): Entry? = all().firstOrNull()

    fun count(): Int = all().size

    fun clear(): Boolean = synchronized(lock) { file.delete() }

    private fun read(): List<Entry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                Entry(
                    text = o.optString("text"),
                    at = o.optLong("at"),
                    kind = o.optString("kind", "note")
                )
            }
        }.getOrElse {
            Vlog.e("Inbox", "unreadable inbox: ${it.message}")
            emptyList()
        }
    }

    private fun write(entries: List<Entry>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { e ->
                array.put(
                    JSONObject()
                        .put("text", e.text)
                        .put("at", e.at)
                        .put("kind", e.kind)
                )
            }
            file.writeText(array.toString())
        }.onFailure { Vlog.e("Inbox", "could not save: ${it.message}") }
    }

    private companion object {
        const val MAX = 200
    }
}
