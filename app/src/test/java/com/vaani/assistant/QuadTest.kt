package com.vaani.assistant

import com.vaani.assistant.lens.Quad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The four corners arrive in any order; the crop needs them in one. */
class QuadTest {

    @Test
    fun cornersAreReordered() {
        // Given in a deliberately scrambled order.
        val scrambled = floatArrayOf(
            200f, 300f,   // bottom-right
            10f, 10f,     // top-left
            10f, 300f,    // bottom-left
            200f, 10f     // top-right
        )
        val q = Quad.ordered(scrambled)
        assertEquals(10f, q[0], 0.01f)   // TL x
        assertEquals(10f, q[1], 0.01f)   // TL y
        assertEquals(200f, q[2], 0.01f)  // TR x
        assertEquals(200f, q[4], 0.01f)  // BR x
        assertEquals(300f, q[5], 0.01f)  // BR y
        assertEquals(10f, q[6], 0.01f)   // BL x
    }

    @Test
    fun outputSizeFollowsTheEdges() {
        val square = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        val (w, h) = Quad.outputSize(square)
        assertEquals(100, w)
        assertEquals(100, h)
    }

    @Test
    fun tinyOrCollapsedSelectionsAreRejected() {
        val frame = 1000f * 1000f
        val tiny = floatArrayOf(0f, 0f, 5f, 0f, 5f, 5f, 0f, 5f)
        assertFalse(Quad.isUsable(tiny, frameArea = frame))
        val collapsed = floatArrayOf(10f, 10f, 10f, 10f, 10f, 10f, 10f, 10f)
        assertFalse(Quad.isUsable(collapsed, frameArea = frame))
        val good = floatArrayOf(100f, 100f, 800f, 100f, 800f, 800f, 100f, 800f)
        assertTrue(Quad.isUsable(good, frameArea = frame))
    }
}
