package com.umamit.app

/**
 * Tolerant parsers for `dumpsys` output in the instrumented tests. The formats differ
 * between Android versions, so each parser looks for stable markers and
 * returns the raw section it used, for the failure message.
 */
internal object Dumpsys {
    /** `Notification.FLAG_PROMOTED_ONGOING` (API 36) as a plain bit, so it can be checked on API 35 too. */
    const val FLAG_PROMOTED_ONGOING = 0x00040000

    /**
     * `Notification.FLAG_SILENT` (`@hide`, API 36): set by the system on
     * force-grouped notifications; System UI then suppresses their full-screen
     * intent and heads-up.
     */
    const val FLAG_SILENT = 0x00020000

    /** The lines of [dump] that contain any of [markers], trimmed, or "" when none do. */
    fun matchingLines(dump: String, markers: List<String>): String =
        dump.lines().filter { line -> markers.any(line::contains) }.joinToString("\n") { it.trim() }

    /**
     * The block of `dumpsys alarm` for the pending alarm whose tag is the
     * broadcast [action] (`tag=*walarm*:<action>`), or null.
     */
    fun alarmBlock(dump: String, action: String): String? {
        val lines = dump.lines()
        val tagIndex = lines.indexOfFirst { it.contains("tag=") && it.contains(action) }
        if (tagIndex < 0) return null
        // The block starts at the alarm header above the tag (e.g. "ELAPSED_WAKEUP #0: Alarm{…}")
        // and ends before the next header.
        val start = (tagIndex downTo 0).firstOrNull { lines[it].contains("Alarm{") } ?: tagIndex
        val end = (tagIndex + 1 until lines.size).firstOrNull { lines[it].contains("Alarm{") } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /** Whether an alarm block describes an exact alarm (`window=0` or an exact-alarm reason). */
    fun isExact(alarmBlock: String): Boolean = Regex("""window=0(\D|$)""").containsMatchIn(alarmBlock) ||
        alarmBlock.contains("exactAllowReason=")

    /** The `dumpsys activity services` block of the service whose class name ends with [className], or null. */
    fun serviceBlock(dump: String, className: String): String? {
        val lines = dump.lines()
        val start = lines.indexOfFirst { it.contains("ServiceRecord{") && it.contains(className) }
        if (start < 0) return null
        val end = (start + 1 until lines.size).firstOrNull { lines[it].contains("* ServiceRecord{") } ?: lines.size
        return lines.subList(start, end).joinToString("\n")
    }

    /**
     * The `dumpsys notification` records (`NotificationRecord(…`) of [packageName]
     * with notification [id], each up to the next record.
     */
    fun notificationRecords(dump: String, packageName: String, id: Int): List<String> {
        val lines = dump.lines()
        val starts = lines.indices.filter { lines[it].contains("NotificationRecord(") }
        return starts.mapIndexedNotNull { index, start ->
            val header = lines[start]
            val matches = header.contains("pkg=$packageName ") && Regex("""\bid=$id\b""").containsMatchIn(header)
            if (!matches) return@mapIndexedNotNull null
            val end = starts.getOrNull(index + 1) ?: lines.size
            lines.subList(start, end).joinToString("\n")
        }
    }

    /**
     * Whether a notification record shows FLAG_PROMOTED_ONGOING: either by
     * name (`flags=…|PROMOTED_ONGOING|…`) or as the 0x40000 bit of a hex flag
     * value (`flags=0x…` or `UNKNOWN(0x…)`).
     */
    fun showsPromotedOngoing(record: String): Boolean {
        if (record.contains("PROMOTED_ONGOING")) return true
        return Regex("""flags=(\S+)""").findAll(record).any { match ->
            Regex("""0x([0-9a-fA-F]+)""").findAll(match.groupValues[1]).any { hex ->
                (hex.groupValues[1].toLongOrNull(radix = 16) ?: 0L) and FLAG_PROMOTED_ONGOING.toLong() != 0L
            }
        }
    }
}
