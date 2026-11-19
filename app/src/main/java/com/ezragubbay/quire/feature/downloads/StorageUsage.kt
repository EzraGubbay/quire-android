package com.ezragubbay.quire.feature.downloads

private const val KILO = 1024L
private val UNITS = listOf("KB", "MB", "GB", "TB")

/** Human-readable size (1024-based, one decimal), e.g. `1.5 MB`. Pure, so it is unit-testable. */
fun formatBytes(bytes: Long): String {
    if (bytes < KILO) return "${bytes.coerceAtLeast(0)} B"
    var value = bytes.toDouble()
    var unit = -1
    while (value >= KILO && unit < UNITS.lastIndex) {
        value /= KILO
        unit++
    }
    return "%.1f %s".format(java.util.Locale.US, value, UNITS[unit])
}
