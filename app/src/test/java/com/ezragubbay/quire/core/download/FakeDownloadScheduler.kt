package com.ezragubbay.quire.core.download

class FakeDownloadScheduler : DownloadScheduler {
    val enqueued = mutableListOf<List<Any>>()
    val cancelled = mutableListOf<String>()
    var cancelledAll = false

    override fun enqueue(
        courseSlug: String,
        fileId: String,
        format: String,
        wifiOnly: Boolean,
    ) {
        enqueued += listOf(courseSlug, fileId, format, wifiOnly)
    }

    override fun cancel(fileId: String) {
        cancelled += fileId
    }

    override fun cancelAll() {
        cancelledAll = true
    }
}
