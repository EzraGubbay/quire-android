package com.ezragubbay.quire.sync

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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var work: WorkManager
    private lateinit var scheduler: WorkManagerSyncScheduler

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        work = WorkManager.getInstance(context)
        scheduler = WorkManagerSyncScheduler(context)
    }

    private fun periodic(): List<WorkInfo> = work.getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_NAME).get()

    private fun live() = periodic().filter { !it.state.isFinished }

    @Test
    fun defaults_scheduleOneUniquePeriodicSixHourWork_needingNetworkAndBatteryNotLow() {
        scheduler.apply(SyncSettings())
        val info = live().single()
        assertEquals(TimeUnit.HOURS.toMillis(6), info.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertTrue(info.constraints.requiresBatteryNotLow())
        assertEquals("quire-sync", SyncScheduler.PERIODIC_NAME)
    }

    @Test
    fun wifiOnly_requiresUnmeteredNetwork() {
        scheduler.apply(SyncSettings(wifiOnly = true))
        assertEquals(NetworkType.UNMETERED, live().single().constraints.requiredNetworkType)
    }

    @Test
    fun changingInterval_reschedulesInPlace_withoutDuplicates() {
        scheduler.apply(SyncSettings())
        val id = live().single().id
        scheduler.apply(SyncSettings(interval = SyncInterval.Hours24))
        val info = live().single()
        assertEquals(id, info.id)
        assertEquals(TimeUnit.HOURS.toMillis(24), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun applyingTheSameSettingsTwice_isIdempotent() {
        scheduler.apply(SyncSettings(interval = SyncInterval.Hours12))
        val first = live().single()
        scheduler.apply(SyncSettings(interval = SyncInterval.Hours12))
        val second = live().single()
        assertEquals(first.id, second.id)
        assertEquals(first.periodicityInfo, second.periodicityInfo)
    }

    @Test
    fun disabling_cancelsThePeriodicWork() {
        scheduler.apply(SyncSettings())
        scheduler.apply(SyncSettings(enabled = false))
        assertTrue(live().isEmpty())
    }

    @Test
    fun intervalOff_cancelsThePeriodicWork() {
        scheduler.apply(SyncSettings())
        scheduler.apply(SyncSettings(interval = SyncInterval.Off))
        assertTrue(live().isEmpty())
    }

    @Test
    fun reEnabling_schedulesAgain() {
        scheduler.apply(SyncSettings(enabled = false))
        scheduler.apply(SyncSettings(enabled = true))
        assertEquals(1, live().size)
    }

    @Test
    fun startupSync_isOneUniqueConstrainedRequest() {
        scheduler.syncSoon(wifiOnly = false)
        scheduler.syncSoon(wifiOnly = false)
        val infos = work.getWorkInfosForUniqueWork(SyncScheduler.STARTUP_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    @Test
    fun staleness_runsOnlyWhenLastSuccessIsOlderThanAnHourOrNever() {
        val now = 10 * SyncPolicy.STALE_MS
        assertTrue(SyncPolicy.isStale(null, now))
        assertTrue(SyncPolicy.isStale(now - SyncPolicy.STALE_MS - 1, now))
        assertEquals(false, SyncPolicy.isStale(now - SyncPolicy.STALE_MS + 1, now))
    }
}
