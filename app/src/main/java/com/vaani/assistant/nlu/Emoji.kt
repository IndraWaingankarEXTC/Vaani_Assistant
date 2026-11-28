package com.vaani.assistant.nlu

import com.vaani.assistant.core.Text

/**
 * "send a heart emoji" — spoken names mapped to the character.
 *
 * Typing an emoji by voice is otherwise impossible: the keyboard picker cannot be driven through
 * the accessibility tree reliably, so the character is inserted directly into the message field.
 */
object Emoji {

    private val names: Map<String, String> = mapOf(
        "heart" to "\u2764\uFE0F", "love" to "\u2764\uFE0F", "red heart" to "\u2764\uFE0F",
        "smile" to "\uD83D\uDE0A", "smiley" to "\uD83D\uDE0A", "happy" to "\uD83D\uDE0A",
        "laugh" to "\uD83D\uDE02", "laughing" to "\uD83D\uDE02", "lol" to "\uD83D\uDE02",
        "thumbs up" to "\uD83D\uDC4D", "thumbsup" to "\uD83D\uDC4D", "like" to "\uD83D\uDC4D",
        "thumbs down" to "\uD83D\uDC4E", "dislike" to "\uD83D\uDC4E",
        "fire" to "\uD83D\uDD25", "lit" to "\uD83D\uDD25",
        "star" to "\u2B50", "cry" to "\uD83D\uDE22", "sad" to "\uD83D\uDE14",
        "angry" to "\uD83D\uDE20", "kiss" to "\uD83D\uDE18", "party" to "\uD83C\uDF89",
        "ok" to "\uD83D\uDC4C", "okay" to "\uD83D\uDC4C", "pray" to "\uD83D\uDE4F",
        "clap" to "\uD83D\uDC4F", "wink" to "\uD83D\uDE09", "cool" to "\uD83D\uDE0E",
        "wave" to "\uD83D\uDC4B", "hello" to "\uD83D\uDC4B",
        "check" to "\u2705", "tick" to "\u2705", "cross" to "\u274C",
        "flower" to "\uD83C\uDF38", "sun" to "\u2600\uFE0F", "moon" to "\uD83C\uDF19",
        "gift" to "\uD83C\uDF81", "cake" to "\uD83C\uDF82", "coffee" to "\u2615",
        "phone" to "\uD83D\uDCF1", "money" to "\uD83D\uDCB0", "time" to "\u23F0",
        "home" to "\uD83C\uDFE0", "rocket" to "\uD83D\uDE80", "100" to "\uD83D\uDCAF"
    )

    /** The character for a spoken name, or null when it is not one we know. */
    fun character(name: String): String? {
        val key = Text.norm(name).trim()
        if (key.isEmpty()) return null
        names[key]?.let { return it }
        return names[key.replace(" ", "")]
    }

    fun allNames(): List<String> = names.keys.sorted()
}
