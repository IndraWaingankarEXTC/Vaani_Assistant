package com.vaani.assistant

import com.vaani.assistant.compress.Compressor
import com.vaani.assistant.compress.Encoder
import com.vaani.assistant.compress.Sizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The size search, tested against a synthetic encoder whose output grows with quality and area —
 * the same shape a real JPEG has, without needing a phone to produce one.
 */
class CompressorTest {

    /** quality × area × 4000 bytes, so 95 at full scale is about 380 KB. */
    private val fake = Encoder { quality, scale ->
        ByteArray((quality * scale * scale * 4000).toInt().coerceAtLeast(1))
    }

    @Test
    fun hitsTheTargetWithoutGoingOver() {
        val result = Compressor(fake).toTarget(15 * 1024)
        assertNotNull(result)
        assertTrue("must not exceed the budget", result!!.size <= 15 * 1024)
        assertTrue("should use most of the budget", result.size > 6 * 1024)
    }

    @Test
    fun alreadySmallEnoughIsLeftAlone() {
        val result = Compressor(fake).toTarget(2 * 1024 * 1024)
        assertEquals(95, result!!.quality)
        assertEquals(1.0f, result.scale, 0.001f)
    }

    @Test
    fun impossibleTargetsReturnNothing() {
        assertNull(Compressor(fake).toTarget(10))
        assertNull(Compressor(fake).toTarget(0))
    }

    @Test
    fun aBiggerBudgetIsSpentBeforeResolutionIsGivenUp() {
        // The trade is pixels against quality, not quality alone: a bigger budget first restores
        // full resolution (possibly at a lower quality number), which looks better than a sharp
        // but tiny image. So the invariant is scale and bytes, not the quality integer.
        val small = Compressor(fake).toTarget(10 * 1024)!!
        val large = Compressor(fake).toTarget(60 * 1024)!!
        assertTrue("bigger budget keeps at least as much resolution", large.scale >= small.scale)
        assertTrue("bigger budget uses at least as many bytes", large.size >= small.size)
        assertTrue("and never exceeds it", large.size <= 60 * 1024)
    }

    @Test
    fun spokenSizesParse() {
        assertEquals(15 * 1024, Sizes.parse("15 kb"))
        assertEquals(15 * 1024, Sizes.parse("compress this to 15KB"))
        assertEquals(2 * 1024 * 1024, Sizes.parse("2 mb"))
        assertEquals(500, Sizes.parse("500 bytes"))
        assertNull(Sizes.parse("make it small"))
    }

    @Test
    fun humanReadableSizes() {
        assertEquals("15 KB", Sizes.human(15 * 1024))
        assertEquals("2.0 MB", Sizes.human(2 * 1024 * 1024))
        assertEquals("900 bytes", Sizes.human(900))
    }
}
