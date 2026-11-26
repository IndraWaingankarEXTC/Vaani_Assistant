package com.vaani.assistant.exec

import com.vaani.assistant.core.Text
import com.vaani.assistant.ui.Box

/**
 * Reads the checkout screen and pulls out the handful of facts a person actually checks before
 * spending money: what it is, which variant, how much, when it arrives.
 *
 * Deliberately short. A wall of text is worse than no summary, because nobody reads it — the
 * whole point is that the bullets are skimmable in two seconds.
 *
 * Everything here comes from whatever the app chose to expose to the accessibility tree, so it
 * can be incomplete or wrong. That is exactly why a human signs off on it.
 */
data class ScreenSummary(
    val appLabel: String,
    val product: String?,
    val specs: List<String>,
    val price: String?,
    val total: String?,
    val delivery: String?,
    val payment: String?,
    val bullets: List<String>
) {

    /**
     * The facts alone, for speech. The sentence around them is built by [Reply] so it comes out
     * in whichever language the user just spoke.
     */
    fun facts(): String {
        val parts = mutableListOf<String>()
        product?.let { parts += it }
        specs.firstOrNull()?.let { parts += it }
        (total ?: price)?.let { parts += it }
        delivery?.let { parts += it }
        return if (parts.isEmpty()) bullets.take(3).joinToString(", ") else parts.joinToString(", ")
    }

    companion object {

        private val PRICE = Regex("(?:₹|Rs\\.?|INR|\\$|€|£)\\s?[\\d,]+(?:\\.\\d{1,2})?")
        private val QTY = Regex("(?i)\\b(?:qty|quantity)\\s*[:\\-]?\\s*\\d+")
        private val DIMENSION = Regex(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s?(?:ml|l|litre|litres|g|kg|cm|mm|inch|inches|in|ft|gb|tb|mah|w|watt|pack|pcs|pieces|count)\\b"
        )
        private val DATE = Regex(
            "(?i)\\b(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*,?\\s*\\d{1,2}\\s*[a-z]{3,9}" +
                "|\\b\\d{1,2}\\s*(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*" +
                "|\\b(?:tomorrow|today|day after tomorrow)\\b"
        )
        private val DELIVERY = Regex("(?i)\\b(arriv\\w*|deliver\\w*|get it by|by \\w+day)\\b")
        private val PAYMENT = Regex(
            "(?i)\\b(upi|credit card|debit card|cod|cash on delivery|net ?banking|wallet|pay later|emi|paypal)\\b"
        )
        private val VARIANT = Regex("(?i)\\b(size|colour|color|variant|shade|model|flavour|flavor)\\b")
        private val TOTAL = Regex("(?i)\\b(total|order total|grand total|to pay|amount payable)\\b")
        private val NOISE = Regex(
            "(?i)^(add to ?cart|buy now|place order|continue|next|back|cancel|apply|checkout|" +
                "proceed.*|see all|view all|edit|change|remove|delete|save|share|help)$"
        )

        fun from(nodes: List<Box>, appLabel: String): ScreenSummary {
            val labels = nodes
                .filter { it.label.isNotBlank() }
                .sortedBy { it.top }
                .map { it.label.trim() }
                .distinct()

            val productLabel = labels.firstOrNull { looksLikeTitle(it) }
            val product = productLabel?.let { clip(it) }

            // Skip the title itself: "Amul Milk 500 ml" would otherwise reappear as a detail.
            val specs = labels.filter { l ->
                l != productLabel &&
                    (VARIANT.containsMatchIn(l) || DIMENSION.containsMatchIn(l) || QTY.containsMatchIn(l)) &&
                    !NOISE.matches(Text.norm(l)) && l.length <= 60
            }.map { clip(it) }.distinct().take(3)

            val prices = labels.flatMap { l -> PRICE.findAll(l).map { it.value }.toList() }
            val price = prices.firstOrNull()
            val total = labels.firstOrNull { TOTAL.containsMatchIn(it) && PRICE.containsMatchIn(it) }
                ?.let { clip(it) }
                ?: prices.maxByOrNull { numeric(it) }?.takeIf { prices.size > 1 }

            val delivery = labels.firstOrNull { DELIVERY.containsMatchIn(it) && DATE.containsMatchIn(it) }
                ?.let { clip(it) }
                ?: labels.firstOrNull { DATE.containsMatchIn(it) && it.length <= 60 }?.let { clip(it) }

            val payment = labels.firstOrNull { PAYMENT.containsMatchIn(it) }?.let { clip(it) }

            val bullets = buildList {
                product?.let { add("Product · $it") }
                if (specs.isNotEmpty()) add("Details · ${specs.joinToString(", ")}")
                price?.let { add("Price · $it") }
                total?.let { if (it != price) add("Total · $it") }
                delivery?.let { add("Delivery · $it") }
                payment?.let { add("Payment · $it") }
            }.ifEmpty {
                // Nothing recognisable: show the first few lines rather than an empty box.
                labels.filter { !NOISE.matches(Text.norm(it)) }.take(4).map { clip(it, 48) }
            }

            return ScreenSummary(appLabel, product, specs, price, total, delivery, payment, bullets)
        }

        private fun looksLikeTitle(label: String): Boolean {
            val n = Text.norm(label)
            if (label.length < 15 || label.length > 90) return false
            if (NOISE.matches(n)) return false
            if (PRICE.containsMatchIn(label)) return false
            if (DATE.containsMatchIn(label)) return false
            if (label.count { it.isLetter() } < 12) return false
            return true
        }

        private fun numeric(price: String): Long =
            price.filter { it.isDigit() }.toLongOrNull() ?: 0L

        private fun clip(s: String, max: Int = 64): String =
            if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
    }
}
