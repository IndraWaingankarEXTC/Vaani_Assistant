package com.vaani.assistant

import com.vaani.assistant.notify.Notif
import com.vaani.assistant.notify.NotificationDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDigestTest {

    private fun n(app: String, title: String, text: String, at: Long, ongoing: Boolean = false) =
        Notif(app, title, text, at, ongoing)

    @Test
    fun saysWhatCameInAndFromWhere() {
        val items = listOf(
            n("WhatsApp", "Ravi", "see you at 6", 300),
            n("Gmail", "Amazon", "your order shipped", 200)
        )
        val s = NotificationDigest.summarise(items)
        assertTrue(s.contains("2 things from 2 apps"))
        assertTrue(s.contains("Ravi"))
    }

    @Test
    fun oneAppIsNamed() {
        val items = listOf(
            n("WhatsApp", "Ravi", "ok", 300),
            n("WhatsApp", "Priya", "call me", 200)
        )
        assertTrue(NotificationDigest.summarise(items).contains("2 from WhatsApp"))
    }

    @Test
    fun ongoingAndMediaAreNotNews() {
        val items = listOf(
            n("Spotify", "Playing", "lo-fi beats", 500, ongoing = true),
            n("System", "Downloading", "file.zip", 400),
            n("WhatsApp", "Ravi", "hi", 300)
        )
        val useful = NotificationDigest.worthTelling(items)
        assertEquals(1, useful.size)
        assertEquals("WhatsApp", useful.first().app)
        assertFalse(NotificationDigest.summarise(items).contains("Spotify"))
    }

    @Test
    fun silenceIsReportedAsSilence() {
        assertEquals("Nothing new needs you.", NotificationDigest.summarise(emptyList()))
    }

    @Test
    fun oneThingReadsNaturally() {
        val s = NotificationDigest.summarise(listOf(n("Phone", "Amma", "call back", 1)))
        assertTrue(s.startsWith("One thing:"))
    }
}
