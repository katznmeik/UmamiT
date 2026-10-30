package com.umamit.core.data.reminder

import android.content.Context
import android.util.Log
import com.umamit.core.common.WallClock
import com.umamit.core.data.LowStockRepository
import com.umamit.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject

/**
 * One run of the daily low-stock reminder (INV-11, 06 §5.4, D-098), as [LowStockReminderWorker] runs it:
 * - **Due** ([LowStockReminderSchedule.isDue]: no run yet on this local date, and at most 30 min before the
 *   set time): it lists every *Knapp* or *Leer* tea ([LowStockRepository.reminderTeas], hidden ones
 *   included), notes the date, and posts notification 12, or removes yesterday's when there is none.
 * - **Not due** (a run far too early after a clock or DST change, or a second one that day): nothing.
 * - Either way it schedules the next run.
 *
 * Rule 8: it runs on WorkManager's threads, never the timer's; it never starts a service, sets an exact
 * alarm or reads `active_brew`.
 */
class LowStockReminderRun @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val lowStock: LowStockRepository,
    private val settings: SettingsRepository,
    private val clock: WallClock,
    private val scheduler: LowStockReminderScheduler,
) {
    suspend fun run() {
        val current = settings.settings.first()
        // Switched off: the switch cancelled the job already; a run that slipped through does nothing.
        if (!current.lowStockReminder) return
        try {
            val now = clock.nowMs()
            val zone = clock.zone()
            if (LowStockReminderSchedule.isDue(now, zone, current.lowStockReminderTime, current.lastReminderDate())) {
                val teas = lowStock.reminderTeas()
                val date = LowStockReminderSchedule.dateOf(now, zone).toString()
                settings.update { it.copy(lowStockReminderLastDate = date) }
                if (teas.isEmpty()) {
                    LowStockReminderNotifications.cancel(context)
                } else {
                    LowStockReminderNotifications.post(context, teas)
                }
            }
        } catch (e: IOException) {
            // An unreadable database or settings file: no reminder today, the next day tries again.
            Log.w(TAG, "Low-stock reminder skipped", e)
        } finally {
            scheduler.scheduleNext()
        }
    }

    private companion object {
        const val TAG = "UmamiTStockReminder"
    }
}
