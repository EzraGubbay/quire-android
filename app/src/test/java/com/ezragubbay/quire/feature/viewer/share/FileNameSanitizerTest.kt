package com.ezragubbay.quire.feature.viewer.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {
    @Test
    fun appendsExtension() = assertEquals("Lecture 1.pdf", FileNameSanitizer.suggestedName("Lecture 1", "pdf"))

    @Test
    fun doesNotDoubleExistingExtension() =
        assertEquals("Lecture 1.pdf", FileNameSanitizer.suggestedName("Lecture 1.PDF", "pdf"))

    @Test
    fun replacesSlashesAndReservedCharacters() =
        assertEquals("a_b_c_d_e_f_g_h_i.pdf", FileNameSanitizer.suggestedName("a/b\\c:d*e?f\"g<h>i", "pdf"))

    @Test
    fun keepsHebrew() = assertEquals("הרצאה 1.pdf", FileNameSanitizer.suggestedName("הרצאה 1", "pdf"))

    @Test
    fun limitsVeryLongNamesKeepingExtension() {
        val name = FileNameSanitizer.suggestedName("x".repeat(500), "pdf")
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.length <= FileNameSanitizer.MAX_LENGTH)
    }

    @Test
    fun blankOrDotsFallBack() {
        assertEquals("Quire file.pdf", FileNameSanitizer.suggestedName("  ", "pdf"))
        assertEquals("Quire file.pdf", FileNameSanitizer.suggestedName("...", "pdf"))
    }

    @Test
    fun stripsControlCharacters() = assertEquals("a__b.pdf", FileNameSanitizer.suggestedName("a\u0000\nb", "pdf"))
}
