package com.umamit.core.data.reminder

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
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
import com.umamit.core.common.WallClock
import com.umamit.core.model.LowStockTea
import com.umamit.core.testing.FakeLowStockRepository
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * The daily low-stock reminder (INV-11, D-098; ticket #130) on WorkManager's test driver: the job is
 * scheduled for the set time, rescheduled when the time changes, cancelled when switched off, and each run
 * posts one silent notification on the channel „Vorrat" listing every *Knapp* or *Leer* tea (or nothing on
 * a day without one), whose tap opens Start; then it schedules the next day. Synthetic teas (D-016).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LowStockReminderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 30)
    private val clock = object : WallClock {
        var now = at(10, 0)

        override fun nowMs(): Long = now

        override fun zone(): ZoneId = zone
    }
    private val settings = FakeSettingsRepository()
    private val lowStock = FakeLowStockRepository()
    private val scheduler = LowStockReminderScheduler(context, settings, clock)
    private val run = LowStockReminderRun(context, lowStock, settings, clock, scheduler)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    private fun at(hour: Int, minute: Int, date: LocalDate = today): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = if (workerClassName == LowStockReminderWorker::class.java.name) {
                LowStockReminderWorker(appContext, workerParameters, run)
            } else {
                null
            }
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                // The job's coroutine runs in place, so a run has ended when the driver returns.
                .setWorkerCoroutineContext(Dispatchers.Unconfined)
                .setWorkerFactory(factory)
                .build(),
        )
    }

    private fun work(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(LowStockReminderScheduler.WORK_NAME).get()

    /** The job waiting for its time; null when none waits. */
    private fun pending(): WorkInfo? = work().singleOrNull { it.state == WorkInfo.State.ENQUEUED }

    /** The app's start: it follows SET-28 and SET-29 from then on. */
    private fun TestScope.startApp() {
        backgroundScope.launch { scheduler.follow() }
        runCurrent()
    }

    /** The job's time has come, at [nowMs] on the wall clock. */
    private fun runJobAt(nowMs: Long) {
        clock.now = nowMs
        val job = requireNotNull(pending()) { "no reminder scheduled" }
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(job.id)
    }

    /** The reminder (notification 12), or null. */
    private fun posted(): Notification? = shadowOf(notifications).getNotification(LowStockReminderNotifications.ID)

    private fun lines(notification: Notification): List<String> =
        notification.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }

    private val three = listOf(
        LowStockTea("t2", "Hisui", 0.0),
        LowStockTea("t4", "Kabuse", 3.0),
        LowStockTea("t1", "Tanabata", 14.0),
    )

    @Test
    fun theFirstRunIsTodayAtTheSetTime() = runTest {
        startApp()

        val job = pending()

        assertThat(job).isNotNull()
        assertThat(job!!.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(8))
    }

    @Test
    fun aChangedTimeReschedulesTheJob() = runTest {
        startApp()
        val first = pending()!!

        settings.update { it.copy(lowStockReminderTime = 21 * 60 + 30) }
        runCurrent()

        val second = pending()!!
        assertThat(second.id).isNotEqualTo(first.id)
        assertThat(second.initialDelayMillis).isEqualTo(TimeUnit.MINUTES.toMillis(11 * 60 + 30))
        // The old job is gone: one job waits.
        assertThat(work().filterNot { it.state.isFinished }.map { it.id }).containsExactly(second.id)
    }

    @Test
    fun switchingOffCancelsTheJobAndTheNotificationAndOnSchedulesItAgain() = runTest {
        lowStock.reminder = three
        startApp()
        runJobAt(at(18, 0))
        assertThat(posted()).isNotNull()

        settings.update { it.copy(lowStockReminder = false) }
        runCurrent()

        assertThat(pending()).isNull()
        assertThat(shadowOf(notifications).allNotifications).isEmpty()

        clock.now = at(19, 0)
        settings.update { it.copy(lowStockReminder = true) }
        runCurrent()
        // Today had its reminder: the next one is tomorrow at 18:00.
        assertThat(pending()!!.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(23))
    }

    @Test
    fun anAppStartKeepsTheJobThatWaits() = runTest {
        startApp()
        val first = pending()!!

        // The process again, an hour later (an update, a restart): the waiting job stays as it is.
        clock.now = at(11, 0)
        val again = LowStockReminderScheduler(context, settings, clock)
        backgroundScope.launch { again.follow() }
        runCurrent()

        assertThat(pending()!!.id).isEqualTo(first.id)
    }

    @Test
    fun aRunPostsOneSilentNotificationListingEveryLowTea() = runTest {
        lowStock.reminder = three
        startApp()

        runJobAt(at(18, 3))

        val notification = requireNotNull(posted())
        assertThat(notification.channelId).isEqualTo("stock")
        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
            .isEqualTo("3 Tees werden knapp")
        assertThat(
            lines(notification),
        ).containsExactly("Hisui · leer", "Kabuse · 3\u00A0g", "Tanabata · 14\u00A0g").inOrder()
        assertThat(notification.fullScreenIntent).isNull()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(shadowOf(notifications).allNotifications).hasSize(1)
    }

    @Test
    fun oneTeaIsNamedInTheTitleAndGramsAreGerman() = runTest {
        lowStock.reminder = listOf(LowStockTea("t1", "Tanabata", 12.5))
        startApp()

        runJobAt(at(18, 0))

        val notification = requireNotNull(posted())
        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString())
            .isEqualTo("Tanabata wird knapp")
        assertThat(lines(notification)).containsExactly("Tanabata · 12,5\u00A0g")
    }

    @Test
    fun aDayWithoutALowTeaPostsNothingAndTheNextDayIsScheduled() = runTest {
        startApp()

        runJobAt(at(18, 0))

        assertThat(shadowOf(notifications).allNotifications).isEmpty()
        assertThat(settings.settings.first().lowStockReminderLastDate).isEqualTo("2026-10-30")
        assertThat(pending()!!.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(24))
    }

    @Test
    fun eachRunSchedulesTheNextDayAtTheSetTime() = runTest {
        lowStock.reminder = three
        startApp()

        runJobAt(at(18, 10))

        assertThat(pending()!!.initialDelayMillis).isEqualTo(TimeUnit.MINUTES.toMillis(24 * 60 - 10))
    }

    @Test
    fun aRunFarTooEarlyPostsNothingAndWaitsForTheSetTime() = runTest {
        lowStock.reminder = three
        startApp()

        // A clock or DST change ran the job at 17:00.
        runJobAt(at(17, 0))

        assertThat(shadowOf(notifications).allNotifications).isEmpty()
        assertThat(settings.settings.first().lowStockReminderLastDate).isNull()
        assertThat(pending()!!.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(1))
    }

    @Test
    fun aSecondRunOnTheSameDayPostsNothing() = runTest {
        lowStock.reminder = three
        startApp()
        runJobAt(at(18, 0))
        notifications.cancelAll()

        // Tomorrow's job, run on today's date (a clock change).
        runJobAt(at(18, 30))

        assertThat(shadowOf(notifications).allNotifications).isEmpty()
        assertThat(pending()!!.initialDelayMillis).isEqualTo(TimeUnit.MINUTES.toMillis(24 * 60 - 30))
    }

    @Test
    fun theChannelIsQuietAndLow() = runTest {
        lowStock.reminder = three
        startApp()

        runJobAt(at(18, 0))

        val channel = notifications.getNotificationChannel("stock")
        assertThat(channel.name.toString()).isEqualTo("Vorrat")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(channel.sound).isNull()
        assertThat(channel.shouldVibrate()).isFalse()
        // The reminder never creates or touches a timer channel (rule 8, D-044).
        assertThat(notifications.notificationChannels.map { it.id }).containsExactly("stock")
    }

    @Test
    fun aTapOpensStartInTheAppsLauncherActivity() = runTest {
        lowStock.reminder = three
        startApp()
        runJobAt(at(18, 0))

        val tap = shadowOf(requireNotNull(posted()).contentIntent).savedIntent

        assertThat(tap.action).isEqualTo("com.umamit.action.OPEN_START")
        assertThat(tap.component?.className).isEqualTo("com.umamit.app.MainActivity")
        assertThat(tap.component?.packageName).isEqualTo(context.packageName)
        assertThat(tap.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(OpenStartIntents.isOpenStart(tap)).isTrue()
        assertThat(shadowOf(requireNotNull(posted()).contentIntent).isActivityIntent).isTrue()
    }
}
