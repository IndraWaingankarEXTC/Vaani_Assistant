package com.vaani.assistant.nlu

import com.vaani.assistant.core.Text
import java.util.Calendar

/** Turns spoken time and duration phrases into numbers. No external dependency. */
object TimeParser {

    data class Clock(val hour: Int, val minute: Int)

    private val NOISE = Regex("\\b(at|around|about|for|by|o clock|oclock|sharp|set|baje|bje|tomorrow|today|tonight|in the|the)\\b")

    fun parseClock(raw: String): Clock? {
        val original = Text.norm(raw)
        val pm = Regex("\\b(pm|p m|evening|night|afternoon)\\b").containsMatchIn(original)
        val am = Regex("\\b(am|a m|morning)\\b").containsMatchIn(original)
        val s = original.replace(NOISE, " ").replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return null

        // "half past six", "quarter to eight"
        Regex("(half|quarter) (past|to) ([a-z0-9]+)").find(s)?.let { m ->
            val base = wordOrDigit(m.groupValues[3]) ?: return null
            var h = base
            var mins = if (m.groupValues[1] == "half") 30 else 15
            if (m.groupValues[2] == "to") {
                h -= 1
                mins = 60 - mins
            }
            return normalise(h, mins, pm, am)
        }

        // "7:30", "07.30"
        Regex("(\\d{1,2})[:.](\\d{2})").find(s)?.let { m ->
            return normalise(m.groupValues[1].toInt(), m.groupValues[2].toInt(), pm, am)
        }

        // "seven thirty", "seven"
        val words = s.split(' ').filter { it.isNotBlank() }
        if (words.isNotEmpty()) {
            val h = wordOrDigit(words[0])
            if (h != null && h in 0..23) {
                val mins = if (words.size > 1) wordOrDigit(words[1]) ?: 0 else 0
                if (mins in 0..59) return normalise(h, mins, pm, am)
            }
        }
        return null
    }

    private fun normalise(hourIn: Int, minute: Int, pm: Boolean, am: Boolean): Clock? {
        if (minute !in 0..59) return null
        var h = hourIn
        if (pm && h in 1..11) h += 12
        if (am && h == 12) h = 0
        if (h !in 0..23) return null
        return Clock(h, minute)
    }

    private fun wordOrDigit(s: String): Int? {
        val cleaned = s.replace(Regex("[^a-z0-9]"), "")
        if (cleaned.isEmpty()) return null
        cleaned.toIntOrNull()?.let { return it }
        return Text.numberWord(cleaned)
    }

    private val UNIT = Regex("(\\d+)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b")

    fun parseDurationSeconds(raw: String): Int? {
        val s = Text.norm(raw)
        var total = 0
        UNIT.findAll(s).forEach { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@forEach
            total += when (m.groupValues[2].first()) {
                'h' -> n * 3600
                'm' -> n * 60
                else -> n
            }
        }
        if (total > 0) return total

        // word forms: "ten minutes", "half an hour", "an hour"
        Regex("([a-z]+)\\s*(hours?|minutes?|seconds?)").findAll(s).forEach { m ->
            val n = Text.numberWord(m.groupValues[1]) ?: return@forEach
            total += when (m.groupValues[2].first()) {
                'h' -> n * 3600
                'm' -> n * 60
                else -> n
            }
        }
        if (s.contains("half an hour") || s.contains("half hour")) total += 1800
        if (total > 0) return total
        if (Regex("\\ban hour\\b|\\bone hour\\b").containsMatchIn(s)) return 3600
        if (Regex("\\ba minute\\b|\\bone minute\\b").containsMatchIn(s)) return 60
        return null
    }

    /** Calendar for "now + seconds", used when an alarm is given as a duration. */
    fun clockFromNow(seconds: Int): Clock {
        val c = Calendar.getInstance()
        c.add(Calendar.SECOND, seconds)
        return Clock(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
