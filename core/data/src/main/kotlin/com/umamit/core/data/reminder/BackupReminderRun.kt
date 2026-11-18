package com.umamit.core.data.reminder

import android.content.Context
import android.util.Log
import com.umamit.core.common.WallClock
import com.umamit.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One run of the backup reminder (BAK-06, 06 §5.4, D-130 Q11), as [BackupReminderWorker] runs it:
 * - SET-20 **off**: nothing, and no successor (*Aus* cancelled the job already).
 * - **On**: with at least [BackupReminderSchedule.MIN_SESSIONS] sessions since `lastBackupAt` (all without one) it
 *   posts the notification. **Whether it posted or not** it sets `backupReminderLastAt` = now and schedules the next
 *   run 30 days on, so the job is never due in the past. A run that fails in any way is logged and counts as a check
 *   too: the job still succeeds, and the next run is 30 days away, never at once.
 *
 * Rule 8: it runs on WorkManager's threads, never the timer's; it never starts a service, sets an exact alarm or
 * reads `active_brew`.
 */
class BackupReminderRun @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val state: BackupReminderState,
    private val settings: SettingsRepository,
    private val clock: WallClock,
    private val scheduler: BackupReminderScheduler,
) {
    suspend fun run() {
        val checkedAt = clock.nowMs()
        var enabled = true
        quietly("Backup reminder skipped") {
            enabled = settings.settings.first().backupReminderEnabled
            if (enabled) remind()
        }
        // Switched off: *Aus* cancelled the job already; a run that slipped through does nothing.
        if (!enabled) return
        quietly("Backup reminder check not noted") { state.markReminderLastAt(checkedAt) }
        // WorkManager's own store failing is logged too: the next app start schedules the reminder again (KEEP).
        quietly("Next backup reminder not scheduled") { scheduler.scheduleNext(checkedAt) }
    }

    private suspend fun remind() {
        val lastBackupAt = state.lastBackupAt()
        val sessions = state.sessionsSince(lastBackupAt)
        if (BackupReminderSchedule.shouldPost(sessions)) {
            BackupReminderNotifications.post(context, sessions, neverSaved = lastBackupAt == null)
        }
    }

    /** Runs [block]; any failure but a cancellation is logged as [what] and never breaks the chain of checks. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, what, e)
        }
    }

    private companion object {
        const val TAG = "UmamiTBackupReminder"
    }
}
