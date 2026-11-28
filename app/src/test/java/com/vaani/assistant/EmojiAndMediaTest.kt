package com.vaani.assistant

import com.vaani.assistant.nlu.Command
import com.vaani.assistant.nlu.Emoji
import com.vaani.assistant.nlu.IntentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiAndMediaTest {

    private val parser = IntentParser()

    @Test
    fun emojiNamesResolve() {
        assertEquals("\u2764\uFE0F", Emoji.character("heart"))
        assertEquals("\uD83D\uDC4D", Emoji.character("thumbs up"))
        assertEquals("\uD83D\uDE02", Emoji.character("laugh"))
        assertNull(Emoji.character("banana"))
        assertNull(Emoji.character(""))
    }

    @Test
    fun spokenEmojiBecomesACommand() {
        assertEquals(Command.SendEmoji("heart"), parser.parse("send a heart emoji"))
        assertEquals(Command.SendEmoji("thumbs up"), parser.parse("send thumbs up emoji"))
        assertTrue(parser.parse("insert a smile emoji") is Command.SendEmoji)
    }

    @Test
    fun spotifySearchAndPlay() {
        assertEquals(Command.SpotifyPlay("lofi"), parser.parse("play lofi on spotify"))
        assertEquals(Command.SpotifyPlay("arijit singh"), parser.parse("spotify play arijit singh"))
        assertTrue(parser.parse("spotify search ap dhillon") is Command.SpotifyPlay)
    }

    @Test
    fun openingSpotifyIsStillJustOpeningIt() {
        // The new Spotify rules must not swallow "open spotify".
        assertTrue(parser.parse("open spotify") is Command.OpenApp)
    }

    @Test
    fun cinemaCommands() {
        assertEquals(Command.BookMovies, parser.parse("book movie tickets"))
        assertEquals(Command.BookMovies, parser.parse("book my show"))
        assertEquals(Command.NewMovies, parser.parse("new movies"))
        assertEquals(Command.NewMovies, parser.parse("what movies are running"))
    }
}
