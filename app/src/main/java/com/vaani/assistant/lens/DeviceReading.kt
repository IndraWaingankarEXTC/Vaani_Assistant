package com.vaani.assistant.lens

/**
 * Turning "some labels and some text" into "that's a Sony WH-CH720N".
 *
 * Generic image labels say "headphones" and stop there. The accuracy comes from the thing every
 * device carries and no classifier can guess: its own printed model number. So the two are
 * combined — labels give the category, OCR gives the identity — and the result says which of the
 * two it leaned on.
 *
 * Pure logic on purpose: no Android, no camera, so it can be tested.
 */
data class Reading(
    val title: String,
    val brand: String?,
    val model: String?,
    val category: String?,
    val details: List<String>,
    val confidence: Float,
    val usedText: Boolean
) {

    /** What Vaani says out loud. Short: this is read while the user is still holding the phone up. */
    fun spoken(): String {
        if (title == UNKNOWN) {
            return "I can't identify it from this angle. Try filling the frame with the device, " +
                "or put the label inside the box."
        }
        val lead = "That looks like a $title"
        val categoryBit = category?.let { ", $it" }?.replace(", $title", "") ?: ""
        val certainty = when {
            confidence >= 0.75f -> ""
            confidence >= 0.45f -> ". I'm fairly sure"
            else -> ". I'm guessing, honestly"
        }
        val source = if (usedText) " I read the model number off the label." else ""
        return "$lead$categoryBit$certainty.$source"
    }

    fun bullets(): List<String> = buildList {
        add("Looks like · $title")
        brand?.let { add("Brand · $it") }
        model?.let { add("Model · $it") }
        category?.let { add("Type · $it") }
        if (details.isNotEmpty()) add("Reads as · ${details.joinToString(", ")}")
        add("Confidence · ${(confidence * 100).toInt()}%" + if (usedText) " (from the label)" else " (from shape only)")
    }

    companion object {
        const val UNKNOWN = "something I can't name yet"
    }
}

object DeviceReading {

    private val brands = setOf(
        "sony", "samsung", "apple", "xiaomi", "redmi", "realme", "oneplus", "oppo", "vivo",
        "nothing", "google", "motorola", "nokia", "asus", "acer", "lenovo", "dell", "hp",
        "micromax", "lava", "infinix", "tecno", "poco", "iqoo", "boat", "noise", "jbl", "bose",
        "sennheiser", "philips", "panasonic", "lg", "haier", "whirlpool", "voltas", "godrej",
        "canon", "nikon", "fujifilm", "gopro", "dji", "anker", "belkin", "logitech", "zebronics",
        "fire-boltt", "fastrack", "titan", "mi", "amazon", "echo", "fire", "kindle", "garmin",
        "fitbit", "tplink", "tp-link", "dlink", "d-link", "netgear", "mi", "wipro", "bajaj"
    )

    /** Units and specs that look like model numbers but are not. */
    private val unitToken = Regex(
        "(?i)^\\d+\\s?(mah|v|w|a|hz|khz|ghz|gb|tb|mb|mp|mm|cm|km|kg|g|ml|l|in|inch|p|nits|rpm)$"
    )
    private val yearLike = Regex("^(19|20)\\d{2}$")
    private val hasLetterAndDigit = Regex("^(?=.*[A-Za-z])(?=.*\\d).+$")

    /**
     * @param labels image labels with confidence, best first, from the on-device labeller
     * @param lines text found inside the box, one string per OCR line
     */
    fun compose(labels: List<Pair<String, Float>>, lines: List<String>): Reading {
        val cleaned = lines.map { it.trim() }.filter { it.isNotBlank() }
        val brand = findBrand(cleaned)
        val model = findModel(cleaned)
        val category = labels.maxByOrNull { it.second }?.first?.lowercase()

        val title = when {
            brand != null && model != null -> "${brand.titleCase()} $model"
            model != null -> model
            brand != null && category != null -> "${brand.titleCase()} $category"
            category != null -> category.titleCase()
            else -> Reading.UNKNOWN
        }

        val base = labels.maxOfOrNull { it.second } ?: 0f
        var confidence = base
        if (model != null) confidence += 0.25f
        if (brand != null) confidence += 0.10f
        if (title == Reading.UNKNOWN) confidence = 0f
        confidence = confidence.coerceIn(0f, 0.99f)

        return Reading(
            title = title,
            brand = brand?.titleCase(),
            model = model,
            category = category?.titleCase(),
            details = labels.drop(1).take(3).map { it.first.lowercase() },
            confidence = confidence,
            usedText = model != null || brand != null
        )
    }

    /** A brand is a known name appearing as its own word anywhere in the text. */
    fun findBrand(lines: List<String>): String? {
        for (line in lines) {
            val words = line.split(' ', '-', '_', '/', '(', ')').filter { it.isNotBlank() }
            for (word in words) {
                val w = word.lowercase().trim('.', ',', ':')
                if (w in brands) return w
            }
        }
        return null
    }

    /**
     * The best model-number candidate in the text.
     *
     * Scored rather than regexed, because device labels are messy: "WH-CH720N" and "SM-A546E" are
     * obvious, but so is "Airdopes 141", and "1000mAh" is not a model at all.
     */
    fun findModel(lines: List<String>): String? {
        var best: String? = null
        var bestScore = 0

        for (line in lines) {
            for (raw in candidates(line)) {
                val token = raw.trim('.', ',', ';', ':', '"', '\'')
                val score = score(token)
                if (score > bestScore) {
                    bestScore = score
                    best = token
                }
            }
        }
        return if (bestScore >= 4) best else null
    }

    /** Single words plus hyphen-joined pairs, so "WH-CH720N" survives as one candidate. */
    private fun candidates(line: String): List<String> {
        val words = line.split(' ').filter { it.isNotBlank() }
        val out = words.toMutableList()
        for (i in 0 until words.size - 1) {
            if (words[i].endsWith("-") || words[i + 1].startsWith("-")) {
                out.add(words[i] + words[i + 1])
            }
        }
        return out
    }

    fun score(token: String): Int {
        if (token.length < 4 || token.length > 22) return 0
        if (!hasLetterAndDigit.matches(token)) return 0
        if (unitToken.matches(token)) return 0
        if (yearLike.matches(token)) return 0

        var score = 2
        if (token.contains('-')) score += 3
        if (token.length in 6..16) score += 2
        if (token.any { it.isUpperCase() } && token.any { it.isDigit() }) score += 1
        if (token.contains('.')) score -= 2          // version numbers, not models
        if (token.count { it.isDigit() } > token.count { it.isLetter() }) score -= 1
        return score
    }

    private fun String.titleCase(): String =
        split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word[0].uppercaseChar() + word.drop(1).lowercase()
        }
}
