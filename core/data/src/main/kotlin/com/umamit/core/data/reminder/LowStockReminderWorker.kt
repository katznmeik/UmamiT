package com.umamit.core.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The daily low-stock reminder's job (INV-11, 06 §5.4, D-098), created by the app's `HiltWorkerFactory`:
 * one [LowStockReminderRun]. It always succeeds, so WorkManager never retries it; the next day's run is
 * already scheduled by then.
 */
@HiltWorker
class LowStockReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val run: LowStockReminderRun,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        run.run()
        return Result.success()
    }
}
