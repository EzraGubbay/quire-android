package com.ezragubbay.quire.core.data.course

import com.ezragubbay.quire.core.database.QuireDatabase
import com.ezragubbay.quire.core.database.escapeLike

/**
 * Searches the Room cache (no network, so it works offline): courses by name/code and files by title/type,
 * case-insensitive, prefix matches first.
 */
class SearchRepository(
    private val db: QuireDatabase,
) {
    /** Matches for [query]; a blank query returns nothing. */
    suspend fun search(
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults(emptyList(), emptyList())
        val pattern = escapeLike(q)
        return SearchResults(
            db.searchDao().searchCourses(pattern, limit).map { it.toModel() },
            db.searchDao().searchFiles(pattern, limit).map { it.toModel() },
        )
    }

    private companion object {
        const val SEARCH_LIMIT = 50
    }
}
