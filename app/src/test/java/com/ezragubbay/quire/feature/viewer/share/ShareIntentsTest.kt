package com.ezragubbay.quire.feature.viewer.share

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {
    private val uri = Uri.parse("content://com.ezragubbay.quire.fileprovider/downloads/c/f1.pdf")

    @Test
    fun sendIntentCarriesStreamMimeAndReadGrant() {
        val intent = ShareIntents.send(uri, "application/pdf", "Lecture 1")
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals(uri, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("Lecture 1", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun createDocumentIntentUsesMimeAndSuggestedName() {
        val intent = ShareIntents.createDocument("application/pdf", "Lecture 1.pdf")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/pdf", intent.type)
        assertEquals("Lecture 1.pdf", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
    }
}
