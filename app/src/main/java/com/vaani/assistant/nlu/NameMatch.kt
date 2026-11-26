package com.vaani.assistant.nlu

import com.vaani.assistant.core.Text
import kotlin.math.abs

/**
 * Matching a spoken name to a contact, when the two do not agree on punctuation.
 *
 * Speech gives "OBrien" or "o brien"; the phone book says "O'Brien". Speech gives "DSouza"; the
 * phone book says "D'Souza". Normalising punctuation alone is not enough, because it leaves a
 * space where the apostrophe was — so the comparison also drops spaces entirely.
 */
object NameMatch {

    /** Punctuation and spacing removed: "O'Brien" and "OBrien" both become "obrien". */
    fun key(raw: String): String = Text.norm(raw).replace(" ", "")

    fun matches(spoken: String, display: String): Boolean {
        val a = key(spoken)
        val b = key(display)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        // "ram" against "ramkumar", or the reverse, but not "ra" against "ramkumar".
        if (a.length >= 3 && b.startsWith(a) && a.length >= b.length - 2) return true
        if (b.length >= 3 && a.startsWith(b) && b.length >= a.length - 2) return true
        val tolerance = maxOf(1, minOf(a.length, b.length) / 5)
        return Text.distance(a, b) <= tolerance
    }

    /** The best display name for what was said, or null if nothing is close. */
    fun best(spoken: String, candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val exact = candidates.firstOrNull { key(it) == key(spoken) }
        if (exact != null) return exact
        val near = candidates.filter { matches(spoken, it) }
            .minByOrNull { abs(key(it).length - key(spoken).length) }
        if (near != null) return near
        return Text.bestMatch(Text.norm(spoken), candidates, 0.6)
    }

    /**
     * A fragment safe to use in a SQL LIKE prefilter. Apostrophes are dropped because the contact
     * store keeps them: "O'Brien" is matched by looking for "brien".
     */
    fun queryToken(spoken: String): String {
        val words = Text.norm(spoken).split(' ')
            .filter { it.length >= 3 }
            .sortedByDescending { it.length }
        return words.firstOrNull() ?: Text.norm(spoken).replace(" ", "")
    }
}
