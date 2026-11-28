package com.vaani.assistant.exec

import com.vaani.assistant.core.Text
import com.vaani.assistant.ui.Box

/**
 * "Point me in the right direction."
 *
 * When Vaani cannot press something — because it is not allowed to, or because it is not there —
 * saying "I couldn't do that" is useless. This turns the screen into directions a person can
 * follow: which part of the screen, and which label it sits under.
 *
 * Everything is computed from the accessibility bounds, so the description is as good as the tree
 * the app exposes. It is a pointer, not a promise.
 */
object Guidance {

    /** Short, spoken description of where [target] is — or what is closest to it. */
    fun where(
        nodes: List<Box>,
        target: String,
        screenWidth: Int = 1080,
        screenHeight: Int = 1920
    ): String {
        val hit = bestNode(nodes, target)
            ?: return "I can't see anything like that on this screen."

        val place = region(hit, screenWidth, screenHeight)
        val neighbour = neighbour(nodes, hit)
        return if (neighbour != null) {
            "It's $place, just ${neighbour.second} '${clip(neighbour.first)}'."
        } else {
            "It's $place."
        }
    }

    /**
     * Buttons that mean the same thing across apps: Amazon says "Place your order", Flipkart says
     * "Place Order", another app says "Pay now". Textual similarity alone misses those, so the
     * offer falls back to these families.
     */
    private val families = listOf(
        setOf("place order", "place your order", "buy now", "order now", "confirm order",
            "pay now", "proceed to pay", "confirm payment", "checkout", "proceed to checkout",
            "go to checkout"),
        setOf("add to cart", "add to bag", "add to basket", "add item"),
        setOf("send", "send message", "submit", "post", "share"),
        setOf("next", "continue", "proceed", "done", "ok", "okay"),
        setOf("search", "find", "look up", "search here"),
        setOf("cancel", "close", "dismiss", "not now", "no thanks", "skip")
    )

    private fun familyOf(label: String): Set<String>? {
        val n = Text.norm(label)
        return families.firstOrNull { family -> family.any { Text.norm(it) == n } }
    }

    /** Labels that plausibly mean the same thing, best first. Never includes [target] itself. */
    fun similar(nodes: List<Box>, target: String, limit: Int = 3): List<String> {
        val q = Text.norm(target)
        if (q.isEmpty()) return emptyList()
        val candidates = nodes
            .filter { it.label.isNotBlank() && (it.clickable || it.editable) }
            .map { it.label }
            .distinct()
            .filter { Text.norm(it) != q }
        val queryFamily = familyOf(target)
        val scored = candidates.mapNotNull { label ->
            val textual = score(q, Text.norm(label))
            val sameFamily = queryFamily != null && familyOf(label) === queryFamily
            val effective = if (sameFamily) maxOf(textual, 0.95) else textual
            if (effective >= 0.45) label to effective else null
        }
        return scored.sortedByDescending { it.second }.map { it.first }.take(limit)
    }

    // ------------------------------------------------------------------ internals

    private fun score(query: String, label: String): Double {
        if (label.isEmpty()) return 0.0
        val qWords = query.split(' ').filter { it.isNotBlank() }.toSet()
        val lWords = label.split(' ').filter { it.isNotBlank() }.toSet()
        val overlap = if (qWords.isEmpty()) 0.0
        else qWords.intersect(lWords).size.toDouble() / qWords.size
        val maxLen = maxOf(query.length, label.length)
        val edit = if (maxLen == 0) 1.0 else 1.0 - Text.distance(query, label).toDouble() / maxLen
        return 0.6 * overlap + 0.4 * edit
    }

    fun bestNode(nodes: List<Box>, target: String): Box? {
        val q = Text.norm(target)
        if (q.isEmpty()) return null
        nodes.firstOrNull { Text.norm(it.label) == q }?.let { return it }
        val best = Text.bestMatch(q, nodes.map { it.label }, 0.6) ?: return null
        return nodes.firstOrNull { it.label == best }
    }

    /** "bottom of the screen", "middle right", "top left" — coarse on purpose. */
    fun region(node: Box, screenWidth: Int, screenHeight: Int): String {
        val cy = ((node.top + node.bottom) / 2f)
        val cx = ((node.left + node.right) / 2f)
        val vertical = when {
            cy < screenHeight * 0.30f -> "top"
            cy < screenHeight * 0.70f -> "middle"
            else -> "bottom"
        }
        val horizontal = when {
            cx < screenWidth * 0.33f -> "left"
            cx > screenWidth * 0.67f -> "right"
            else -> ""
        }
        return if (horizontal.isEmpty()) "at the $vertical of the screen"
        else "at the $vertical $horizontal"
    }

    /**
     * The nearest label above or below the target, used as an anchor: "under 'Order total'".
     * Only labels that actually sit above/below and overlap horizontally count.
     */
    private fun neighbour(nodes: List<Box>, node: Box): Pair<String, String>? {
        val above = nodes.filter { other ->
            other !== node && other.label.length > 2 &&
                other.bottom <= node.top &&
                other.right > node.left && other.left < node.right
        }.maxByOrNull { it.bottom }

        if (above != null && node.top - above.bottom < 220) {
            return above.label to "under"
        }
        val below = nodes.filter { other ->
            other !== node && other.label.length > 2 &&
                other.top >= node.bottom &&
                other.right > node.left && other.left < node.right
        }.minByOrNull { it.top }

        if (below != null && below.top - node.bottom < 220) {
            return below.label to "above"
        }
        return null
    }

    private fun clip(s: String, max: Int = 42): String =
        if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"
}
