package com.vaani.assistant.nlu

/**
 * "the third one", "second from the top", "the last one".
 *
 * Hands-free use hits this constantly: a list of search results, a row of buttons, a stack of
 * messages. Saying "tap the third one" is faster than reading every label aloud — but only if the
 * number is understood exactly, so this parses words, digits, ordinals and position phrases.
 */
object Ordinals {

    /** Index into a list of things on screen; 0-based. Null when nothing numeric was said. */
    fun parse(text: String): Int? {
        val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null

        for (word in words) {
            wordNumber(word)?.let { return it }
        }
        // "3rd", "21st", "2nd"
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)\\b").find(text)?.let {
            return it.groupValues[1].toIntOrNull()?.minus(1)
        }
        // A bare number: "tap 1", "number 5".
        return fromDigit(text)
    }

    /** True when the phrase points at the end of the list rather than a number. */
    fun isLast(text: String): Boolean {
        val t = text.lowercase()
        return Regex("\\b(last|bottom|final|lowest)\\b").containsMatchIn(t)
    }

    fun isFirst(text: String): Boolean {
        val t = text.lowercase()
        return Regex("\\b(first|top|uppermost|beginning)\\b").containsMatchIn(t)
    }

    /** "from the bottom" flips the counting direction. */
    fun countsFromBottom(text: String): Boolean =
        Regex("\\b(from the bottom|bottom up|upside down)\\b").containsMatchIn(text.lowercase())

    private fun wordNumber(word: String): Int? = when (word.trim('.', ',', ':')) {
        "first", "one" -> 0
        "second", "two" -> 1
        "third", "three" -> 2
        "fourth", "four" -> 3
        "fifth", "five" -> 4
        "sixth", "six" -> 5
        "seventh", "seven" -> 6
        "eighth", "eight" -> 7
        "ninth", "nine" -> 8
        "tenth", "ten" -> 9
        "eleventh", "eleven" -> 10
        "twelfth", "twelve" -> 11
        else -> null
    }

    /** Turn a 1-based digit the user said into an index, or null. */
    fun fromDigit(text: String): Int? {
        val match = Regex("\\b(\\d{1,2})\\b").find(text) ?: return null
        val n = match.groupValues[1].toIntOrNull() ?: return null
        return if (n >= 1) n - 1 else null
    }
}
