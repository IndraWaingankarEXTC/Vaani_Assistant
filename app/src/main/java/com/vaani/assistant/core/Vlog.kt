package com.vaani.assistant.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny in-memory ring buffer log. The UI subscribes to it; nothing is written to disk,
 * so it costs no RAM beyond the last [MAX] lines.
 */
object Vlog {
    private const val MAX = 250
    private val buffer = ArrayDeque<String>(MAX)
    private val listeners = mutableListOf<(String) -> Unit>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun i(tag: String, message: String) = push("$tag: $message")
    fun e(tag: String, message: String) = push("$tag! $message")

    private fun push(line: String) {
        val stamped = "${fmt.format(Date())}  $line"
        synchronized(buffer) {
            buffer.addLast(stamped)
            while (buffer.size > MAX) buffer.removeFirst()
        }
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { runCatching { it(stamped) } }
    }

    fun addListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
    }

    fun removeListener(l: (String) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { runCatching { it("") } }
    }

    fun dump(): String = synchronized(buffer) { buffer.joinToString("\n") }
}
