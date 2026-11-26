package com.vaani.assistant

import com.vaani.assistant.lens.CalloutLayout
import com.vaani.assistant.lens.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "the information near the object" — above it by default, and never off screen. */
class CalloutLayoutTest {

    private val canvasW = 1080f
    private val canvasH = 1920f

    @Test
    fun sitsAboveTheObject() {
        val box = Detection(300f, 900f, 600f, 1200f)
        val c = CalloutLayout.place(box, textWidth = 240f, textHeight = 60f, canvasW, canvasH)
        assertTrue(c.above)
        assertTrue("callout bottom should be above the box", c.bottom < box.top)
        assertEquals(box.left, c.left, 0.01f)
    }

    @Test
    fun flipsBelowWhenThereIsNoRoomAbove() {
        val box = Detection(300f, 20f, 600f, 320f)
        val c = CalloutLayout.place(box, textWidth = 240f, textHeight = 60f, canvasW, canvasH)
        assertFalse(c.above)
        assertTrue(c.top > box.bottom)
    }

    @Test
    fun slidesLeftAtTheRightEdge() {
        val box = Detection(1000f, 900f, 1075f, 1200f)
        val c = CalloutLayout.place(box, textWidth = 300f, textHeight = 60f, canvasW, canvasH)
        assertTrue("must stay inside the canvas", c.right <= canvasW)
    }

    @Test
    fun neverLeavesTheCanvas() {
        val box = Detection(0f, 1900f, 100f, 1915f)
        val c = CalloutLayout.place(box, textWidth = 400f, textHeight = 60f, canvasW, canvasH)
        assertTrue(c.top >= 0f)
        assertTrue(c.bottom <= canvasH)
        assertTrue(c.left >= 0f)
    }

    @Test
    fun followsTheObjectAsItMoves() {
        val left = CalloutLayout.place(Detection(100f, 900f, 300f, 1200f), 200f, 60f, canvasW, canvasH)
        val right = CalloutLayout.place(Detection(700f, 900f, 900f, 1200f), 200f, 60f, canvasW, canvasH)
        assertTrue("callout should travel with the box", right.left > left.left)
    }
}
