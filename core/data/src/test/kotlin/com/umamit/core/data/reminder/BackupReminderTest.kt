package com.umamit.core.data.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The backup reminder (BAK-06, 06 §5.4, D-130 Q11) on WorkManager's test driver: a job 30 days after the latest of the
 * last export and the last check or turn-on, no constraints; a run with 19 sessions since the last export posts
 * nothing, with 20 one silent notification on „Erinnerungen"; either way it notes the check and schedules the next
 * run 30 days on; off cancels, an export or a turn-on replaces. Synthetic data (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupReminderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val day = TimeUnit.DAYS.toMillis(1)
    private val clock = FakeClock(wall = 1_000 * day)
    private val settings = FakeSettingsRepository()
    private val state = FakeState()
    private val scheduler = BackupReminderScheduler(context, state, settings, clock)
    private val run = BackupReminderRun(context, state, settings, clock, scheduler)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private class FakeState : BackupReminderState {
        var lastBackup: Long? = null
        var reminderLast: Long? = null
        var sessions: Int = 0
        var sessionsSinceAsked: Long? = -1L
        var failCount = false

        override suspend fun lastBackupAt(): Long? = lastBackup

        override suspend fun reminderLastAt(): Long? = reminderLast

        override suspend fun markReminderLastAt(atMs: Long) {
            reminderLast = atMs
        }

        override suspend fun sessionsSince(sinceMs: Long?): Int {
            if (failCount) throw IOException("synthetic database failure")
            sessionsSinceAsked = sinceMs
            return sessions
        }
    }

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == BackupReminderWorker::class.java.name) {
                BackupReminderWorker(appContext, workerParameters, run)
            } else {
                null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .setWorkerCoroutineContext(Dispatchers.Unconfined)
                .setWorkerFactory(factory)
                .build(),
        )
    }

    private fun work(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackupReminderScheduler.WORK_NAME).get()

    private fun pending(): WorkInfo? = work().singleOrNull { it.state == WorkInfo.State.ENQUEUED }

    private fun TestScope.startApp() {
        backgroundScope.launch { scheduler.follow() }
        runCurrent()
    }

    /** The job's time has come, [afterDays] on. */
    private fun runJobAfter(afterDays: Long) {
        clock.wall += afterDays * day
        val job = requireNotNull(pending()) { "no reminder scheduled" }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(job.id)
    }

    private fun posted(): Notification? = shadowOf(notifications).getNotification(BackupReminderNotifications.ID)

    private fun text(n: Notification): String = n.extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    @Test
    fun theJobIs30DaysAfterTheLatestOfTheExportAndTheLastCheck() = runTest {
        state.lastBackup = clock.wall - 10 * day
        state.reminderLast = clock.wall - 4 * day

        startApp()

        assertThat(pending()!!.initialDelayMillis).isEqualTo(26 * day)
    }

    @Test
    fun anExportLaterThanTheLastCheckCountsFromTheExport() = runTest {
        state.lastBackup = clock.wall - 2 * day
        state.reminderLast = clock.wall - 20 * day

        startApp()

        assertThat(pending()!!.initialDelayMillis).isEqualTo(28 * day)
    }

    @Test
    fun theFirstStartWithNeitherMarkerCountsFromNow() = runTest {
        startApp()

        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
        assertThat(state.reminderLast).isEqualTo(clock.wall)
    }

    @Test
    fun theJobHasNoConstraints() = runTest {
        startApp()

        val constraints = pending()!!.constraints
        assertThat(constraints.requiredNetworkType.name).isEqualTo("NOT_REQUIRED")
        assertThat(constraints.requiresCharging()).isFalse()
        assertThat(constraints.requiresDeviceIdle()).isFalse()
        assertThat(constraints.requiresBatteryNotLow()).isFalse()
        assertThat(constraints.requiresStorageNotLow()).isFalse()
    }

    @Test
    fun aWaitingJobIsKeptAtAppStart() = runTest {
        startApp()
        val first = pending()!!.id

        clock.wall += 5 * day
        val second = FakeSettingsRepository()
        BackupReminderScheduler(context, state, second, clock).also { backgroundScope.launch { it.follow() } }
        runCurrent()

        assertThat(pending()!!.id).isEqualTo(first)
    }

    @Test
    fun withSwitchOffAppStartSchedulesNothing() = runTest {
        settings.update { it.copy(backupReminderEnabled = false) }

        startApp()

        assertThat(work()).isEmpty()
    }

    @Test
    fun nineteenSessionsPostNothingButScheduleTheNextCheck() = runTest {
        state.lastBackup = clock.wall - 40 * day
        state.reminderLast = clock.wall - 1 * day
        state.sessions = 19
        startApp()

        runJobAfter(29)

        assertThat(posted()).isNull()
        assertThat(state.sessionsSinceAsked).isEqualTo(state.lastBackup)
        assertThat(state.reminderLast).isEqualTo(clock.wall)
        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
    }

    @Test
    fun twentySessionsPostTheReminderAndScheduleTheNextCheck() = runTest {
        state.lastBackup = clock.wall - 40 * day
        state.reminderLast = clock.wall - 1 * day
        state.sessions = 20
        startApp()

        runJobAfter(29)

        val notification = requireNotNull(posted())
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo("Zeit für eine Sicherung")
        assertThat(text(notification)).isEqualTo("20 neue Sessions seit der letzten Sicherung.")
        assertThat(state.reminderLast).isEqualTo(clock.wall)
        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
    }

    @Test
    fun withoutAnExportTheTextSaysSoAndAllSessionsCount() = runTest {
        state.reminderLast = clock.wall - 1 * day
        state.sessions = 25
        startApp()

        runJobAfter(29)

        assertThat(state.sessionsSinceAsked).isNull()
        assertThat(text(requireNotNull(posted()))).isEqualTo("Du hast noch keine Sicherung erstellt.")
    }

    @Test
    fun theNotificationIsSilentOnTheLowChannelAndItsTapOpensTheBackupScreen() = runTest {
        state.reminderLast = clock.wall - 1 * day
        state.sessions = 30
        startApp()
        runJobAfter(29)

        val notification = requireNotNull(posted())
        val channel = notifications.getNotificationChannel(BackupReminderNotifications.CHANNEL_ID)

        assertThat(channel.name.toString()).isEqualTo("Erinnerungen")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        val tap = shadowOf(notification.contentIntent).savedIntent
        assertThat(OpenBackupIntents.isOpenBackup(tap)).isTrue()
        assertThat(tap.component!!.className).isEqualTo(OpenStartIntents.LAUNCHER_ACTIVITY)
    }

    @Test
    fun aFailingSessionCountStillSchedulesTheNextCheck() = runTest {
        state.reminderLast = clock.wall - 1 * day
        state.failCount = true
        startApp()

        runJobAfter(29)

        assertThat(posted()).isNull()
        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
    }

    @Test
    fun aRunWithTheSwitchOffDoesNothingAndSchedulesNothing() = runTest {
        state.sessions = 50
        state.reminderLast = clock.wall - 1 * day
        startApp()
        val before = state.reminderLast
        settings.update { it.copy(backupReminderEnabled = false) }

        run.run()

        assertThat(posted()).isNull()
        assertThat(state.reminderLast).isEqualTo(before)
    }

    @Test
    fun turningTheSwitchOffCancelsTheJobAndTheNotification() = runTest {
        state.reminderLast = clock.wall - 1 * day
        state.sessions = 30
        startApp()
        runJobAfter(29)
        assertThat(posted()).isNotNull()

        settings.update { it.copy(backupReminderEnabled = false) }
        runCurrent()

        assertThat(pending()).isNull()
        assertThat(posted()).isNull()
    }

    @Test
    fun turningTheSwitchOnSetsTheMarkerAndReplacesTheJob() = runTest {
        settings.update { it.copy(backupReminderEnabled = false) }
        state.lastBackup = clock.wall - 90 * day
        startApp()
        assertThat(pending()).isNull()

        scheduler.turnOn { settings.update { it.copy(backupReminderEnabled = true) } }

        assertThat(state.reminderLast).isEqualTo(clock.wall)
        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
        assertThat(settings.settings.first().backupReminderEnabled).isTrue()
    }

    @Test
    fun anExportReplacesTheJobWhenTheSwitchIsOn() = runTest {
        state.reminderLast = clock.wall - 20 * day
        startApp()
        assertThat(pending()!!.initialDelayMillis).isEqualTo(10 * day)

        state.lastBackup = clock.wall
        scheduler.exported()

        assertThat(pending()!!.initialDelayMillis).isEqualTo(30 * day)
    }

    @Test
    fun anExportWithTheSwitchOffCreatesNoJob() = runTest {
        settings.update { it.copy(backupReminderEnabled = false) }
        state.lastBackup = clock.wall

        scheduler.exported()

        assertThat(work()).isEmpty()
    }

    @Test
    fun theDelayIsNeverNegative() {
        assertThat(BackupReminderSchedule.delayMs(100 * day, lastBackupAt = 1 * day, reminderLastAt = 2 * day))
            .isEqualTo(0L)
    }
}
