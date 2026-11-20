package com.umamit.core.data.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The backup reminder's job (BAK-06, 06 §5.4, D-130 Q11), created by the app's `HiltWorkerFactory`: one
 * [BackupReminderRun]. It always succeeds, so WorkManager never retries it; the next run is already scheduled by then.
 */
@HiltWorker
class BackupReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val run: BackupReminderRun,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        run.run()
        return Result.success()
    }
}
