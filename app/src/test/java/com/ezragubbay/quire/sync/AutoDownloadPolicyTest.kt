package com.ezragubbay.quire.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPolicyTest {
    private fun file(
        id: String,
        size: Long,
    ) = PinFile(id, "t", true, size)

    private fun ids(files: List<PinFile>) = files.map { it.fileId }

    @Test
    fun defaultCap_isTwoGigabytes() {
        assertEquals(2L * 1024 * 1024 * 1024, AutoDownloadPolicy.DEFAULT_CAP_BYTES)
    }

    @Test
    fun everythingFits_allAllowed_notPaused() {
        val plan = AutoDownloadPolicy.plan(listOf(file("a", 10), file("b", 20)), usedBytes = 0, capBytes = 100)
        assertEquals(listOf("a", "b"), ids(plan.allowed))
        assertFalse(plan.paused)
    }

    @Test
    fun exactlyAtCap_isAllowed() {
        val plan = AutoDownloadPolicy.plan(listOf(file("a", 60)), usedBytes = 40, capBytes = 100)
        assertEquals(listOf("a"), ids(plan.allowed))
    }

    @Test
    fun firstFileOverCap_pausesEverythingAfterIt() {
        val plan =
            AutoDownloadPolicy.plan(listOf(file("a", 50), file("big", 80), file("c", 1)), usedBytes = 0, capBytes = 100)
        assertEquals(listOf("a"), ids(plan.allowed))
        assertEquals(listOf("big", "c"), ids(plan.blocked))
        assertTrue(plan.paused)
    }

    @Test
    fun alreadyUsedBytes_countTowardTheCap() {
        val plan = AutoDownloadPolicy.plan(listOf(file("a", 30)), usedBytes = 80, capBytes = 100)
        assertTrue(plan.allowed.isEmpty())
        assertTrue(plan.paused)
    }

    @Test
    fun noCandidates_isNotPaused_evenWhenOverCap() {
        val plan = AutoDownloadPolicy.plan(emptyList(), usedBytes = 500, capBytes = 100)
        assertFalse(plan.paused)
    }

    @Test
    fun isOverCap_isStrict() {
        assertFalse(AutoDownloadPolicy.isOverCap(100, 100))
        assertTrue(AutoDownloadPolicy.isOverCap(101, 100))
    }
}
