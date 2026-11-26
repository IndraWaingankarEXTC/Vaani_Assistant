package com.vaani.assistant

import com.vaani.assistant.nlu.CodeMixLexicon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The heart of "mixed language, not one language at a time". */
class CodeMixLexiconTest {

    @Test
    fun romanisedHindiBecomesCanonical() {
        assertEquals("spotify open", CodeMixLexicon.rewrite("spotify kholo"))
        // rewriting is per-token; word order is the parser's job (see IntentParser.trailingVerb)
        assertEquals("lofi play", CodeMixLexicon.rewrite("lofi bajao"))
        assertEquals("volume up", CodeMixLexicon.rewrite("awaaz badhao"))
        assertEquals("turn off", CodeMixLexicon.rewrite("band karo"))
    }

    @Test
    fun devanagariIsUnderstoodInScript() {
        assertEquals("open", CodeMixLexicon.rewrite("खोलो"))
        assertEquals("turn off", CodeMixLexicon.rewrite("बंद करो"))
        assertEquals("volume up", CodeMixLexicon.rewrite("आवाज़ बढ़ाओ"))
        assertEquals("call", CodeMixLexicon.rewrite("फोन करो"))
    }

    @Test
    fun otherScriptsWorkToo() {
        assertEquals("open", CodeMixLexicon.rewrite("திற"))       // Tamil
        assertEquals("open", CodeMixLexicon.rewrite("খোলো"))      // Bengali
        assertEquals("play", CodeMixLexicon.rewrite("వాయించు"))    // Telugu
    }

    @Test
    fun transliterationSurvivesMatras() {
        // Normalising before transliterating would turn "खोलो" into "खल".
        assertEquals("kholo", CodeMixLexicon.transliterate("खोलो"))
        assertTrue(CodeMixLexicon.transliterate("स्पॉटिफाई").startsWith("spot"))
    }

    @Test
    fun englishPassesThroughUntouched() {
        assertEquals("open obsidian and search notes",
            CodeMixLexicon.rewrite("open obsidian and search notes"))
    }
}
