package com.ezragubbay.quire.feature.viewer

import com.ezragubbay.quire.core.download.DownloadState

/** What the viewer's download toggle does for the current state. */
enum class ToggleAction { Enqueue, Remove }

/** Which `download-state-*` indicator the viewer shows. */
enum class DownloadIndicator(
    val tag: String,
) {
    Idle("download-state-idle"),
    Running("download-state-running"),
    Done("download-state-done"),
    Failed("download-state-failed"),
}

object DownloadToggle {
    /** Idle and Failed start (or retry) a persisted download; Running cancels it; Done removes it. */
    fun actionFor(state: DownloadState): ToggleAction =
        when (state) {
            DownloadState.Idle, is DownloadState.Failed -> ToggleAction.Enqueue
            is DownloadState.Running, is DownloadState.Done -> ToggleAction.Remove
        }

    fun indicatorFor(state: DownloadState): DownloadIndicator =
        when (state) {
            DownloadState.Idle -> DownloadIndicator.Idle
            is DownloadState.Running -> DownloadIndicator.Running
            is DownloadState.Done -> DownloadIndicator.Done
            is DownloadState.Failed -> DownloadIndicator.Failed
        }
}
