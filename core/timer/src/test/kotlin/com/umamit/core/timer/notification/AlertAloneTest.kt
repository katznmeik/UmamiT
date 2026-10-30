package com.umamit.core.timer.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.TimerAlarmKind
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
import com.umamit.core.timer.controller.send
import com.umamit.core.timer.controller.state
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.service.BrewTimerService
import com.umamit.core.timer.service.BrewTimerServicePort
import com.umamit.core.timer.service.ForegroundServiceState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * D-044 through the real controller, service port, notifier and service (04 §4 „Which
 * notification"; 04 §12 Robolectric): while alerting, the alert (ID 2) is the app's only
 * timer notification. No notification 1 or quiet notification is posted after
 * `deadlineReached`, after `addStep` while alerting, or after `recover` of an alerting state;
 * the alert starts full screen once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertAloneTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val harness = ControllerHarness(notificationPorts = { androidPorts() })
    private var service: ServiceController<BrewTimerService>? = null
    private var startId = 0

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        runBlocking { harness.teaWithStock() }
    }

    @After
    fun tearDown() {
        ForegroundServiceState.reset()
        harness.close()
    }

    @Test
    fun `at the deadline the alert replaces the Live Update, full screen`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(30), infusion(30)))
        harness.controller.send(TimerEvent.Start)
        deliver()
        assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)

        harness.advanceSeconds(30)
        deliver()

        assertAlertAlone()
        assertThat(posted(TimerNotifications.ID_ALERT)?.fullScreenIntent).isNotNull()
    }

    @Test
    fun `a step added while alerting keeps the alert alone`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(30)))
        harness.controller.send(TimerEvent.Start)
        harness.advanceSeconds(30)
        deliver()

        harness.controller.send(TimerEvent.AddStep)
        deliver()

        assertAlertAlone()
    }

    @Test
    fun `the alert recovered in a new process is alone`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(30)))
        harness.controller.send(TimerEvent.Start)
        harness.advanceSeconds(30)
        deliver()
        // The process dies: its service and notifications go with it.
        ForegroundServiceState.reset()
        service = null
        notificationManager.cancelAll()

        val after = harness.processRestarted()
        after.clock.advanceBy(5_000)
        after.controller.onAlarm(TimerSlot.BREW, TimerAlarmKind.OVERTIME_TIMEOUT)
        deliver()

        assertAlertAlone(after)
        assertThat(posted(TimerNotifications.ID_ALERT)?.fullScreenIntent).isNotNull()
    }

    @Test
    fun `a long step's quiet notification goes before its alert`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(20 * 60, tempC = 20), method = BrewMethod.COLD_BREW))
        harness.controller.send(TimerEvent.Start)
        deliver()
        assertThat(harness.active.phase).isEqualTo(TimerPhase.RUNNING)
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_QUIET)

        harness.advanceSeconds(20 * 60L)
        deliver()

        assertAlertAlone()
    }

    @Test
    fun `a quick alert beside a brew is the only loud notification, the brew is notification 5 (D-098)`() =
        runBlocking<Unit> {
            harness.controller.startSession(brewStart(infusion(120)))
            harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
            harness.controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
            deliver()
            assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
            assertThat(posted(TimerNotifications.ID_OTHER)?.channelId).isEqualTo(TimerNotifications.CHANNEL_TIMER_OTHER)

            harness.advanceSeconds(30)
            deliver()

            assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_ALERT)
            assertThat(posted(TimerNotifications.ID_ALERT)?.fullScreenIntent).isNotNull()
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
            assertOnlyTheAlertIsLoud()
        }

    @Test
    fun `a quick alert held behind the brew's is a quiet notification 5, and rings full screen after it`() =
        runBlocking<Unit> {
            harness.controller.startSession(brewStart(infusion(60)))
            harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
            harness.controller.startQuickTimer(61, preAlertSeconds = 0, run = true)
            harness.advanceSeconds(61)
            deliver()

            assertThat(harness.controller.state(TimerSlot.QUICK).value.let { it as? TimerUiState.Active }?.alertHeld)
                .isTrue()
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
            assertThat(posted(TimerNotifications.ID_OTHER)?.fullScreenIntent).isNull()
            assertOnlyTheAlertIsLoud()

            harness.controller.send(TimerSlot.BREW, TimerEvent.Poured)
            deliver()

            assertThat(harness.active.kind).isEqualTo(SessionKind.Quick)
            assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_ALERT)
            assertThat(posted(TimerNotifications.ID_ALERT)?.fullScreenIntent).isNotNull()
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
            assertOnlyTheAlertIsLoud()
        }

    /** D-044 with two timers: every timer notification but the alert is on a channel below DEFAULT. */
    private fun assertOnlyTheAlertIsLoud() {
        notificationManager.activeNotifications.filter { it.id != TimerNotifications.ID_ALERT }.forEach {
            val importance = notificationManager.getNotificationChannel(it.notification.channelId).importance
            assertWithMessage("importance of notification ${it.id}").that(importance)
                .isLessThan(NotificationManager.IMPORTANCE_DEFAULT)
        }
    }

    private fun assertAlertAlone(of: ControllerHarness = harness) {
        assertThat(of.active.phase).isEqualTo(TimerPhase.ALERTING)
        assertWithMessage("D-044: the alert is the only timer notification").that(activeIds())
            .containsExactly(TimerNotifications.ID_ALERT)
        assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_ALERT)
    }

    private fun activeIds(): List<Int> = notificationManager.activeNotifications.map { it.id }

    private fun posted(id: Int): Notification? = shadowOf(notificationManager).getNotification(id)

    private fun foregroundId(): Int? = service?.let { shadowOf(it.get()).lastForegroundNotificationId }

    /** Delivers the commands sent to the service, in order, as Android would. */
    private fun deliver() {
        while (true) {
            val command = shadowOf(app).nextStartedService ?: break
            startId++
            service = service?.withIntent(command)?.startCommand(0, startId)
                ?: Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, startId)
        }
    }

    private fun ControllerHarness.androidPorts(): Pair<BrewTimerServicePort, AndroidTimerNotifier> {
        val port = BrewTimerServicePort(
            app,
            TimerClocks(clock, clock),
            FakeTimerDiagnostics(),
            TimerStateNotifications(app),
            FakeTimerWakeUp(clock),
        )
        return port to AndroidTimerNotifier(app, port, TimerStateNotifications(app))
    }
}
