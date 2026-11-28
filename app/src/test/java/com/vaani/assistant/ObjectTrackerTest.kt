package com.vaani.assistant

import com.vaani.assistant.lens.Detection
import com.vaani.assistant.lens.ObjectTracker
import com.vaani.assistant.lens.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Objects keep their identity as they move, so their callouts can follow them. */
class ObjectTrackerTest {

    private fun box(l: Float, t: Float, r: Float, b: Float, label: String? = "headphones") =
        Detection(l, t, r, b, label)

    private fun reading(title: String, category: String? = "Headphones") = Reading(
        title = title, brand = null, model = null, category = category,
        details = emptyList(), confidence = 0.8f, usedText = true
    )

    @Test
    fun theSameObjectKeepsItsIdAsItMoves() {
        val tracker = ObjectTracker()
        val first = tracker.update(listOf(box(100f, 100f, 300f, 300f)), now = 0)
        assertEquals(1, first.size)

        // Moved a little to the right — still the same thing.
        val second = tracker.update(listOf(box(140f, 110f, 340f, 310f)), now = 400)
        assertEquals(1, second.size)
        assertEquals(first[0].id, second[0].id)
    }

    @Test
    fun aDifferentObjectGetsANewId() {
        val tracker = ObjectTracker(maxAgeMs = 1_000)
        tracker.update(listOf(box(0f, 0f, 100f, 100f)), now = 0)

        // Immediately afterwards the first object still lingers — deliberate, so a single missed
        // frame does not make its callout flicker — but the newcomer is a separate object.
        val during = tracker.update(listOf(box(500f, 500f, 700f, 700f)), now = 300)
        assertEquals(2, during.size)
        assertEquals(2, during.map { it.id }.maxOrNull())

        // Keep seeing the newcomer; the object that left ages out after the grace period.
        tracker.update(listOf(box(500f, 500f, 700f, 700f)), now = 700)
        val after = tracker.update(listOf(box(500f, 500f, 700f, 700f)), now = 1_100)
        assertEquals(1, after.size)
        assertEquals(2, after[0].id)
    }

    @Test
    fun twoObjectsStaySeparate() {
        val tracker = ObjectTracker()
        val tracks = tracker.update(
            listOf(box(0f, 0f, 100f, 100f), box(400f, 400f, 600f, 600f)), now = 0
        )
        assertEquals(2, tracks.size)
        assertEquals(2, tracks.map { it.id }.distinct().size)
    }

    @Test
    fun aWobbleDoesNotDuplicate() {
        val tracker = ObjectTracker(maxAgeMs = 1_500)
        tracker.update(listOf(box(100f, 100f, 300f, 300f)), now = 0)
        // Missed one frame entirely, then reappears.
        val after = tracker.update(listOf(box(105f, 100f, 305f, 300f)), now = 600)
        assertEquals(1, after.size)
        assertEquals(1, after[0].id)
    }

    @Test
    fun objectsThatLeaveAreForgotten() {
        val tracker = ObjectTracker(maxAgeMs = 1_000)
        tracker.update(listOf(box(100f, 100f, 300f, 300f)), now = 0)
        val later = tracker.update(emptyList(), now = 5_000)
        assertTrue(later.isEmpty())
    }

    @Test
    fun identificationSticksToTheObject() {
        val tracker = ObjectTracker()
        tracker.update(listOf(box(100f, 100f, 300f, 300f)), now = 0)
        val claimed = tracker.claimNextToIdentify()
        assertNotNull(claimed)
        tracker.identify(claimed!!.id, reading("Sony WH-CH720N"), now = 100)

        val moved = tracker.update(listOf(box(150f, 120f, 350f, 320f)), now = 300)
        assertEquals("Sony WH-CH720N", moved[0].title)
        assertTrue(moved[0].identified)
        // And it is not queued for reading again.
        assertNull(tracker.claimNextToIdentify())
    }

    @Test
    fun tinyObjectsAreNotWorthReading() {
        val tracker = ObjectTracker()
        // A speck in a 1000x1000 frame.
        tracker.update(listOf(box(10f, 10f, 40f, 40f)), now = 0)
        assertNull(tracker.claimNextToIdentify(minAreaFraction = 0.05f, frameArea = 1_000_000f))
    }

    @Test
    fun tappingPicksTheInnermostObject() {
        val tracker = ObjectTracker()
        tracker.update(
            listOf(box(0f, 0f, 800f, 800f, "shelf"), box(300f, 300f, 500f, 500f, "phone")),
            now = 0
        )
        val hit = tracker.hitTest(400f, 400f)
        assertEquals("phone", hit?.box?.label)
    }

    @Test
    fun iouIsSane() {
        val a = box(0f, 0f, 100f, 100f)
        assertEquals(1f, ObjectTracker.iou(a, a), 0.001f)
        assertEquals(0f, ObjectTracker.iou(a, box(200f, 200f, 300f, 300f)), 0.001f)
        val half = ObjectTracker.iou(a, box(0f, 0f, 50f, 100f))
        assertEquals(0.5f, half, 0.01f)
    }
}
