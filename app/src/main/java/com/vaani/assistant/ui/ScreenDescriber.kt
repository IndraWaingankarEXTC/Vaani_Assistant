package com.vaani.assistant.ui

import com.vaani.assistant.core.Text

/**
 * Describes what is on screen in a sentence, on demand.
 *
 * Deliberately not a screen reader. A screen reader walks every element with a focus cursor and
 * reads it in order — correct for someone navigating blind, exhausting for someone who just wants
 * to know what they are looking at. This answers one question: *what is here?* — the heading, the
 * things I can press, and how much text there is.
 */
object ScreenDescriber {

    fun describe(nodes: List<Box>, appLabel: String?): String {
        val labelled = nodes.filter { it.label.isNotBlank() }
        if (labelled.isEmpty()) {
            return if (appLabel != null) {
                "$appLabel is on screen, but it is not showing me anything I can read."
            } else {
                "I can't read anything on this screen."
            }
        }

        val title = heading(labelled)
        val actions = labelled.filter { it.clickable && !it.editable }
            .map { short(it.label) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)
        val fields = labelled.count { it.editable }
        val bodyText = labelled.count { !it.clickable && !it.editable && it.label.length > 40 }

        val parts = mutableListOf<String>()
        if (appLabel != null) parts += "This is $appLabel."
        if (title != null) parts += "It says ${short(title)}."
        if (actions.isNotEmpty()) {
            parts += "You can press: ${actions.joinToString(", ")}."
        }
        if (fields > 0) {
            parts += if (fields == 1) "There is one text field." else "There are $fields text fields."
        }
        if (bodyText > 2) parts += "There is a fair amount of text below."

        if (parts.size <= 1 && appLabel != null) {
            parts += "I can see ${labelled.size} elements, but nothing that stands out."
        }
        return parts.joinToString(" ")
    }

    /** The most title-like thing near the top: long enough to be a heading, short enough not to be prose. */
    private fun heading(nodes: List<Box>): String? =
        nodes.filter { node ->
            val n = Text.norm(node.label)
            node.label.length in 4..60 &&
                n.any { it.isLetter() } &&
                !node.editable &&
                !Regex("^(ok|cancel|back|next|done|close|skip|allow|deny)$").matches(n)
        }
            .minByOrNull { it.top }
            ?.label
            ?.trim()

    private fun short(label: String, max: Int = 40): String {
        val clean = label.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}
