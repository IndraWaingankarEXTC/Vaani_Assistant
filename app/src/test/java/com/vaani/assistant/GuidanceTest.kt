package com.vaani.assistant

import com.vaani.assistant.exec.Guidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Point me in the right direction" — the wording matters as much as the finding. */
class GuidanceTest {

    private val W = 1080
    private val H = 2400

    private val checkout = listOf(
        FakeBox("Your order", 40, 100, 400, 140, false),
        FakeBox("Sony WH-CH720N", 40, 200, 900, 260, false),
        FakeBox("Order total ₹8,469", 40, 1900, 700, 1940, false),
        FakeBox("Pay now", 40, 2000, 1040, 2080)
    )

    @Test
    fun pointsAtTheControlAndItsAnchor() {
        assertEquals(
            "It's at the bottom of the screen, just under 'Order total ₹8,469'.",
            Guidance.where(checkout, "pay now", W, H)
        )
    }

    @Test
    fun saysSoWhenItIsNotThere() {
        assertEquals(
            "I can't see anything like that on this screen.",
            Guidance.where(checkout, "place order", W, H)
        )
    }

    @Test
    fun topOfScreenReadsCorrectly() {
        val top = listOf(
            FakeBox("Skip", 40, 60, 300, 120),
            FakeBox("Welcome to the app", 40, 200, 900, 260, false)
        )
        assertEquals("It's at the top left, just above 'Welcome to the app'.",
            Guidance.where(top, "skip", W, H))
    }

    @Test
    fun offersTheClosestEquivalent() {
        val alternatives = Guidance.similar(checkout, "place order")
        assertTrue("expected an offer, got $alternatives", alternatives.isNotEmpty())
        assertEquals("Pay now", alternatives.first())
    }
}
