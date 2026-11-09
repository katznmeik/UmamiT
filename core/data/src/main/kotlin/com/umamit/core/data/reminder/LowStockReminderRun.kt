package com.umamit.core.data.reminder

import android.content.Context
import android.util.Log
import com.umamit.core.common.WallClock
import com.umamit.core.data.LowStockRepository
import com.umamit.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One run of the low-stock reminder (INV-11, 06 §5.4, D-098; interval D-108), as [LowStockReminderWorker] runs it:
 * - **Due** ([LowStockReminderSchedule.isDue]: at least SET-28's N days after the last posted reminder, and at
 *   most 30 min before the set time): it lists every *Knapp* or *Leer* tea ([LowStockRepository.reminderTeas],
 *   hidden ones included). With one or more it posts notification 12 and notes the date; with none it posts
 *   nothing, removes an older one and leaves the date, so the next day tries again.
 * - **Not due** (a run far too early after a clock or DST change, or a second one that day): nothing.
 * - Either way it schedules the next run. A run that fails in any way (an unreadable database or settings file,
 *   a bug) is logged and skips the day: the job still succeeds, so the run it appended is never failed with it,
 *   and the next one waits for the next day, never runs at once again (D-098).
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
    @Suppress("TooGenericExceptionCaught") // any failure skips the day, never the chain of days (INV-11)
    suspend fun run() {
        val outcome = try {
            remind()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Low-stock reminder skipped", e)
            Outcome.FAILED
        }
        // Switched off: *Aus* cancelled the job already; a run that slipped through does nothing.
        if (outcome == Outcome.OFF) return
        try {
            scheduler.scheduleNext(skipToday = outcome == Outcome.FAILED || outcome == Outcome.NOTHING_DUE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // WorkManager's own store failed: the next app start schedules the reminder again (KEEP).
            Log.e(TAG, "Next low-stock reminder not scheduled", e)
        }
    }

    /** Posts today's reminder when it is due; what happened. */
    private suspend fun remind(): Outcome {
        val current = settings.settings.first()
        val interval = current.lowStockReminderInterval
        if (!interval.isOn) return Outcome.OFF
        val now = clock.nowMs()
        val zone = clock.zone()
        val due = LowStockReminderSchedule.isDue(
            nowMs = now,
            zone = zone,
            minuteOfDay = current.lowStockReminderTime,
            everyDays = interval.days,
            lastPosted = current.lastReminderDate(),
        )
        return if (due) post(LowStockReminderSchedule.dateOf(now, zone).toString()) else Outcome.DONE
    }

    /** Posts the reminder of [date] and notes the date, or with no tea due only removes an older one. */
    private suspend fun post(date: String): Outcome {
        val teas = lowStock.reminderTeas()
        if (teas.isEmpty()) {
            LowStockReminderNotifications.cancel(context)
            return Outcome.NOTHING_DUE
        }
        settings.update { it.copy(lowStockReminderLastDate = date) }
        LowStockReminderNotifications.post(context, teas)
        return Outcome.DONE
    }

    /** [NOTHING_DUE] and [FAILED] count today as had, so the next run waits for tomorrow. */
    private enum class Outcome { DONE, NOTHING_DUE, OFF, FAILED }

    private companion object {
        const val TAG = "UmamiTStockReminder"
    }
}
