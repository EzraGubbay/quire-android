package com.ezragubbay.quire.core.upload

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UploadSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var work: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        work = WorkManager.getInstance(context)
    }

    private fun infos(id: String) = work.getWorkInfosForUniqueWork(WorkManagerUploadScheduler.uniqueName(id)).get()

    @Test
    fun enqueue_usesUniqueNamedWorkWithTagAndConnectedConstraint() {
        WorkManagerUploadScheduler(context).enqueue("u1")

        val info = infos("u1").single()
        assertTrue(WorkManagerUploadScheduler.TAG in info.tags)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun enqueueTwice_keepsTheFirst() {
        val s = WorkManagerUploadScheduler(context)
        s.enqueue("u1")
        val first = infos("u1").single().id

        s.enqueue("u1")

        assertEquals(first, infos("u1").single().id)
    }

    @Test
    fun cancel_andCancelAll() {
        val s = WorkManagerUploadScheduler(context)
        s.enqueue("u1")
        s.enqueue("u2")

        s.cancel("u1")
        assertEquals(WorkInfo.State.CANCELLED, infos("u1").single().state)
        assertEquals(WorkInfo.State.ENQUEUED, infos("u2").single().state)

        s.cancelAll()
        assertEquals(WorkInfo.State.CANCELLED, infos("u2").single().state)
    }
}
