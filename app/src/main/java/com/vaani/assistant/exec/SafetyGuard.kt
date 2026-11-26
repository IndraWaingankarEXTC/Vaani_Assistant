package com.vaani.assistant.exec

import com.vaani.assistant.core.Text
import com.vaani.assistant.ui.Box

/**
 * The one rule that cannot be argued with: Vaani never moves money and never helps pirate content.
 *
 * This is enforced at the *point of action*, not at the point of parsing, so it still holds when a
 * recipe is wrong, an app changes its layout, or the LLM planner hallucinates a step. Every tap
 * and every app launch is checked here first.
 */
object SafetyGuard {

    /**
     * ALLOW   — do it.
     * CONFIRM — destructive; ask out loud.
     * AUTHORISE — money-adjacent (checkout, place order, proceed to pay). Vaani stops, shows a
     *             summary card, and moves only on an explicit human yes. See docs/SAFETY.md.
     * DENY    — never: credentials, PINs, card numbers, payment apps, piracy.
     */
    enum class Verdict { ALLOW, CONFIRM, AUTHORISE, DENY }

    data class Decision(val verdict: Verdict, val reason: String = "") {
        val allowed: Boolean get() = verdict == Verdict.ALLOW
    }

    private val ALLOW = Decision(Verdict.ALLOW)

    // ---------------------------------------------------------------- payments

    private val paymentPackages = setOf(
        "com.phonepe.app", "net.one97.paytm", "com.google.android.apps.nbu.paisa.user",
        "in.org.npci.upiapp", "com.amazon.mShop.android.pay", "com.mobikwik_new",
        "com.freecharge.android", "com.dreamplug.androidapp", "com.slicepay",
        "com.paypal.android.p2pmobile", "com.razorpay.payments", "com.wallet.crypto.trustapp",
        "com.binance.dev", "com.coinbase.android", "com.wazirx"
    )

    private val paymentLabelWords = listOf(
        "phonepe", "paytm", "google pay", "gpay", "bhim", "upi", "paypal", "razorpay",
        "netbanking", "net banking", "wallet"
    )

    /** Steps that commit an order or take you to payment: gated, not forbidden. */
    private val commitText = listOf(
        Regex("\\bplace (your )?order\\b"), Regex("\\bconfirm (order|purchase|booking)\\b"),
        Regex("\\bbuy now\\b"), Regex("\\bcheckout\\b"), Regex("\\bproceed to pay\\b"),
        Regex("\\bcontinue to pay(ment)?\\b"), Regex("\\bgo to checkout\\b"),
        Regex("\\bconfirm (and )?pay\\b"), Regex("\\bpay now\\b")
    )

    /** Credentials and final money movement: never touched, gated or not. */
    private val paymentText = listOf(
        Regex("\\benter (upi )?pin\\b"), Regex("\\bupi pin\\b"), Regex("\\benter (the )?otp\\b"),
        Regex("\\botp\\b"), Regex("\\bcvv\\b"), Regex("\\bcard number\\b"),
        Regex("\\badd (a )?(new )?card\\b"), Regex("\\bsend money\\b"),
        Regex("\\btransfer (money|funds)\\b"), Regex("\\bpay ₹"), Regex("\\bpay rs"),
        Regex("\\bsubscribe for\\b"), Regex("\\bstart (your )?subscription\\b")
    )

    // ---------------------------------------------------------------- piracy

    private val piracyPackages = setOf(
        "com.utorrent.client", "com.bittorrent.client", "org.qbittorrent.qbittorrent",
        "com.frostwire.android", "hu.tagsoft.ttorrent.lite"
    )

    private val piracyWords = listOf(
        "torrent", "pirate", "pirated", "crack", "cracked", "keygen", "warez", "nulled",
        "mod apk", "modded apk", "free download full movie", "magnet link"
    )

    // ---------------------------------------------------------------- destructive

    private val destructiveWords = listOf(
        "uninstall", "clear data", "clear storage", "delete all", "format", "factory reset",
        "erase", "remove account", "log out", "sign out", "forget network", "delete account"
    )

    // ---------------------------------------------------------------- checks

    fun forApp(pkg: String, label: String = ""): Decision {
        val n = Text.norm("$pkg $label")
        if (paymentPackages.contains(pkg)) return deny("payment app")
        if (paymentLabelWords.any { n.contains(Text.norm(it)) }) return deny("payment app")
        if (piracyPackages.contains(pkg)) return deny("piracy app")
        if (piracyWords.any { n.contains(Text.norm(it)) }) return deny("piracy app")
        return ALLOW
    }

    fun forText(label: String): Decision {
        val n = Text.norm(label)
        if (n.isEmpty()) return ALLOW
        // Credentials first: these are refused even when the user has authorised an order.
        for (rx in paymentText) if (rx.containsMatchIn(n)) return deny("payment credentials")
        if (piracyWords.any { n.contains(Text.norm(it)) }) return deny("piracy")
        for (rx in commitText) if (rx.containsMatchIn(n)) {
            return Decision(Verdict.AUTHORISE, "this commits an order")
        }
        if (destructiveWords.any { n.contains(Text.norm(it)) }) {
            return Decision(Verdict.CONFIRM, "that looks destructive")
        }
        return ALLOW
    }

    /**
     * Screen-level check: if the page in front of us is asking for money, stop the whole plan.
     * Catches the case where an app's own layout moved us into checkout.
     */
    fun forScreen(nodes: List<Box>): Decision {
        val joined = Text.norm(nodes.take(120).joinToString(" ") { it.label })
        if (joined.isEmpty()) return ALLOW
        // Only credential fields stop a plan outright. "Proceed to pay" is now a gated step,
        // handled by forText, so it must not trip the screen check first.
        val hardHits = listOf("upi pin", "enter pin", "cvv", "card number", "enter otp", "net banking password")
        if (hardHits.any { joined.contains(it) }) return deny("payment screen")
        return ALLOW
    }

    private fun deny(reason: String) = Decision(Verdict.DENY, reason)
}
