package com.ezragubbay.quire.core.upload

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [UploadQueue] for view-model tests. */
class FakeUploadQueue : UploadQueue {
    val items = MutableStateFlow<List<UploadItem>>(emptyList())
    var nextResult = EnqueueResult(emptyList(), emptyList(), emptyList())
    val enqueued = mutableListOf<Triple<String, UploadTab, List<Uri>>>()
    val retried = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    val dismissed = mutableListOf<String>()
    var clearedAll = 0

    override fun observe(courseSlug: String): Flow<List<UploadItem>> = items

    override suspend fun enqueue(
        courseSlug: String,
        tab: UploadTab,
        uris: List<Uri>,
    ): EnqueueResult {
        enqueued += Triple(courseSlug, tab, uris)
        return nextResult
    }

    override suspend fun retry(id: String) {
        retried += id
    }

    override suspend fun cancel(id: String) {
        cancelled += id
    }

    override suspend fun dismiss(id: String) {
        dismissed += id
    }

    override suspend fun clearAll() {
        clearedAll++
    }
}
