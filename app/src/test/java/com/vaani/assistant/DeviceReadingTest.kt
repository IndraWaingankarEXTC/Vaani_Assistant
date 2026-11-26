package com.vaani.assistant

import com.vaani.assistant.lens.DeviceReading
import com.vaani.assistant.lens.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The accuracy story: labels give the type, the printed model number gives the identity. */
class DeviceReadingTest {

    @Test
    fun readsAHeadphoneLabel() {
        val r = DeviceReading.compose(
            listOf("Headphones" to 0.87f, "Audio equipment" to 0.61f),
            listOf("SONY", "WH-CH720N", "Bluetooth", "1000mAh")
        )
        assertEquals("Sony WH-CH720N", r.title)
        assertEquals("Sony", r.brand)
        assertEquals("WH-CH720N", r.model)
        assertEquals("Headphones", r.category)
        assertTrue("model should push confidence up", r.confidence > 0.87f)
        assertTrue(r.usedText)
    }

    @Test
    fun readsAPhoneModel() {
        val r = DeviceReading.compose(
            listOf("Mobile phone" to 0.7f),
            listOf("SAMSUNG", "SM-A546E", "5G", "2024")
        )
        assertEquals("Samsung SM-A546E", r.title)
        assertEquals("SM-A546E", r.model)
    }

    @Test
    fun specsAreNotModelNumbers() {
        // "1000mAh", "5V" and a year look like models to a naive regex.
        assertNull(DeviceReading.findModel(listOf("1000mAh", "5V", "2024")))
        assertNull(DeviceReading.findModel(listOf("Capacity 5000mAh", "Output 20W")))
    }

    @Test
    fun labelsOnlyWhenThereIsNoText() {
        val r = DeviceReading.compose(listOf("Laptop" to 0.55f), emptyList())
        assertEquals("Laptop", r.title)
        assertNull(r.model)
        assertFalse(r.usedText)
    }

    @Test
    fun admitsWhenItDoesNotKnow() {
        val r = DeviceReading.compose(emptyList(), emptyList())
        assertEquals(Reading.UNKNOWN, r.title)
        assertEquals(0f, r.confidence, 0.001f)
        assertTrue(r.spoken().contains("can't identify"))
    }

    @Test
    fun aHyphenatedModelBeatsAPlainOne() {
        val model = DeviceReading.findModel(listOf("Model A12", "XR-55X90K"))
        assertEquals("XR-55X90K", model)
    }

    @Test
    fun brandCanComeFromAnyLine() {
        assertEquals("boat", DeviceReading.findBrand(listOf("boAt", "Airdopes 141")))
        assertEquals("sony", DeviceReading.findBrand(listOf("Model: WH-CH720N", "SONY")))
    }
}
