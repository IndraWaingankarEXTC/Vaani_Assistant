package com.vaani.assistant.voice

/**
 * Turns a dictated sentence into written text.
 *
 * Speech recognisers hand back a bare stream of words: "meeting moved to friday can you send the
 * deck". Typed like that it reads badly. This adds the punctuation a person would have written —
 * from spoken markers ("comma", "full stop", "new line") and, when asked, a closing full stop so
 * each dictation lands as a sentence rather than a fragment.
 */
object Punctuation {

    private data class Mark(val phrase: String, val symbol: String)

    /** Longest phrases first: "new paragraph" must win before "new line" sees it. */
    private val marks = listOf(
        Mark("new paragraph", "\n\n"),
        Mark("naya paragraph", "\n\n"),
        Mark("new line", "\n"),
        Mark("next line", "\n"),
        Mark("nayi line", "\n"),
        Mark("naya line", "\n"),
        Mark("full stop", "."),
        Mark("purna viram", "."),
        Mark("exclamation mark", "!"),
        Mark("exclamation point", "!"),
        Mark("question mark", "?"),
        Mark("prashn chinha", "?"),
        Mark("open bracket", "("),
        Mark("close bracket", ")"),
        Mark("open quote", "\""),
        Mark("close quote", "\""),
        Mark("semicolon", ";"),
        Mark("colon", ":"),
        Mark("comma", ","),
        Mark("alpa viram", ","),
        Mark("hyphen", "-"),
        Mark("dash", "—"),
        Mark("underscore", "_"),
        Mark("at the rate", "@"),
        Mark("forward slash", "/"),
        Mark("asterisk", "*"),
        Mark("percent", "%"),
        Mark("period", ".")
    )

    private val SPACES = Regex("[ \\t]+")
    private val SPACE_BEFORE = Regex(" +([,.!?;:\\)\\]])")
    private val SPACE_AFTER_PAREN = Regex("([\\(\\[]) +")
    // A space goes after , ; : ! ? when a word follows — but never after a full stop, because
    // "gmail.com" and "3.5" must stay intact. Spoken marks already arrive space-separated.
    private val SPACE_BEFORE_WORD = Regex("([,;:!?])(?=[A-Za-z])")
    private val NEWLINE_SPACES = Regex(" *\\n *")
    private val STANDALONE_I = Regex("\\bi\\b")

    /**
     * @param autoStop append a full stop when the sentence has no terminal punctuation.
     */
    fun render(raw: String, autoStop: Boolean = true): String {
        if (raw.isBlank()) return ""
        var s = " ${raw.trim()} "
        for (m in marks) {
            val rx = Regex("(?i)\\b" + Regex.escape(m.phrase) + "\\b")
            s = rx.replace(s) { " ${m.symbol} " }
        }
        s = NEWLINE_SPACES.replace(s, "\n")
        s = SPACES.replace(s, " ")
        s = SPACE_BEFORE.replace(s, "$1")
        s = SPACE_AFTER_PAREN.replace(s, "$1")
        s = SPACE_BEFORE_WORD.replace(s, "$1 ")
        s = s.replace(Regex("\\n +"), "\n").trim()
        if (s.isEmpty()) return ""
        s = capitalise(s)
        s = STANDALONE_I.replace(s, "I")
        if (autoStop && s.last() !in ".!?:;,\n") s += "."
        return s
    }

    /**
     * Upper-case the first letter and the first letter after each sentence end.
     *
     * Two things it must not do: capitalise inside an address ("indra@gmail.com" must not become
     * "Indra@Gmail.Com"), and treat the dot in a domain or a decimal as a sentence end.
     */
    private fun capitalise(input: String): String {
        val sb = StringBuilder(input)
        var upNext = true
        for (i in sb.indices) {
            val c = sb[i]
            when {
                upNext && c.isLetter() -> {
                    if (!insideAddress(sb, i)) sb.setCharAt(i, c.uppercaseChar())
                    upNext = false
                }
                c == '!' || c == '?' || c == '\n' -> upNext = true
                // A dot only ends a sentence when a space or the end of the text follows it.
                c == '.' -> upNext = i + 1 >= sb.length || sb[i + 1].isWhitespace()
                !c.isWhitespace() -> upNext = false
            }
        }
        return sb.toString()
    }

    /** Is this position inside something that looks like an email address or a URL? */
    private fun insideAddress(sb: StringBuilder, index: Int): Boolean {
        var start = index
        while (start > 0 && !sb[start - 1].isWhitespace()) start--
        var end = index
        while (end < sb.length && !sb[end].isWhitespace()) end++
        if (start >= end) return false
        val token = sb.substring(start, end)
        return token.contains('@') || token.contains("://") || token.startsWith("www.")
    }
}
