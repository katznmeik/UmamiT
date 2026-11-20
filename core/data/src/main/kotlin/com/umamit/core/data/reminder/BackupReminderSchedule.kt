package com.umamit.core.data.reminder

import java.util.concurrent.TimeUnit

/**
 * When the backup reminder runs and whether it posts (BAK-06, 06 §5.4, D-130 Q11 and item 24): 30 days after the
 * latest of the last export, the switch's last turn-on and the last check, and it posts only with at least
 * [MIN_SESSIONS] sessions since the last export.
 */
object BackupReminderSchedule {
    /** The wait between two checks. */
    val INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(30)

    /** Sessions since the last export (all, without one) that make a reminder worth posting. */
    const val MIN_SESSIONS = 20

    /**
     * The wait from [nowMs] to the next run: [INTERVAL_MS] after the latest of [lastBackupAt] and [reminderLastAt],
     * counted from [nowMs] when both are null; never negative, so the job is never due in the past.
     */
    fun delayMs(nowMs: Long, lastBackupAt: Long?, reminderLastAt: Long?): Long {
        val base = listOfNotNull(lastBackupAt, reminderLastAt).maxOrNull() ?: nowMs
        return (base + INTERVAL_MS - nowMs).coerceAtLeast(0L)
    }

    /** Whether [sessions] since the last export are enough to post. */
    fun shouldPost(sessions: Int): Boolean = sessions >= MIN_SESSIONS
}
