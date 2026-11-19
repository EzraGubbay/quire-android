package com.ezragubbay.quire.feature.courses.list

import com.ezragubbay.quire.core.database.semesterKey

/** Orders semester labels most recent first; labels in an unknown format (and null) sort last. */
object SemesterOrder : Comparator<String?> {
    override fun compare(
        a: String?,
        b: String?,
    ): Int {
        val byKey = semesterKey(b).compareTo(semesterKey(a))
        return when {
            byKey != 0 -> byKey
            a == null || b == null -> compareValues(a == null, b == null)
            else -> a.compareTo(b)
        }
    }
}

/** Stable tag segment for a semester label (`Winter 2026` -> `winter-2026`); `other` when there is no label. */
fun semesterSlug(label: String?): String =
    label
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("[^\\p{L}\\p{N}]+"), "-")
        ?.trim('-')
        ?.takeIf { it.isNotEmpty() }
        ?: "other"
