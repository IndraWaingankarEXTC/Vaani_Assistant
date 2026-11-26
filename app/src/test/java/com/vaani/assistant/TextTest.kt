package com.vaani.assistant

import com.vaani.assistant.core.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {

    @Test
    fun normalisesCasePunctuationAndSpacing() {
        assertEquals("open spotify now", Text.norm("  Open   Spotify,  NOW! "))
        assertEquals("6:30 am", Text.norm("6:30 AM"))
    }

    @Test
    fun stripsFillersPeopleActuallySay() {
        assertEquals("open spotify", Text.stripFillers("Hey Vaani, please open Spotify"))
        assertEquals("", Text.stripFillers("Vaani"))
    }

    @Test
    fun distanceAndSimilarity() {
        assertEquals(1, Text.distance("vani", "vaani"))
        assertTrue(Text.similar("spotify", "spotify"))
        assertFalse(Text.similar("obsidian", "zomato"))
    }

    @Test
    fun bestMatchRejectsNonsense() {
        val labels = listOf("Add to cart", "Buy now", "Place your order")
        assertEquals("Buy now", Text.bestMatch("buy now", labels))
        assertEquals("Place your order", Text.bestMatch("place order", labels))
        assertEquals(null, Text.bestMatch("zzzz qqqq", labels))
    }

    @Test
    fun numberWords() {
        assertEquals(7, Text.numberWord("seven"))
        assertEquals(30, Text.numberWord("thirty"))
    }
}
