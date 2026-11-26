package com.vaani.assistant.nlu

import com.vaani.assistant.core.Text

/**
 * Per-utterance language picture, used for two things only: which language tag the recogniser
 * should hint next time, and which voice the reply should come out in.
 *
 * It never gates the grammar — [CodeMixLexicon] has already flattened the sentence to canonical
 * tokens, so a command works the same whether it arrived in English, Hindi, Tamil or all three.
 */
object LanguageDetector {

    data class LanguageMix(
        val primary: String,
        val languages: List<String>,
        val mixed: Boolean,
        val confidence: Float
    ) {
        /** BCP-47 tag for the recogniser and the TTS engine. */
        fun tag(): String = when (primary) {
            "hi", "ur" -> "hi-IN"
            "mr" -> "mr-IN"
            "bn" -> "bn-IN"
            "ta" -> "ta-IN"
            "te" -> "te-IN"
            "kn" -> "kn-IN"
            "ml" -> "ml-IN"
            "gu" -> "gu-IN"
            "pa" -> "pa-IN"
            else -> "en-IN"
        }
    }

    fun detect(input: String): LanguageMix {
        val hits = CodeMixLexicon.languageHits(input)
            .filterKeys { it != "num" }
            .toList()
            .sortedByDescending { it.second }

        if (hits.isEmpty()) return LanguageMix("en", listOf("en"), false, 0.2f)

        val total = hits.sumOf { it.second }.coerceAtLeast(1)
        val primary = hits.first().first
        val languages = hits.map { it.first }
        val englishish = languages.contains("en")
        val indian = languages.any { it != "en" }
        return LanguageMix(
            primary = primary,
            languages = languages,
            mixed = englishish && indian,
            confidence = hits.first().second.toFloat() / total
        )
    }

    /** True when the text is mostly a non-English Indian language. */
    fun isIndianDominant(mix: LanguageMix): Boolean = mix.primary != "en" && mix.confidence > 0.5f

    fun describe(input: String): String {
        val m = detect(input)
        val words = Text.norm(input).split(' ').size
        return "${m.primary}${if (m.mixed) "+mixed" else ""} (${m.languages.joinToString("/")}, $words words)"
    }
}
