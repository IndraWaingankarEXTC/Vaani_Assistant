package com.vaani.assistant.notify

/**
 * Turns the pile of notifications on the phone into something you can listen to.
 *
 * Not a read-out of every notification in arrival order — that is what the shade is for. This
 * groups by app, drops the noise (ongoing, silent, group summaries), and says what actually wants
 * attention.
 */
data class Notif(
    val app: String,
    val title: String,
    val text: String,
    val at: Long,
    val ongoing: Boolean = false
) {
    val line: String get() = when {
        title.isBlank() && text.isBlank() -> app
        title.isBlank() -> "$app: $text"
        text.isBlank() -> "$app: $title"
        else -> "$app: $title — $text"
    }
}

object NotificationDigest {

    /** The noisy ones: media players, downloads, "running in background". */
    private val noise = Regex(
        "(?i)\\b(playing|paused|downloading|connected|charging|usb|screenshot saved|" +
            "running in the background|battery|wifi)\\b"
    )

    fun worthTelling(items: List<Notif>): List<Notif> =
        items.filter { !it.ongoing && !noise.containsMatchIn("${it.title} ${it.text}") }

    /** One spoken paragraph: what came in, from whom, and what is most recent. */
    fun summarise(items: List<Notif>, limit: Int = 5): String {
        val useful = worthTelling(items).sortedByDescending { it.at }
        if (useful.isEmpty()) return "Nothing new needs you."
        if (useful.size == 1) return "One thing: ${clip(useful[0].line)}."

        val byApp = useful.groupBy { it.app }
        val head = if (byApp.size == 1) {
            "${useful.size} from ${useful[0].app}"
        } else {
            "${useful.size} things from ${byApp.size} apps"
        }
        val top = useful.take(limit).joinToString(". ") { clip(it.line) }
        return "$head. $top."
    }

    private fun clip(text: String, max: Int = 90): String {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        return if (clean.length <= max) clean else clean.take(max - 1).trimEnd() + "…"
    }
}
