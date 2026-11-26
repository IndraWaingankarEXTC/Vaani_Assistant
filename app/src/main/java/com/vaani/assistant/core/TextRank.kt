package com.vaani.assistant.core

/**
 * Ranking what you saved against what you asked for.
 *
 * Shared by the object memory and the voice notes, because both answer the same question: "which of
 * the things I kept is the one I mean?" Word overlap first, recency as the tie-breaker — so "where
 * are my keys" finds the entry labelled "keys" even if you saved it a month ago.
 */
object TextRank {

    fun score(query: String, text: String, ageMs: Long): Double {
        val words = Text.norm(query).split(' ').filter { it.length > 2 }
        val body = Text.norm(text)
        if (words.isEmpty() || body.isEmpty()) return 0.0

        val hits = words.count { body.contains(it) }
        if (hits == 0) {
            val close = words.count { Text.similar(it, body) }
            return if (close == 0) 0.0 else 0.35 + recency(ageMs) * 0.1
        }
        return hits.toDouble() / words.size + recency(ageMs) * 0.25
    }

    /** 1.0 for something saved minutes ago, decaying to 0 over a month. */
    fun recency(ageMs: Long): Double {
        val month = 30L * 24 * 3600 * 1000
        if (ageMs <= 0) return 1.0
        if (ageMs >= month) return 0.0
        return 1.0 - (ageMs.toDouble() / month)
    }
}
