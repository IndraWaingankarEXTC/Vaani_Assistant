package com.vaani.assistant

import com.vaani.assistant.nlu.TimeParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeParserTest {

    @Test
    fun clockForms() {
        assertEquals(TimeParser.Clock(6, 30), TimeParser.parseClock("6:30 am"))
        assertEquals(TimeParser.Clock(18, 30), TimeParser.parseClock("6:30 pm"))
        assertEquals(TimeParser.Clock(19, 45), TimeParser.parseClock("19:45"))
        assertEquals(TimeParser.Clock(7, 0), TimeParser.parseClock("7"))
        assertEquals(TimeParser.Clock(6, 30), TimeParser.parseClock("half past six"))
        assertEquals(TimeParser.Clock(7, 45), TimeParser.parseClock("quarter to 8"))
        assertEquals(TimeParser.Clock(7, 0), TimeParser.parseClock("7 baje"))
    }

    @Test
    fun durations() {
        assertEquals(1500, TimeParser.parseDurationSeconds("25 minutes"))
        assertEquals(90, TimeParser.parseDurationSeconds("90 seconds"))
        assertEquals(3600, TimeParser.parseDurationSeconds("an hour"))
        assertEquals(5400, TimeParser.parseDurationSeconds("1 hour 30 minutes"))
    }
}
