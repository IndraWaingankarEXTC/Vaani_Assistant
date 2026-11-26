package com.vaani.assistant.core

import java.text.Normalizer

/** Normalisation and fuzzy matching used everywhere a spoken word meets a screen label. */
object Text {

    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s:.]")
    private val SPACES = Regex("\\s+")

    /** Lowercase, strip punctuation, collapse whitespace. Keeps ':' and '.' for times. */
    fun norm(s: String): String {
        val folded = Normalizer.normalize(s, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
        return folded.replace(NON_WORD, " ").replace(SPACES, " ").trim()
    }

    /** Plain Levenshtein distance, iterative, two rows. */
    fun distance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** True when [a] and [b] are the same string up to a tolerance scaled to their length. */
    fun similar(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.isBlank() || b.isBlank()) return false
        if (a.length >= 4 && b.length >= 4 && (a.contains(b) || b.contains(a))) return true
        val tolerance = maxOf(1, minOf(a.length, b.length) / 4)
        return distance(a, b) <= tolerance
    }

    /**
     * Pick the candidate that best matches [query].
     * Score = word overlap first, then normalised edit distance. Returns null when nothing
     * is close enough, so we never tap a random button.
     */
    fun bestMatch(query: String, candidates: List<String>, minScore: Double = 0.55): String? {
        val q = norm(query)
        if (q.isEmpty()) return null
        val qWords = q.split(' ').filter { it.isNotBlank() }.toSet()
        var best: String? = null
        var bestScore = 0.0
        for (c in candidates) {
            val n = norm(c)
            if (n.isEmpty()) continue
            val cWords = n.split(' ').filter { it.isNotBlank() }.toSet()
            val overlap = if (qWords.isEmpty() || cWords.isEmpty()) 0.0
            else qWords.intersect(cWords).size.toDouble() / qWords.size
            val maxLen = maxOf(q.length, n.length)
            val edit = if (maxLen == 0) 1.0 else 1.0 - distance(q, n).toDouble() / maxLen
            val score = 0.6 * overlap + 0.4 * edit
            if (score > bestScore) { bestScore = score; best = c }
        }
        return if (bestScore >= minScore) best else null
    }

    /** Strip filler words a speech recogniser likes to prepend. */
    private val FILLERS = listOf(
        "hey vaani", "hi vaani", "ok vaani", "vaani", "please", "can you", "could you",
        "would you", "i want you to", "i want to", "for me"
    )

    fun stripFillers(s: String): String {
        var out = norm(s)
        for (f in FILLERS) {
            if (out == f) return ""
            if (out.startsWith("$f ")) out = out.removePrefix("$f ")
            if (out.endsWith(" $f")) out = out.removeSuffix(" $f")
        }
        return out.trim()
    }

    fun numberWord(s: String): Int? = when (norm(s)) {
        "zero", "oh" -> 0
        "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5
        "six" -> 6; "seven" -> 7; "eight" -> 8; "nine" -> 9; "ten" -> 10
        "eleven" -> 11; "twelve" -> 12; "thirteen" -> 13; "fourteen" -> 14
        "fifteen" -> 15; "sixteen" -> 16; "seventeen" -> 17; "eighteen" -> 18
        "nineteen" -> 19; "twenty" -> 20; "thirty" -> 30; "forty" -> 40
        "fifty" -> 50; "sixty" -> 60
        else -> null
    }
}
