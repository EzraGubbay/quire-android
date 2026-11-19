package com.ezragubbay.quire.feature.downloads

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {
    @Test
    fun formatsBytesAcrossUnits() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun negativeIsZero() = assertEquals("0 B", formatBytes(-5))
}
