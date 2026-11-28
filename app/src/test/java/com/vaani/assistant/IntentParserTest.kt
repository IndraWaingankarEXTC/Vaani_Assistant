package com.vaani.assistant

import com.vaani.assistant.nlu.Command
import com.vaani.assistant.nlu.IntentParser
import com.vaani.assistant.nlu.MediaAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every case here is an utterance that was actually typed into the harness at some point. */
class IntentParserTest {

    private val parser = IntentParser()

    private fun open(c: Command): Command.OpenApp {
        assertTrue("expected OpenApp, got $c", c is Command.OpenApp)
        return c as Command.OpenApp
    }

    @Test
    fun opensAppsInAnyLanguage() {
        assertEquals("spotify", open(parser.parse("open spotify")).app)
        assertEquals("spotify", open(parser.parse("Spotify kholo")).app)
        assertEquals("obsidian", open(parser.parse("obsidian khol do")).app)
    }

    @Test
    fun playOnSpotifyUsesItsOwnSearch() {
        // Was OpenApp("spotify") + typing into its search box. Now it hands the query to
        // Spotify's own search deep link, which needs no screen control at all.
        val c = parser.parse("play lofi beats on spotify")
        assertTrue(c is com.vaani.assistant.nlu.Command.SpotifyPlay)
        assertEquals("lofi beats", (c as com.vaani.assistant.nlu.Command.SpotifyPlay).query)
    }

    @Test
    fun openThenSearchMergesIntoOneCommand() {
        val c = open(parser.parse("open amazon and search for headphones"))
        assertEquals("amazon", c.app)
        assertEquals("headphones", c.query)
    }

    @Test
    fun callsInIndianWordOrder() {
        val c = parser.parse("amma ko call karo")
        assertTrue(c is Command.Call)
        assertEquals("amma", (c as Command.Call).who)
    }

    @Test
    fun messagesInIndianWordOrder() {
        val c = parser.parse("amma ko message bhejo saying aa rahi hoon")
        assertTrue(c is Command.SendSms)
        c as Command.SendSms
        assertEquals("amma", c.who)
        assertEquals("aa rahi hoon", c.text)
    }

    @Test
    fun mediaTransport() {
        assertEquals(Command.Media(MediaAction.PAUSE), parser.parse("pause"))
        assertEquals(Command.Media(MediaAction.NEXT), parser.parse("next song"))
        assertEquals(Command.Volume(true), parser.parse("volume up"))
        assertEquals(Command.Volume(false), parser.parse("awaaz kam karo"))
    }

    @Test
    fun alarmsAndTimers() {
        val alarm = parser.parse("alarm lagao 7 baje")
        assertTrue(alarm is Command.SetAlarm)
        assertEquals(7, (alarm as Command.SetAlarm).hour)

        val timer = parser.parse("set a timer for 25 minutes")
        assertTrue(timer is Command.SetTimer)
        assertEquals(1500, (timer as Command.SetTimer).seconds)
    }

    @Test
    fun togglesInDevanagari() {
        val c = parser.parse("torch चालू करो")
        assertTrue(c is Command.Toggle)
        c as Command.Toggle
        assertEquals("torch", c.setting)
        assertTrue(c.on)
    }

    @Test
    fun screenActions() {
        assertEquals(Command.Back, parser.parse("go back"))
        assertEquals(Command.ReadScreen, parser.parse("kya hai screen par"))
        assertTrue(parser.parse("tap Continue") is Command.Tap)
        assertTrue(parser.parse("type biryani") is Command.TypeText)
    }

    @Test
    fun dictationMode() {
        assertEquals(Command.Dictate, parser.parse("start dictating"))
        assertEquals(Command.Dictate, parser.parse("type mode"))
        assertEquals(Command.Dictate, parser.parse("bol ke likho"))
        assertEquals(Command.StopDictation, parser.parse("stop typing"))
    }

    @Test
    fun compoundCommandsAreSplit() {
        val c = parser.parse("open spotify and set a timer for 5 minutes")
        assertTrue("expected Compound, got $c", c is Command.Compound)
        val parts = (c as Command.Compound).parts
        assertEquals(2, parts.size)
        assertEquals("spotify", (parts[0] as Command.OpenApp).app)
        assertEquals(300, (parts[1] as Command.SetTimer).seconds)
    }

    @Test
    fun commaJoinedPlayIsFoldedIntoTheApp() {
        // "spotify kholo, lofi bajao" is one intent: open Spotify on lofi.
        val c = parser.parse("spotify kholo, lofi bajao")
        val open = open(c)
        assertEquals("spotify", open.app)
        assertEquals("lofi", open.query)
    }

    @Test
    fun nonsenseIsUnknownNotAGuess() {
        assertTrue(parser.parse("qwerty zxcvbn") is Command.Unknown)
    }
}
