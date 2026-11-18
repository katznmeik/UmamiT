package com.umamit.core.data.reminder

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.umamit.core.common.WallClock
import com.umamit.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the backup reminder (BAK-06, 06 §5.4, D-130 Q11, item 24): one unique one-time WorkManager job
 * ([WORK_NAME]) due [BackupReminderSchedule.INTERVAL_MS] after the latest of `lastBackupAt` and
 * `backupReminderLastAt`; none while SET-20 is off. Inexact by design: no constraints, no exact alarm; WorkManager
 * re-arms it after a reboot.
 *
 * - **App start** ([follow]): keeps a job that waits, so a process started for the job never drops or repeats its
 *   run; without one it schedules the next. The first start with neither marker set counts the 30 days from now.
 * - **SET-20 on** ([turnOn]): sets `backupReminderLastAt` = now and replaces the job; **off** ([turnOff]) cancels it.
 * - **A finished export** ([exported]): replaces the job, if the switch is on.
 * - **Each run** schedules its successor ([scheduleNext]), appended to itself.
 */
class BackupReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val state: BackupReminderState,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Follows SET-20 for the life of the process: the first value keeps a waiting job; later changes (an import can
     * bring the switch in either state) cancel it or replace it.
     */
    suspend fun follow() {
        var first = true
        settings.settings
            .map { it.backupReminderEnabled }
            .distinctUntilChanged()
            .collect { enabled ->
                if (!enabled) {
                    cancel()
                } else {
                    if (first && state.lastBackupAt() == null && state.reminderLastAt() == null) {
                        state.markReminderLastAt(clock.nowMs())
                    }
                    enqueue(delayMs(), if (first) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
                }
                first = false
            }
    }

    /**
     * SET-20 turned on: the 30 days count from now (`backupReminderLastAt` = now), [save] stores the switch, and the
     * job is replaced. In this order, so a run never sees the switch on with an older marker.
     */
    suspend fun turnOn(save: suspend () -> Unit) {
        state.markReminderLastAt(clock.nowMs())
        save()
        enqueue(delayMs(), ExistingWorkPolicy.REPLACE)
    }

    /** SET-20 turned off: no job, no notification. */
    fun turnOff() = cancel()

    /** A finished export (`lastBackupAt` is set): the job is replaced, unless SET-20 is off. */
    suspend fun exported() {
        if (settings.settings.first().backupReminderEnabled) enqueue(delayMs(), ExistingWorkPolicy.REPLACE)
    }

    /**
     * Schedules the run after the one that is running now, which checked at [checkedAt]: appended to it, so it starts
     * once the running one has ended, never cancelling it (REPLACE from inside the job would). Counted from
     * [checkedAt] at the latest, so it is never due in the past, whatever the stored markers say; without readable
     * markers it comes in 30 days.
     */
    @Suppress("TooGenericExceptionCaught") // unreadable markers never break the chain of checks (BAK-06)
    suspend fun scheduleNext(checkedAt: Long) {
        val delay = try {
            BackupReminderSchedule.delayMs(checkedAt, state.lastBackupAt(), checkedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backup reminder markers unreadable: the next run comes in 30 days", e)
            BackupReminderSchedule.INTERVAL_MS
        }
        enqueue(delay, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
        BackupReminderNotifications.cancel(context)
    }

    private suspend fun delayMs(): Long =
        BackupReminderSchedule.delayMs(clock.nowMs(), state.lastBackupAt(), state.reminderLastAt())

    private fun enqueue(delayMs: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<BackupReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    companion object {
        /** The job's unique name (06 §5.4). */
        const val WORK_NAME = "backup-reminder"

        private const val TAG = "UmamiTBackupReminder"
    }
}
