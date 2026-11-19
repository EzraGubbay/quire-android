package com.ezragubbay.quire.core.database

private val SEMESTER = Regex("""(?i)(winter|spring|summer|fall|autumn)\D*(\d{4})""")
private const val SEASONS_PER_YEAR = 10
private const val WINTER = 1
private const val SPRING = 2
private const val SUMMER = 3
private const val FALL = 4

/** Sortable grouping key for a semester label like "Winter 2026": year-major, then season. 0 when unparseable. */
fun semesterKey(label: String?): Int {
    val match = label?.let(SEMESTER::find) ?: return 0
    val season =
        when (match.groupValues[1].lowercase()) {
            "winter" -> WINTER
            "spring" -> SPRING
            "summer" -> SUMMER
            else -> FALL
        }
    return match.groupValues[2].toInt() * SEASONS_PER_YEAR + season
}
