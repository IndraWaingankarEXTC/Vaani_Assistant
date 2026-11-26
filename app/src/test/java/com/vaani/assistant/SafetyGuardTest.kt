package com.vaani.assistant

import com.vaani.assistant.exec.SafetyGuard
import org.junit.Assert.assertEquals
import org.junit.Test

/** The four tiers are the whole point: gated, not forbidden — except credentials. */
class SafetyGuardTest {

    @Test
    fun committingAnOrderIsGated() {
        assertEquals(SafetyGuard.Verdict.AUTHORISE, SafetyGuard.forText("Place your order").verdict)
        assertEquals(SafetyGuard.Verdict.AUTHORISE, SafetyGuard.forText("Proceed to pay").verdict)
        assertEquals(SafetyGuard.Verdict.AUTHORISE, SafetyGuard.forText("Buy now").verdict)
    }

    @Test
    fun credentialsAreNeverTouched() {
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forText("Enter UPI PIN").verdict)
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forText("CVV").verdict)
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forText("Enter OTP").verdict)
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forText("Card number").verdict)
    }

    @Test
    fun ordinaryStepsAreAllowed() {
        assertEquals(SafetyGuard.Verdict.ALLOW, SafetyGuard.forText("Add to cart").verdict)
        assertEquals(SafetyGuard.Verdict.ALLOW, SafetyGuard.forText("Search").verdict)
    }

    @Test
    fun destructiveAsksFirst() {
        assertEquals(SafetyGuard.Verdict.CONFIRM, SafetyGuard.forText("Uninstall").verdict)
        assertEquals(SafetyGuard.Verdict.CONFIRM, SafetyGuard.forText("Clear data").verdict)
    }

    @Test
    fun paymentAppsAndTorrentsAreBlocked() {
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forApp("com.phonepe.app").verdict)
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forApp("net.one97.paytm").verdict)
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forApp("com.utorrent.client").verdict)
        assertEquals(SafetyGuard.Verdict.ALLOW, SafetyGuard.forApp("com.spotify.music").verdict)
    }

    @Test
    fun aCredentialScreenStopsThePlan() {
        val nodes = listOf(FakeBox("Enter UPI PIN", 40, 500, 900, 560))
        assertEquals(SafetyGuard.Verdict.DENY, SafetyGuard.forScreen(nodes).verdict)
    }
}
