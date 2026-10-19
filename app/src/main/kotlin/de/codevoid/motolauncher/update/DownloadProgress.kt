package de.codevoid.motolauncher.update

/**
 * Turns a download's running byte count into progress text such as "45% · 2.2 MB/s",
 * at most once per [intervalMs], with the rate measured since the previous report.
 */
class DownloadProgress(
    private val intervalMs: Long = 500,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastAt = now()
    private var lastBytes = 0L
    private var hasReported = false

    /** Returns the text to show, or null while the previous report is still current. */
    fun report(written: Long, total: Long): String? {
        val at = now()
        if (hasReported && at - lastAt < intervalMs) return null
        val elapsedMs = (at - lastAt).coerceAtLeast(1)
        val megabytesPerSecond = (written - lastBytes) * 1000.0 / elapsedMs / (1024 * 1024)
        lastAt = at
        lastBytes = written
        hasReported = true
        val percent = if (total > 0) "${written * 100 / total}% · " else ""
        return percent + "%.1f MB/s".format(megabytesPerSecond)
    }
}
