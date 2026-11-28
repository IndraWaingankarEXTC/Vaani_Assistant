package com.vaani.assistant

import com.vaani.assistant.nlu.NameMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #3 from the list: "apostrophe in a name".
 *
 * Speech drops the apostrophe, the phone book keeps it, and D'Souza is a common Indian surname.
 * A contact lookup that fails on that is a lookup that fails for real people.
 */
class NameMatchTest {

    @Test
    fun apostrophesAreIrrelevant() {
        assertTrue(NameMatch.matches("OBrien", "O'Brien"))
        assertTrue(NameMatch.matches("o brien", "O'Brien"))
        assertTrue(NameMatch.matches("D'Souza", "DSouza"))
        assertTrue(NameMatch.matches("DSouza", "D'Souza"))
        assertTrue(NameMatch.matches("d souza", "D'Souza"))
    }

    @Test
    fun hyphensAndSpacingToo() {
        assertTrue(NameMatch.matches("AnneMarie", "Anne-Marie"))
        assertEquals("dsouza", NameMatch.key("D'Souza"))
        assertEquals("obrien", NameMatch.key("O'Brien"))
    }

    @Test
    fun doesNotCallTheWrongPerson() {
        assertFalse(NameMatch.matches("priya", "Ravi"))
        // Nicknames are deliberately not guessed: "ram" must not resolve to "Ramkumar".
        assertFalse(NameMatch.matches("ram", "Ramkumar"))
    }

    @Test
    fun picksTheExactOne() {
        val book = listOf("Ravi Kumar", "O'Brien", "Priya")
        assertEquals("O'Brien", NameMatch.best("obrien", book))
        assertEquals("Ravi Kumar", NameMatch.best("Ravi Kumar", book))
    }

    @Test
    fun sqlPrefilterDropsTheApostrophe() {
        assertEquals("brien", NameMatch.queryToken("O'Brien"))
        assertEquals("souza", NameMatch.queryToken("D'Souza"))
    }
}
