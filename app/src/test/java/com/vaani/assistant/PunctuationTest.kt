package com.vaani.assistant

import com.vaani.assistant.voice.Punctuation
import org.junit.Assert.assertEquals
import org.junit.Test

class PunctuationTest {

    @Test
    fun closesASentence() {
        assertEquals("Meeting moved to friday can you send the deck.",
            Punctuation.render("meeting moved to friday can you send the deck"))
    }

    @Test
    fun spokenPunctuation() {
        assertEquals("Hello, how are you?", Punctuation.render("hello comma how are you question mark"))
        assertEquals("First point. Second point. Done.",
            Punctuation.render("first point full stop second point full stop done"))
    }

    @Test
    fun newLines() {
        assertEquals("Line one.\nLine two.", Punctuation.render("line one full stop new line line two"))
        assertEquals("Dear sir, I am applying.\n\nRegards, ravi.",
            Punctuation.render("dear sir comma i am applying full stop new paragraph regards comma ravi"))
    }

    @Test
    fun addressesSurviveCapitalisation() {
        // #2 from the list: "capital letter email". The domain dot is not a sentence end.
        assertEquals("Mail me at indra@gmail.com.", Punctuation.render("mail me at indra@gmail.com"))
        assertEquals("indra@gmail.com.", Punctuation.render("indra@gmail.com"))
        assertEquals("See https://example.com/x for details.",
            Punctuation.render("see https://example.com/x for details"))
    }

    @Test
    fun decimalsAreNotSentenceEnds() {
        assertEquals("It costs 3.5 lakh.", Punctuation.render("it costs 3.5 lakh"))
    }

    @Test
    fun standaloneIIsCapitalised() {
        assertEquals("I think it works.", Punctuation.render("i think it works"))
    }

    @Test
    fun emptyAndAutoStopOff() {
        assertEquals("", Punctuation.render("   "))
        assertEquals("Hello, world", Punctuation.render("hello comma world", autoStop = false))
    }
}
