package com.vaani.assistant.exec

import com.vaani.assistant.core.Text
import com.vaani.assistant.core.Vlog
import com.vaani.assistant.ui.UiNode

/**
 * The small unplanned actions: closing the thing that is in the way.
 *
 * A cookie banner, a "not now" upsell, a permission prompt — none of these were in the plan, and
 * a strict assistant would stop and report "I can't find the button". Instead Vaani may dismiss
 * one, then retry the original step.
 *
 * Deliberately narrow. It only ever presses a label on an explicit allow-list, never more than
 * twice per step, and never on a screen the safety guard has flagged — a payment sheet or a
 * checkout page is not something to go poking at.
 */
object Obstacle {

    private const val MAX_CLEARS = 2

    /** Preferred first: refusing consent is better than accepting it. */
    private val dismissal = listOf(
        "reject all", "decline", "decline all",
        "not now", "no thanks", "no, thanks", "maybe later", "later",
        "skip", "skip for now", "skip this", "dismiss", "got it", "ok", "okay",
        "allow while using the app", "while using the app", "only this time",
        "continue", "close ad", "no, i don't want"
    )

    private var clearsThisStep = 0

    fun reset() {
        clearsThisStep = 0
    }

    /**
     * Returns the node to press, or null when there is nothing safe to clear.
     * [nodes] is the current snapshot; the caller re-snapshots afterwards.
     */
    fun findDismisser(nodes: List<UiNode>): UiNode? {
        if (clearsThisStep >= MAX_CLEARS) return null
        if (SafetyGuard.forScreen(nodes).verdict == SafetyGuard.Verdict.DENY) return null

        for (phrase in dismissal) {
            val hit = nodes.firstOrNull { node ->
                node.clickable && Text.norm(node.label) == phrase
            }
            if (hit != null) return hit
        }
        // Looser second pass, but only for unambiguous wording.
        for (phrase in dismissal.take(8)) {
            val hit = nodes.firstOrNull { node ->
                node.clickable && Text.norm(node.label).startsWith(phrase)
            }
            if (hit != null) return hit
        }
        return null
    }

    fun noteCleared(label: String) {
        clearsThisStep++
        Vlog.i("Obstacle", "dismissed '$label' ($clearsThisStep/$MAX_CLEARS)")
    }
}
