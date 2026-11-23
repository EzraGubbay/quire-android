package com.ezragubbay.quire.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/** What [PushMessageHandler.handle] did with one message. */
sealed interface PushHandleResult {
    /** Not understood by this app version (unknown `v`/`type`, missing ids): dropped. */
    data object Dropped : PushHandleResult

    /** The event's category is off in the app. */
    data object Suppressed : PushHandleResult

    data class Posted(
        val spec: PushNotificationSpec,
    ) : PushHandleResult

    /** Built but not shown: notifications are denied or off for the app. */
    data class NotPosted(
        val spec: PushNotificationSpec,
    ) : PushHandleResult
}

/**
 * Receives one FCM data message (ADR 0003 §4.3). `onMessageReceived` runs on FCM's background thread with a short
 * budget, so [handle] does all of its work synchronously, under one overall deadline of [deadlineMillis]. The name
 * lookups (course name; file or note title) run in parallel, each Room first and then the API; whatever has not
 * resolved by the deadline falls back to generic text, and the notification is posted in every case. No work
 * outlives the call: unfinished lookups are cancelled.
 */
class PushMessageHandler(
    private val preferences: PushPreferences,
    private val lookup: PushNamesLookup,
    private val factory: PushNotificationFactory,
    private val notifier: PushNotifier,
    private val sync: PushSyncTrigger,
    private val deadlineMillis: Long = DEADLINE_MS,
) {
    fun handle(data: Map<String, String>): PushHandleResult {
        val event = PushPayloadParser.parse(data)
        if (event == null) {
            // Ids only, never values: the payload carries no secrets, but keep logs minimal.
            PushLog.info("dropped push: type=${data["type"]} v=${data["v"]}")
            return PushHandleResult.Dropped
        }
        return runBlocking { handle(event, System.currentTimeMillis() + deadlineMillis) }
    }

    private suspend fun handle(
        event: PushEvent,
        deadline: Long,
    ): PushHandleResult {
        val category = event.category
        // A preference read that misses the deadline counts as "on": better one extra notification than a lost one.
        if (category != null && withTimeoutOrNull(remaining(deadline)) { preferences.isEnabled(category) } == false) {
            return PushHandleResult.Suppressed
        }
        val spec = factory.build(event, resolveNames(event, deadline))
        if (event is PushEvent.FilesAdded) {
            withTimeoutOrNull(remaining(deadline)) { sync.onFilesAdded() }
        }
        return if (notifier.post(spec)) PushHandleResult.Posted(spec) else PushHandleResult.NotPosted(spec)
    }

    /** Runs the lookups in parallel until [deadline]; unresolved names stay null. */
    private suspend fun resolveNames(
        event: PushEvent,
        deadline: Long,
    ): PushNames {
        val course = AtomicReference<String?>()
        val item = AtomicReference<FileName?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val jobs =
            listOfNotNull(
                courseSlug(event)?.let { slug -> scope.launch { course.set(safely { lookup.courseName(slug) }) } },
                itemLookup(event)?.let { block -> scope.launch { item.set(safely(block)) } },
            )
        withTimeoutOrNull(remaining(deadline)) { jobs.joinAll() }
        scope.cancel()
        val resolved = item.get()
        return PushNames(courseName = course.get(), itemTitle = resolved?.title, itemType = resolved?.type)
    }

    private fun courseSlug(event: PushEvent): String? =
        when (event) {
            is PushEvent.FilesAdded -> event.courseSlug
            is PushEvent.HomeworkDue -> event.courseSlug
            is PushEvent.NoteSynthesized -> event.courseSlug
            is PushEvent.Test -> null
        }

    /** The file / sheet / note title lookup an event needs, if any. */
    private fun itemLookup(event: PushEvent): (suspend () -> FileName?)? =
        when (event) {
            is PushEvent.FilesAdded ->
                if (event.count == 1) suspend { lookup.file(event.courseSlug, event.fileId) } else null
            is PushEvent.HomeworkDue ->
                event.fileId?.let { fileId ->
                    suspend { lookup.file(event.courseSlug, fileId) }
                }
            is PushEvent.NoteSynthesized ->
                if (event.ready) {
                    suspend { lookup.noteTitle(event.courseSlug, event.noteId)?.let { FileName(it, type = "") } }
                } else {
                    null
                }
            is PushEvent.Test -> null
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> safely(block: suspend () -> T?): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PushLog.warn("push name lookup failed: ${e.javaClass.simpleName}")
            null
        }

    private fun remaining(deadline: Long): Long = (deadline - System.currentTimeMillis()).coerceAtLeast(1)

    companion object {
        /** One overall budget for a message: FCM gives `onMessageReceived` about 10 s before it may be killed. */
        const val DEADLINE_MS = 8_000L
    }
}
