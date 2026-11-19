package com.ezragubbay.quire.feature.viewer.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class StreamCopyTest {
    @Test
    fun copiesAllBytesAndReturnsCount() {
        val data = ByteArray(100_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        assertEquals(100_000L, StreamCopy.copy(ByteArrayInputStream(data), out))
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun emptyInputCopiesNothing() {
        val empty = ByteArrayInputStream(ByteArray(0))
        assertEquals(0L, StreamCopy.copy(empty, ByteArrayOutputStream()))
    }
}
