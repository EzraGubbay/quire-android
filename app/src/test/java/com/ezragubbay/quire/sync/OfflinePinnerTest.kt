package com.ezragubbay.quire.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class FakePinStorage : PinStorage {
    val files = mutableMapOf<String, List<PinFile>>()
    val held = mutableMapOf<String, MutableList<HeldDownload>>()
    val used = MutableStateFlow(0L)
    val enqueued = mutableListOf<Triple<String, String, Boolean>>()
    val removed = mutableListOf<String>()

    override suspend fun files(slug: String) = files[slug].orEmpty()

    override suspend fun held(slug: String) = held[slug].orEmpty().toList()

    override suspend fun usedBytes() = used.value

    override fun observeUsedBytes(): Flow<Long> = used

    override suspend fun enqueue(
        slug: String,
        fileId: String,
        wifiOnly: Boolean,
    ) {
        enqueued += Triple(slug, fileId, wifiOnly)
        held.getOrPut(slug) { mutableListOf() } += HeldDownload(fileId)
    }

    override suspend fun remove(fileId: String) {
        removed += fileId
        held.values.forEach { rows -> rows.removeAll { it.fileId == fileId } }
    }
}

class OfflinePinnerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: OfflineSettingsStore
    private val storage = FakePinStorage()
    private var wifi = false

    private fun file(
        id: String,
        updatedAt: String = "t1",
        size: Long = 10,
    ) = PinFile(id, updatedAt, true, size)

    private val pinner get() = OfflinePinner(settings, storage, wifiOnly = { wifi })

    @Before
    fun setUp() {
        settings =
            OfflineSettingsStore(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("o.preferences_pb") })
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> run(block: suspend () -> T): T = runBlocking { block() }

    @Test
    fun pin_enqueuesEveryPdfFile_andRecordsThePin() {
        storage.files["a"] = listOf(file("1"), file("2"))
        run { pinner.pin("a") }
        assertEquals(listOf("1", "2"), storage.enqueued.map { it.second })
        assertEquals(setOf("a"), run { settings.current().pinned })
    }

    @Test
    fun enqueue_honoursWifiOnly() {
        wifi = true
        storage.files["a"] = listOf(file("1"))
        run { pinner.pin("a") }
        assertTrue(storage.enqueued.single().third)
    }

    @Test
    fun pin_overCap_enqueuesNothing_andFlagsCapPause() {
        storage.files["a"] = listOf(file("1", size = 500))
        run {
            settings.setCapBytes(100)
            pinner.pin("a")
        }
        assertTrue(storage.enqueued.isEmpty())
        assertEquals(setOf("a"), run { settings.current().capPausedSlugs })
        assertTrue(run { pinner.observeCapWarning().first() })
    }

    @Test
    fun capPause_clearsOnceEverythingFits() {
        storage.files["a"] = listOf(file("1", size = 500))
        run {
            settings.setCapBytes(100)
            pinner.pin("a")
            settings.setCapBytes(1000)
            val before = pinner.snapshot("a")!!
            pinner.afterRefresh("a", before)
        }
        assertEquals(listOf("1"), storage.enqueued.map { it.second })
        assertTrue(run { settings.current().capPausedSlugs.isEmpty() })
        assertFalse(run { pinner.observeCapWarning().first() })
    }

    @Test
    fun afterRefresh_downloadsNew_redownloadsChanged_deletesRemoved() {
        storage.files["a"] = listOf(file("keep"), file("chg", "t1"), file("gone"))
        run { pinner.pin("a") }
        storage.enqueued.clear()
        val before = run { pinner.snapshot("a")!! }
        storage.files["a"] = listOf(file("keep"), file("chg", "t2"), file("new"))
        run { pinner.afterRefresh("a", before) }
        assertEquals(listOf("new", "chg"), storage.enqueued.map { it.second })
        assertEquals(setOf("gone", "chg"), storage.removed.toSet())
    }

    @Test
    fun unpinnedCourse_isNotTouchedBySync() {
        storage.files["a"] = listOf(file("1"))
        assertNull(run { pinner.snapshot("a") })
        run { pinner.afterRefresh("a", emptyList()) }
        assertTrue(storage.enqueued.isEmpty())
    }

    @Test
    fun unpin_keep_leavesDownloads_remove_deletesThem() {
        storage.files["a"] = listOf(file("1"), file("2"))
        run { pinner.pin("a") }
        run { pinner.unpin("a", removeFiles = false) }
        assertTrue(storage.removed.isEmpty())
        assertTrue(run { settings.current().pinned.isEmpty() })
        run { pinner.unpin("a", removeFiles = true) }
        assertEquals(listOf("1", "2"), storage.removed)
    }

    @Test
    fun capWarning_alsoShowsWhenStoredBytesExceedTheCap() {
        storage.files["a"] = listOf(file("1", size = 1))
        run {
            pinner.pin("a")
            settings.setCapBytes(100)
        }
        storage.used.value = 101
        assertTrue(run { pinner.observeCapWarning().first() })
    }
}
