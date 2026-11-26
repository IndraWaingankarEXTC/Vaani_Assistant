package com.vaani.assistant

import com.vaani.assistant.convert.ConvertPlan
import com.vaani.assistant.vault.VaultKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAndConvertTest {

    @Test
    fun spokenFieldNamesResolve() {
        assertEquals("address", VaultKeys.normalise("my address"))
        assertEquals("email", VaultKeys.normalise("email"))
        assertEquals("email", VaultKeys.normalise("e mail id"))
        assertEquals("phone", VaultKeys.normalise("mobile number"))
        assertEquals("pincode", VaultKeys.normalise("pin code"))
        assertEquals("upi", VaultKeys.normalise("upi id"))
    }

    @Test
    fun nonsenseIsNotAField() {
        assertEquals(null, VaultKeys.normalise("banana"))
        assertEquals(null, VaultKeys.normalise(""))
    }

    @Test
    fun conversionIsChosenByExtension() {
        assertEquals(ConvertPlan.Kind.IMAGES_TO_PDF, ConvertPlan.kindFor("scan.JPG"))
        assertEquals(ConvertPlan.Kind.IMAGES_TO_PDF, ConvertPlan.kindFor("photo.png"))
        assertEquals(ConvertPlan.Kind.TEXT_TO_PDF, ConvertPlan.kindFor("notes.txt"))
        assertEquals(ConvertPlan.Kind.PDF_TO_IMAGES, ConvertPlan.kindFor("doc.pdf"))
        assertEquals(ConvertPlan.Kind.UNSUPPORTED, ConvertPlan.kindFor("slides.pptx"))
    }

    @Test
    fun outputKeepsTheStemAndNeverOverwrites() {
        assertEquals("scan.pdf", ConvertPlan.outputName("scan.jpg", "pdf"))
        assertEquals("doc.png", ConvertPlan.outputName("doc.pdf", "png"))
        assertEquals("scan.pdf", ConvertPlan.outputName("scan.jpeg", "pdf"))
    }

    @Test
    fun imagesAreCentredAndKeepAspectRatio() {
        val wide = ConvertPlan.fitInside(2000, 1000)
        val tall = ConvertPlan.fitInside(1000, 2000)
        assertTrue("wide image is wider than tall", wide[2] > wide[3])
        assertTrue("tall image is taller than wide", tall[3] > tall[2])
        assertTrue("fits inside the page", wide[0] + wide[2] <= ConvertPlan.pageWidth)
        assertTrue("fits inside the page", tall[1] + tall[3] <= ConvertPlan.pageHeight)
        assertEquals("centred horizontally", (ConvertPlan.pageWidth - wide[2]) / 2, wide[0])
    }

    @Test
    fun linesPerPageIsSane() {
        val lines = ConvertPlan.linesPerPage()
        assertTrue(lines in 40..60)
    }
}
