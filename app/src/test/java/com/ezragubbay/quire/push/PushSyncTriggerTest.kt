package com.ezragubbay.quire.push

import androidx.work.NetworkType
import androidx.work.OutOfQuotaPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSyncTriggerTest {
    @Test
    fun filesAddedSync_isExpedited_degradingToOrdinaryWorkOutOfQuota() {
        val spec = WorkManagerPushSyncTrigger.request(expedited = true, wifiOnly = false).workSpec

        assertTrue(spec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals("com.ezragubbay.quire.sync.SyncWorker", spec.workerClassName)
    }

    @Test
    fun deletedMessagesSync_isOrdinary_andHonoursWifiOnly() {
        val spec = WorkManagerPushSyncTrigger.request(expedited = false, wifiOnly = true).workSpec

        assertFalse(spec.expedited)
        assertEquals(NetworkType.UNMETERED, spec.constraints.requiredNetworkType)
    }

    @Test
    fun expeditedOnlyFromAndroid12() {
        assertFalse(WorkManagerPushSyncTrigger.supportsExpedited(30))
        assertTrue(WorkManagerPushSyncTrigger.supportsExpedited(31))
        assertTrue(WorkManagerPushSyncTrigger.supportsExpedited(36))
    }
}
