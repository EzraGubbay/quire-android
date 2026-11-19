package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.download.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadToggleTest {
    @Test fun idle_enqueues() = assertEquals(ToggleAction.Enqueue, DownloadToggle.actionFor(DownloadState.Idle))

    @Test fun failed_retries() = assertEquals(ToggleAction.Enqueue, DownloadToggle.actionFor(DownloadState.Failed("x")))

    @Test fun running_cancelsByRemoving() =
        assertEquals(ToggleAction.Remove, DownloadToggle.actionFor(DownloadState.Running(1, 10)))

    @Test fun done_removes() = assertEquals(ToggleAction.Remove, DownloadToggle.actionFor(DownloadState.Done("/p", 1)))

    @Test fun indicators() {
        assertEquals("download-state-idle", DownloadToggle.indicatorFor(DownloadState.Idle).tag)
        assertEquals("download-state-running", DownloadToggle.indicatorFor(DownloadState.Running(0, null)).tag)
        assertEquals("download-state-done", DownloadToggle.indicatorFor(DownloadState.Done("/p", 1)).tag)
        assertEquals("download-state-failed", DownloadToggle.indicatorFor(DownloadState.Failed("x")).tag)
    }
}
