package com.umamit.core.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.umamit.core.common.WallClock
import com.umamit.core.data.SettingsRepository
import com.umamit.core.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Schedules the daily low-stock reminder (INV-11, 06 §5.4, D-098): one unique WorkManager job
 * ([WORK_NAME]) for the next run at SET-29's time, none while SET-28 is off. Inexact by design: no exact
 * alarm, no wake-up of its own, no constraints; WorkManager re-arms it after a reboot.
 *
 * - **App start** (also after an app update, whose `MY_PACKAGE_REPLACED` starts the process): [follow]
 *   keeps a job that waits or runs, so a process started for the job, or a start at 18:05 before the late
 *   job ran, never drops or repeats today's run; without one it schedules the next run.
 * - **A change of SET-28 or SET-29:** the job is replaced for the new time; off cancels it and removes the
 *   notification.
 * - **Each run** schedules its successor ([scheduleNext]) after itself.
 */
class LowStockReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: WallClock,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** Follows SET-28 and SET-29 for the life of the process: the first value keeps a waiting job. */
    suspend fun follow() {
        var first = true
        settings.settings
            .map { Choice(it.lowStockReminder, it.lowStockReminderTime) }
            .distinctUntilChanged()
            .collect { choice ->
                if (!choice.on) {
                    workManager.cancelUniqueWork(WORK_NAME)
                    LowStockReminderNotifications.cancel(context)
                } else {
                    enqueue(delayMs(), if (first) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
                }
                first = false
            }
    }

    /**
     * Schedules the run after the one that is running now: appended to it, so it starts once the running one
     * has ended, never cancelling it (REPLACE from inside the job would).
     */
    suspend fun scheduleNext() = enqueue(delayMs(), ExistingWorkPolicy.APPEND_OR_REPLACE)

    private suspend fun delayMs(): Long {
        val current = settings.settings.first()
        return LowStockReminderSchedule.delayMs(
            nowMs = clock.nowMs(),
            zone = clock.zone(),
            minuteOfDay = current.lowStockReminderTime,
            lastDate = current.lastReminderDate(),
        )
    }

    private fun enqueue(delayMs: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<LowStockReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private data class Choice(val on: Boolean, val minuteOfDay: Int)

    companion object {
        /** The job's unique name (06 §5.4). */
        const val WORK_NAME = "low-stock-reminder"
    }
}

/** The local date of the reminder's last run that was due; null before the first or when unreadable. */
internal fun UserSettings.lastReminderDate(): LocalDate? = lowStockReminderLastDate?.let {
    try {
        LocalDate.parse(it)
    } catch (_: DateTimeParseException) {
        null
    }
}
