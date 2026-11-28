package com.vaani.assistant.voice

/**
 * Reading a screen out loud, in pieces you can steer.
 *
 * A whole article read in one breath cannot be paused usefully, skipped, or sped up mid-way — so it
 * is split at sentence ends into chunks, and the speed steps rather than jumps.
 */
object ReadAloud {

    /** Split at sentence ends, keeping chunks under [maxChars]. */
    fun chunks(text: String, maxChars: Int = 260): List<String> {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxChars) return listOf(clean)

        val out = mutableListOf<String>()
        val buffer = StringBuilder()

        for (sentence in clean.split(Regex("(?<=[.!?])\\s+"))) {
            if (buffer.isEmpty()) {
                buffer.append(sentence)
                continue
            }
            if (buffer.length + sentence.length + 1 > maxChars) {
                out.add(buffer.toString().trim())
                buffer.setLength(0)
                buffer.append(sentence)
            } else {
                buffer.append(' ').append(sentence)
            }
        }
        if (buffer.isNotEmpty()) out.add(buffer.toString().trim())
        return out.filter { it.isNotBlank() }
    }

    /** Faster / slower, within what a speech engine can actually do. */
    fun stepSpeed(current: Float, faster: Boolean): Float {
        val next = if (faster) current * 1.25f else current / 1.25f
        return next.coerceIn(0.6f, 2.0f)
    }

    /** "next paragraph" past the end, and "back" before the start, stay put. */
    fun nextIndex(current: Int, count: Int, forward: Boolean): Int {
        if (count <= 0) return 0
        val moved = if (forward) current + 1 else current - 1
        return moved.coerceIn(0, count - 1)
    }
}
