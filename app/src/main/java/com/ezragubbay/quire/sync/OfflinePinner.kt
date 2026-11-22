package com.ezragubbay.quire.sync

import com.ezragubbay.quire.core.database.DownloadDao
import com.ezragubbay.quire.core.database.DownloadEntity
import com.ezragubbay.quire.core.database.FileDao
import com.ezragubbay.quire.core.download.DownloadRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** What the UI does with offline pinning. */
interface PinController {
    fun observePinned(): Flow<Set<String>>

    /** True while the storage cap is holding auto-download back (or stored bytes already exceed it). */
    fun observeCapWarning(): Flow<Boolean>

    /** Pins the course and queues downloads for every PDF-renderable cached file. */
    suspend fun pin(slug: String)

    /** Unpins; [removeFiles] also deletes the course's downloads, otherwise they stay as plain downloads. */
    suspend fun unpin(
        slug: String,
        removeFiles: Boolean,
    )
}

/** The sync pass's view of pinning: snapshot before a course refresh, reconcile after it. */
interface PinnedSync {
    /** The course's files before a refresh, or null when it is not pinned (nothing to do after it). */
    suspend fun snapshot(slug: String): List<PinFile>?

    /** Auto-downloads what changed since [before]; deletes what the server dropped. */
    suspend fun afterRefresh(
        slug: String,
        before: List<PinFile>,
    )

    object None : PinnedSync {
        override suspend fun snapshot(slug: String): List<PinFile>? = null

        override suspend fun afterRefresh(
            slug: String,
            before: List<PinFile>,
        ) = Unit
    }
}

/** The cached files and the downloads [OfflinePinner] works over (seams so the logic is unit-testable). */
interface PinStorage {
    suspend fun files(slug: String): List<PinFile>

    suspend fun held(slug: String): List<HeldDownload>

    suspend fun usedBytes(): Long

    fun observeUsedBytes(): Flow<Long>

    suspend fun enqueue(
        slug: String,
        fileId: String,
        wifiOnly: Boolean,
    )

    suspend fun remove(fileId: String)
}

class RoomPinStorage(
    private val files: FileDao,
    private val dao: DownloadDao,
    private val downloads: DownloadRepository,
) : PinStorage {
    override suspend fun files(slug: String) =
        files.getForCourse(slug).map { PinFile(it.fileId, it.updatedAt, it.hasPdf, it.sizeBytes) }

    override suspend fun held(slug: String) =
        dao.getAll().filter { it.courseSlug == slug }.map { HeldDownload(it.fileId, it.state == DownloadEntity.FAILED) }

    override suspend fun usedBytes() = downloads.storageBytes()

    override fun observeUsedBytes() = downloads.observeStorageBytes()

    override suspend fun enqueue(
        slug: String,
        fileId: String,
        wifiOnly: Boolean,
    ) = downloads.enqueue(slug, fileId, PDF, wifiOnly)

    override suspend fun remove(fileId: String) = downloads.remove(fileId)

    private companion object {
        const val PDF = "pdf"
    }
}

/**
 * Offline pinning: a pinned course keeps its PDF-renderable files on the phone. Pure rules live in
 * [PinnedCourseDiff] and [AutoDownloadPolicy]; this class applies them to storage and settings.
 * [wifiOnly] is read per run so the sync 'Wi-Fi only' switch also governs auto-download.
 */
class OfflinePinner(
    private val settings: OfflineSettingsStore,
    private val storage: PinStorage,
    private val wifiOnly: suspend () -> Boolean,
) : PinController,
    PinnedSync {
    override fun observePinned(): Flow<Set<String>> = settings.settings.map { it.pinned }.distinctUntilChanged()

    override fun observeCapWarning(): Flow<Boolean> =
        combine(settings.settings, storage.observeUsedBytes()) { s, used ->
            s.pinned.isNotEmpty() && (s.capPausedSlugs.isNotEmpty() || AutoDownloadPolicy.isOverCap(used, s.capBytes))
        }.distinctUntilChanged()

    override suspend fun pin(slug: String) {
        settings.addPin(slug)
        reconcile(slug, emptyList())
    }

    override suspend fun unpin(
        slug: String,
        removeFiles: Boolean,
    ) {
        settings.removePin(slug)
        if (removeFiles) storage.held(slug).forEach { storage.remove(it.fileId) }
    }

    override suspend fun snapshot(slug: String): List<PinFile>? =
        if (slug in settings.current().pinned) storage.files(slug) else null

    override suspend fun afterRefresh(
        slug: String,
        before: List<PinFile>,
    ) {
        if (slug in settings.current().pinned) reconcile(slug, before)
    }

    private suspend fun reconcile(
        slug: String,
        before: List<PinFile>,
    ) {
        val diff = PinnedCourseDiff.compute(before, storage.files(slug), storage.held(slug))
        // Removed files go, and so do the stale bytes of changed ones (an enqueue would keep a finished download).
        (diff.removed + diff.changed.map { it.fileId }).forEach { storage.remove(it) }
        val plan = AutoDownloadPolicy.plan(diff.toDownload, storage.usedBytes(), settings.current().capBytes)
        val wifi = wifiOnly()
        // Batch first: a download that settles instantly must find its batch already open.
        settings.startBatch(slug, plan.allowed.map { it.fileId })
        plan.allowed.forEach { storage.enqueue(slug, it.fileId, wifi) }
        settings.setCapPaused(slug, plan.paused)
    }
}
