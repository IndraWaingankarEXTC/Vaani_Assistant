package com.vaani.assistant

import com.vaani.assistant.nlu.Ordinals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "tap the third one" has to mean exactly the third one. */
class OrdinalsTest {

    @Test
    fun wordsBecomeIndices() {
        assertEquals(0, Ordinals.parse("the first one"))
        assertEquals(1, Ordinals.parse("tap the second one"))
        assertEquals(2, Ordinals.parse("the third one"))
        assertEquals(9, Ordinals.parse("tenth"))
    }

    @Test
    fun digitsAndOrdinalsToo() {
        assertEquals(2, Ordinals.parse("3rd"))
        assertEquals(20, Ordinals.parse("21st"))
        assertEquals(4, Ordinals.parse("number 5"))
        assertEquals(0, Ordinals.parse("tap 1"))
    }

    @Test
    fun positionPhrases() {
        assertTrue(Ordinals.isLast("the last one"))
        assertTrue(Ordinals.isLast("the bottom one"))
        assertTrue(Ordinals.isFirst("the top one"))
        assertTrue(Ordinals.countsFromBottom("second from the bottom"))
        assertEquals(1, Ordinals.parse("second from the bottom"))
    }

    @Test
    fun noNumberMeansNoOrdinal() {
        assertNull(Ordinals.parse("the blue button"))
        assertNull(Ordinals.parse(""))
    }
}
