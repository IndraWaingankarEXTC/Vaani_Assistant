package com.vaani.assistant

import com.vaani.assistant.voice.ReadAloud
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudTest {

    @Test
    fun shortTextIsOneChunk() {
        assertEquals(listOf("Hello there."), ReadAloud.chunks("Hello there."))
    }

    @Test
    fun longTextSplitsAtSentenceEnds() {
        val text = (1..12).joinToString(" ") { "This is sentence number $it." }
        val chunks = ReadAloud.chunks(text, maxChars = 120)
        assertTrue("should split", chunks.size > 1)
        assertTrue("no chunk is oversized", chunks.all { it.length <= 140 })
        assertTrue("every chunk ends a sentence", chunks.all { it.endsWith(".") })
    }

    @Test
    fun emptyTextGivesNothing() {
        assertTrue(ReadAloud.chunks("   ").isEmpty())
    }

    @Test
    fun speedStepsAndClamps() {
        assertEquals(1.25f, ReadAloud.stepSpeed(1.0f, faster = true), 0.001f)
        assertEquals(0.8f, ReadAloud.stepSpeed(1.0f, faster = false), 0.001f)
        assertEquals(2.0f, ReadAloud.stepSpeed(1.9f, faster = true), 0.001f)
        assertEquals(0.6f, ReadAloud.stepSpeed(0.65f, faster = false), 0.001f)
    }

    @Test
    fun indexStaysInBounds() {
        assertEquals(0, ReadAloud.nextIndex(0, 3, forward = false))
        assertEquals(2, ReadAloud.nextIndex(2, 3, forward = true))
        assertEquals(1, ReadAloud.nextIndex(0, 3, forward = true))
        assertEquals(0, ReadAloud.nextIndex(0, 0, forward = true))
    }
}
