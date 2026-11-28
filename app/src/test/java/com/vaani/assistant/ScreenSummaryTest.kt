package com.vaani.assistant

import com.vaani.assistant.exec.ScreenSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSummaryTest {

    private fun summaryOf(labels: List<String>): ScreenSummary {
        // Lay the labels out top to bottom so the "longest line near the top" rule has geometry.
        val boxes = labels.mapIndexed { i, label ->
            FakeBox(label, 40, 100 + i * 80, 1000, 160 + i * 80)
        }
        return ScreenSummary.from(boxes, "Amazon")
    }

    @Test
    fun readsARealCheckout() {
        val s = summaryOf(
            listOf(
                "Back",
                "Your order",
                "Sony WH-CH720N Noise Cancelling Wireless Headphones, Black",
                "Colour: Black",
                "Quantity: 1",
                "₹7,990",
                "FREE delivery Tue, 25 Nov",
                "Order total: ₹8,469",
                "Pay with UPI",
                "Place your order"
            )
        )
        assertEquals("Sony WH-CH720N Noise Cancelling Wireless Headphones, Black", s.product)
        assertEquals("₹7,990", s.price)
        assertEquals("Order total: ₹8,469", s.total)
        assertTrue(s.delivery!!.contains("Tue, 25 Nov"))
        assertEquals("Pay with UPI", s.payment)
        assertTrue(s.bullets.any { it.startsWith("Product ·") })
        assertTrue(s.bullets.any { it.startsWith("Delivery ·") })
    }

    @Test
    fun keepsItShort() {
        val s = summaryOf(listOf("Something long enough to be a title here", "₹1,299", "Total ₹1,499"))
        assertTrue("bullets should stay skimmable: ${s.bullets.size}", s.bullets.size <= 6)
    }

    @Test
    fun fallsBackInsteadOfShowingNothing() {
        val s = summaryOf(listOf("Loading", "Please wait"))
        assertTrue(s.bullets.isNotEmpty())
    }
}
