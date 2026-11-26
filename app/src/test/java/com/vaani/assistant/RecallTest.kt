package com.vaani.assistant

import com.vaani.assistant.core.TextRank
import com.vaani.assistant.memory.ObjectMemory
import com.vaani.assistant.notes.VoiceNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "where are my keys" and "find my note about the plumber" — same problem, same ranking. */
class RecallTest {

    private val day = 24L * 3600 * 1000

    private fun obj(label: String, note: String?, ageDays: Long) =
        ObjectMemory.Remembered(label, note, 1_000_000_000_000L - ageDays * day, null)

    private fun note(text: String, ageDays: Long) =
        VoiceNotes.Note(text, 1_000_000_000_000L - ageDays * day, null)

    @Test
    fun findsTheThingYouNamed() {
        val items = listOf(obj("keys", "on the kitchen counter", 3), obj("passport", null, 1))
        val hit = ObjectMemory.rank("where are my keys", items, 1_000_000_000_000L)
        assertEquals("keys", hit.first().label)
    }

    @Test
    fun recentBeatsOldWhenBothMatch() {
        val items = listOf(obj("charger", "bedroom", 20), obj("charger", "bag", 1))
        val hit = ObjectMemory.rank("where is my charger", items, 1_000_000_000_000L)
        assertEquals("bag", hit.first().note)
    }

    @Test
    fun unrelatedQuestionsReturnNothing() {
        val items = listOf(obj("keys", null, 2))
        assertTrue(ObjectMemory.rank("what is the weather", items, 1_000_000_000_000L).isEmpty())
    }

    @Test
    fun notesAreSearchableByWhatWasSaid() {
        val notes = listOf(
            note("call the plumber about the kitchen tap", 2),
            note("buy milk and eggs", 5),
            note("the wifi password is on the router", 9)
        )
        val hit = VoiceNotes.rank("plumber", notes, 1_000_000_000_000L)
        assertEquals(1, hit.size)
        assertTrue(hit.first().text.contains("plumber"))
    }

    @Test
    fun recencyDecaysOverAMonth() {
        assertEquals(1.0, TextRank.recency(0), 0.001)
        assertEquals(0.0, TextRank.recency(31L * day), 0.001)
        assertTrue(TextRank.recency(day) in 0.9..1.0)
    }
}
