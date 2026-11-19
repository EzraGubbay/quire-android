package com.ezragubbay.quire.navigation

import kotlinx.serialization.Serializable

/** The course screen for [slug]; pushed over the shell from the Courses list. */
@Serializable
data class CourseRoute(
    val slug: String,
)

/** Full-screen search; pushed over the shell from the Courses top bar. */
@Serializable
data object SearchRoute

/** The read-only note reader for [noteId] of course [courseSlug]; pushed over the shell from the Notes tab. */
@Serializable
data class NoteReaderRoute(
    val courseSlug: String,
    val noteId: String,
)

/** The PDF viewer for [fileId] of course [courseSlug]; pushed over the shell (open-file flow, #38, targets it). */
@Serializable
data class ViewerRoute(
    val courseSlug: String,
    val fileId: String,
)

/** One course's chat threads (the course screen's Ask destination); pushed over the shell. */
@Serializable
data class CourseThreadsRoute(
    val slug: String,
)

/**
 * The chat conversation for [threadId] of course [courseSlug]; pushed over the shell from a thread row, or after
 * "New chat" created the thread (the thread is created first, then this route opens on its id). [documentTitle] is set
 * when opened from the viewer's 'Ask about this document': the conversation then shows a header naming it.
 */
@Serializable
data class ConversationRoute(
    val courseSlug: String,
    val threadId: String,
    val documentTitle: String? = null,
)
