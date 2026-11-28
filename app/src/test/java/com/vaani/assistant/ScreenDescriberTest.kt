package com.vaani.assistant

import com.vaani.assistant.ui.ScreenDescriber
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "What's on my screen?" answered in a sentence, not a focus cursor. */
class ScreenDescriberTest {

    private fun screen() = listOf(
        FakeBox("Chats", 0, 10, 300, 60),
        FakeBox("New chat", 900, 10, 1060, 60, clickable = true),
        FakeBox("Search", 700, 10, 880, 60, clickable = true),
        FakeBox("Type a message", 0, 1800, 900, 1900, editable = true, clickable = true),
        FakeBox("Ravi: see you at 6", 0, 200, 900, 260)
    )

    @Test
    fun namesTheAppTheHeadingAndTheActions() {
        val d = ScreenDescriber.describe(screen(), "WhatsApp")
        assertTrue(d.contains("WhatsApp"))
        assertTrue(d.contains("Chats"))
        assertTrue(d.contains("New chat"))
        assertTrue(d.contains("Search"))
        assertTrue(d.contains("one text field"))
    }

    @Test
    fun doesNotListTextFieldsAsButtons() {
        val d = ScreenDescriber.describe(screen(), "WhatsApp")
        val buttons = d.substringAfter("You can press:", "")
        assertFalse("an input is not a button", buttons.contains("Type a message"))
    }

    @Test
    fun saysSoWhenNothingIsReadable() {
        val d = ScreenDescriber.describe(emptyList(), "Some App")
        assertTrue(d.contains("not showing me anything"))
    }

    @Test
    fun noAppNameIsFine() {
        val d = ScreenDescriber.describe(screen(), null)
        assertFalse(d.contains("This is"))
        assertTrue(d.contains("Chats"))
    }
}
