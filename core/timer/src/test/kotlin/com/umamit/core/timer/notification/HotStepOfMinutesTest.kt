package com.umamit.core.timer.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
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
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
import com.umamit.core.timer.controller.state
import com.umamit.core.timer.engine.DueTimes
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.service.BrewTimerService
import com.umamit.core.timer.service.BrewTimerServicePort
import com.umamit.core.timer.service.ForegroundServiceState
import com.umamit.core.timer.service.SystemLikeNotificationManager
import com.umamit.core.timer.service.SystemLikeService
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
 * Hot steps of 10–59 min are short steps from v0.3.0 (TMR-18, 04 §1, §6.1, §12; D-098, #122, #127), through the
 * real controller, service port, notifier and service with the shadows of AOSP's notification rules (D-084): a
 * 15-min hot step keeps the service, its wake lock and the countdown Live Update (notification 1) and plays its
 * Vorwarnung („Gleich fertig …"), also after a process death. Cold and ice brew and hot steps of 60 min or more
 * stay long steps: „Fertig um 14:30" on the quiet notification 4, no service, the exact deadline alarm only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [SystemLikeNotificationManager::class, SystemLikeService::class])
class HotStepOfMinutesTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val system = SystemLikeNotificationManager.of(notificationManager)
    private val harness = ControllerHarness(notificationPorts = { androidPorts() })
    private var service: ServiceController<BrewTimerService>? = null
    private var startId = 0
    private var lastCommand: android.content.Intent? = null

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        SystemLikeService.reset()
        runBlocking { harness.teaWithStock() }
    }

    @After
    fun tearDown() {
        ForegroundServiceState.reset()
        SystemLikeService.reset()
        harness.close()
    }

    @Test
    fun `a 15-min hot step counts down in the service's Live Update and plays its Vorwarnung`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(FIFTEEN_MIN)))
        harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
        deliver()

        val deadline = checkNotNull(brew(harness).deadlineElapsedMs)
        assertThat(brew(harness).longStep).isFalse()
        assertThat(harness.alarms.armed(TimerSlot.BREW))
            .isEqualTo(DueTimes(preAlertAtElapsedMs = deadline - PRE_ALERT_MS, deadlineElapsedMs = deadline))
        assertCountdownLiveUpdate(brew(harness))
        assertWithMessage("the wake lock covers the step (04 §6.1)")
            .that(BrewTimerService.wakeLockMsOf(checkNotNull(lastCommand))).isAtLeast(FIFTEEN_MIN * 1_000L)

        harness.advanceSeconds(FIFTEEN_MIN - 10L)
        deliver()

        assertThat(harness.alert.ticks).isEqualTo(1)
        assertThat(brew(harness).preAlertPlayed).isTrue()
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo("Gleich fertig …")

        harness.advanceSeconds(10)
        deliver()

        assertThat(brew(harness).phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(harness.alert.started).hasSize(1)
        assertThat(system.fullScreenLaunches).hasSize(1)
    }

    @Test
    fun `a 20-min cold brew step stays a long step, Fertig um without the service or a Vorwarnung`() =
        assertLongStep(brewStart(infusion(20 * 60, tempC = 20), method = BrewMethod.COLD_BREW))

    @Test
    fun `a 60-min hot step stays a long step, Fertig um without the service or a Vorwarnung`() =
        assertLongStep(brewStart(infusion(60 * 60)))

    /** [start]'s step runs as a long step (TMR-18): unchanged from v0.2.2, up to its alert. */
    private fun assertLongStep(start: BrewStart) = runBlocking<Unit> {
        harness.controller.startSession(start)
        harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
        deliver()

        val deadline = checkNotNull(brew(harness).deadlineElapsedMs)
        assertThat(brew(harness).longStep).isTrue()
        assertThat(harness.alarms.armed(TimerSlot.BREW)).isEqualTo(DueTimes(deadlineElapsedMs = deadline))
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_QUIET)
        assertThat(posted(TimerNotifications.ID_QUIET).text()).startsWith("Fertig um")
        assertThat(ForegroundServiceState.isForeground).isFalse()

        harness.advanceSeconds((deadline - harness.clock.elapsed) / 1_000L)
        deliver()

        assertThat(harness.alert.ticks).isEqualTo(0)
        assertThat(brew(harness).phase).isEqualTo(TimerPhase.ALERTING)
        assertThat(harness.alert.started).hasSize(1)
        assertThat(ForegroundServiceState.isForeground).isTrue()
    }

    @Test
    fun `a 15-min hot step keeps its Vorwarnung after a process death`() = runBlocking<Unit> {
        harness.controller.startSession(brewStart(infusion(FIFTEEN_MIN)))
        harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
        deliver()
        val deadline = checkNotNull(brew(harness).deadlineElapsedMs)

        val after = processDied()
        after.clock.advanceBy(5 * 60_000L)
        after.controller.recover(RecoveryTrigger.APP_START)
        deliver()

        assertThat(after.alarms.armed(TimerSlot.BREW))
            .isEqualTo(DueTimes(preAlertAtElapsedMs = deadline - PRE_ALERT_MS, deadlineElapsedMs = deadline))
        assertCountdownLiveUpdate(brew(after))

        after.advanceSeconds((deadline - PRE_ALERT_MS - after.clock.elapsed) / 1_000L)
        deliver()

        assertThat(after.alert.ticks).isEqualTo(1)
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo("Gleich fertig …")
    }

    @Test
    fun `beside a quick timer a 15-min hot step is a short step, notification 5 first, then the Live Update`() =
        runBlocking<Unit> {
            harness.controller.startSession(brewStart(infusion(FIFTEEN_MIN)))
            harness.controller.send(TimerSlot.BREW, TimerEvent.Start)
            harness.controller.startQuickTimer(5 * 60, preAlertSeconds = 0, run = true)
            deliver()

            // The quick timer ends first, so it is the Live Update; the brew's step is notification 5 (D-098).
            assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).title()).startsWith(QUICK)
            assertThat(posted(TimerNotifications.ID_OTHER).title()).startsWith(ControllerHarness.TEA_NAME)
            assertWithMessage("the wake lock covers the brew's step, the longer of the two (04 §6.1)")
                .that(BrewTimerService.wakeLockMsOf(checkNotNull(lastCommand))).isAtLeast(FIFTEEN_MIN * 1_000L)

            harness.controller.send(TimerSlot.QUICK, TimerEvent.Cancel)
            deliver()

            // Alone, the step stays with the service as the countdown Live Update, not the quiet „Fertig um".
            assertCountdownLiveUpdate(brew(harness))
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_LIVE_UPDATE)
        }

    private fun brew(h: ControllerHarness) = h.controller.state(TimerSlot.BREW).value as TimerUiState.Active

    /**
     * [running]'s step is the service's notification 1, the promoted Live Update, with a chronometer counting down
     * to its deadline (04 §8.3), not the quiet „Fertig um" of a long step.
     */
    private fun assertCountdownLiveUpdate(running: TimerUiState.Active) {
        assertThat(ForegroundServiceState.isForeground).isTrue()
        assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        val liveUpdate = posted(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(liveUpdate.title()).startsWith(ControllerHarness.TEA_NAME)
        assertThat(NotificationCompat.isRequestPromotedOngoing(liveUpdate)).isTrue()
        assertThat(liveUpdate.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(liveUpdate.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(liveUpdate.`when`).isEqualTo(running.deadlineWallMs)
        assertThat(liveUpdate.text().orEmpty()).doesNotContain("Fertig um")
        assertThat(activeIds()).doesNotContain(TimerNotifications.ID_QUIET)
    }

    /**
     * The process dies: its service goes and the system removes the service's foreground notification. A new
     * process with fresh ports starts on the same database.
     */
    private fun processDied(): ControllerHarness {
        service?.let { system.systemCancel(shadowOf(it.get()).lastForegroundNotificationId) }
        service = null
        ForegroundServiceState.reset()
        return harness.processRestarted()
    }

    private fun activeIds(): List<Int> = notificationManager.activeNotifications.map { it.id }

    private fun posted(id: Int): Notification =
        checkNotNull(system.getNotification(id)) { "notification $id is not showing: ${activeIds()}" }

    private fun foregroundId(): Int? = service?.let { shadowOf(it.get()).lastForegroundNotificationId }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /**
     * Delivers the commands sent to the service, in order, as Android would; then a stop the port sent: Android
     * destroys the service and removes its foreground notification.
     */
    private fun deliver() {
        while (true) {
            val command = shadowOf(app).nextStartedService ?: break
            lastCommand = command
            startId++
            service = service?.withIntent(command)?.startCommand(0, startId)
                ?: Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, startId)
        }
        if (shadowOf(app).nextStoppedService != null) {
            service?.let {
                system.systemCancel(shadowOf(it.get()).lastForegroundNotificationId)
                it.destroy()
            }
            service = null
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

    private companion object {
        const val FIFTEEN_MIN = 15 * 60
        const val PRE_ALERT_MS = 10_000L
        const val QUICK = "Schnelltimer"
    }
}
