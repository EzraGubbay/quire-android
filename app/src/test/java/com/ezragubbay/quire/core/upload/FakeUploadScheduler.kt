package com.ezragubbay.quire.core.upload

class FakeUploadScheduler : UploadScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    var cancelledAll = false

    override fun enqueue(uploadId: String) {
        enqueued += uploadId
    }

    override fun cancel(uploadId: String) {
        cancelled += uploadId
    }

    override fun cancelAll() {
        cancelledAll = true
    }
}
