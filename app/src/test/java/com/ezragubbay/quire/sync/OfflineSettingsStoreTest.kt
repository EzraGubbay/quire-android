package com.ezragubbay.quire.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineSettingsStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: OfflineSettingsStore

    @Before
    fun setUp() {
        store =
            OfflineSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("o.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun defaults_areTwoGigabyteCap_notificationsOn_nothingPinned() {
        val s = run { store.current() }
        assertEquals(2L * 1024 * 1024 * 1024, s.capBytes)
        assertEquals(true, s.notify)
        assertEquals(emptySet<String>(), s.pinned)
    }

    @Test
    fun batch_summaryIsDueOnlyWhenTheLastFileSettles_countingSuccesses() {
        run {
            store.startBatch("a", listOf("1", "2", "3"))
            assertNull(store.settle("a", "1", true))
            assertNull(store.settle("a", "2", false))
            assertEquals(2, store.settle("a", "3", true))
        }
    }

    @Test
    fun batch_ofOnlyFailures_reportsZero() {
        run {
            store.startBatch("a", listOf("1"))
            assertEquals(0, store.settle("a", "1", false))
        }
    }

    @Test
    fun settlingAFileOutsideAnyBatch_isIgnored() {
        run { assertNull(store.settle("a", "manual", true)) }
    }

    @Test
    fun batchesAreIndependentPerCourse_andRestartCleanly() {
        run {
            store.startBatch("a", listOf("1"))
            store.startBatch("b", listOf("9"))
            assertEquals(1, store.settle("a", "1", true))
            store.startBatch("a", listOf("2"))
            assertEquals(1, store.settle("a", "2", true)) // a fresh batch does not inherit the old count
            assertEquals(1, store.settle("b", "9", true))
        }
    }

    @Test
    fun removePin_dropsPinPauseAndPendingBatch() {
        run {
            store.addPin("a")
            store.setCapPaused("a", true)
            store.startBatch("a", listOf("1"))
            store.removePin("a")
            val s = store.current()
            assertEquals(emptySet<String>(), s.pinned)
            assertEquals(emptySet<String>(), s.capPausedSlugs)
            assertNull(store.settle("a", "1", true))
        }
    }
}
