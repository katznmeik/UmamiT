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
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.RecoveryTrigger
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
import com.umamit.core.timer.controller.state
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
 * One Live Update for the timer that ends next (04 §8.2, §8.3, §12; TMR-15, D-098, PO Q5), through the real
 * controller, service port, notifier and service, with the shadows that add AOSP's notification rules (D-084):
 * with a brew session and a quick timer at once, notification 1 (the promoted Live Update, the service's) is the
 * timer due next and notification 5 (`timer_other`, LOW, never promoted) the other. A running timer beats a
 * waiting one; of two running, the earlier deadline. The roles move when the order changes, and neither timer's
 * end leaves a notification behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [SystemLikeNotificationManager::class, SystemLikeService::class])
class NextDueLiveUpdateTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val system = SystemLikeNotificationManager.of(notificationManager)
    private val harness = ControllerHarness(notificationPorts = { androidPorts() })
    private val controller get() = harness.controller
    private var service: ServiceController<BrewTimerService>? = null
    private var startId = 0

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
    fun `of two running timers the one due first is the promoted Live Update, the other a quiet notification 5`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(120, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
            deliver()

            assertLiveUpdate(QUICK, other = BREW)
        }

    @Test
    fun `pausing the timer due first moves the Live Update to the other, resuming it moves it back`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(120, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
            deliver()

            controller.send(TimerSlot.QUICK, TimerEvent.Pause)
            deliver()

            assertLiveUpdate(BREW, other = QUICK)
            assertThat(posted(TimerNotifications.ID_OTHER).text()).startsWith("Pausiert")

            controller.send(TimerSlot.QUICK, TimerEvent.Resume)
            deliver()

            assertLiveUpdate(QUICK, other = BREW)
        }

    @Test
    fun `each notification's tap opens its own timer, also after Pause moved the Live Update (04 §8_2)`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(120, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(300, preAlertSeconds = 0, run = true)
            deliver()
            assertLiveUpdate(BREW, other = QUICK)
            assertThat(tapOpens(TimerNotifications.ID_LIVE_UPDATE)).isEqualTo(TimerSlot.BREW)
            assertThat(tapOpens(TimerNotifications.ID_OTHER)).isEqualTo(TimerSlot.QUICK)

            // *Pause* on the brew (its row on the quick timer's screen): the Schnelltimer is due next.
            controller.send(TimerSlot.BREW, TimerEvent.Pause)
            deliver()

            assertLiveUpdate(QUICK, other = BREW)
            assertThat(tapOpens(TimerNotifications.ID_LIVE_UPDATE)).isEqualTo(TimerSlot.QUICK)
            assertThat(tapOpens(TimerNotifications.ID_OTHER)).isEqualTo(TimerSlot.BREW)
        }

    @Test
    fun `when the Live Update's timer ends the other one becomes the Live Update, with nothing left behind`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(120, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
            deliver()

            controller.send(TimerSlot.QUICK, TimerEvent.Cancel)
            deliver()

            assertLiveUpdate(BREW, other = null)
        }

    @Test
    fun `when the other timer ends its notification 5 goes and the Live Update stays`() = runBlocking<Unit> {
        controller.startSession(brewStart(infusion(120, preAlert = 0)))
        controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        deliver()
        assertLiveUpdate(QUICK, other = BREW)

        controller.send(TimerSlot.BREW, TimerEvent.Discard)
        deliver()

        assertLiveUpdate(QUICK, other = null)
    }

    @Test
    fun `the timer due first rings, and after Fertig the other is the Live Update again`() = runBlocking<Unit> {
        controller.startSession(brewStart(infusion(120, preAlert = 0)))
        controller.send(TimerSlot.BREW, TimerEvent.Start)
        controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        harness.advanceSeconds(30)
        deliver()
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
        assertThat(posted(TimerNotifications.ID_OTHER).title()).startsWith(BREW)

        controller.send(TimerSlot.QUICK, TimerEvent.Poured)
        deliver()

        assertLiveUpdate(BREW, other = null)
    }

    @Test
    fun `a cold brew's running step before a waiting quick timer is the Live Update, shown by the service`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(20 * 60, tempC = 20), method = BrewMethod.COLD_BREW))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            deliver()
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_QUIET)

            // The quick timer waits in READY and needs the service (04 §6.1): the brew's step still ends next.
            controller.startQuickTimer(5 * 60, preAlertSeconds = 0)
            deliver()

            assertLiveUpdate(BREW, other = QUICK)
            assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).startsWith("Fertig um")
            assertWithMessage("a long step holds no wake lock (04 §6.1)")
                .that(BrewTimerService.wakeLockMsOf(lastCommand)).isEqualTo(0L)

            controller.send(TimerSlot.QUICK, TimerEvent.Cancel)
            deliver()

            // Alone again, the long step runs without the service, on its quiet notification (TMR-18).
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_QUIET)
            assertThat(posted(TimerNotifications.ID_QUIET).text()).startsWith("Fertig um")
            assertThat(ForegroundServiceState.isForeground).isFalse()
        }

    @Test
    fun `of two waiting timers the brew is the Live Update, until it goes quiet beside the quick timer`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(60, preAlert = 0)))
            harness.advanceSeconds(29 * 60)
            controller.startQuickTimer(60, preAlertSeconds = 0)
            deliver()
            assertLiveUpdate(BREW, other = QUICK)

            // 30 min untouched: the brew goes quiet (TMR-14) and leaves the service to the quick timer (04 §6.1).
            harness.advanceSeconds(60)
            deliver()

            assertThat((controller.state(TimerSlot.BREW).value as TimerUiState.Active).demoted).isTrue()
            assertLiveUpdate(QUICK, other = BREW)
        }

    @Test
    fun `Sitzung beenden during the brew's alert rings the held quick alert as a new alert that takes the screen`() =
        endedDuringItsAlertReleasesTheQuickAlert(TimerEvent.FinishSession)

    @Test
    fun `Sitzung verwerfen during the brew's alert rings the held quick alert as a new alert that takes the screen`() =
        endedDuringItsAlertReleasesTheQuickAlert(TimerEvent.Discard)

    /**
     * #134: the brew's alert is the service's notification 2 when [end] ends the session; the held quick alert
     * rings in the same turn. An update of notification 2 would never launch its full-screen intent (System UI
     * launches one only for a notification it adds), so the quick alert must be a new notification 2.
     */
    private fun endedDuringItsAlertReleasesTheQuickAlert(end: TimerEvent) = runBlocking<Unit> {
        controller.startSession(brewStart(infusion(60, preAlert = 0)))
        controller.send(TimerSlot.BREW, TimerEvent.Start)
        controller.startQuickTimer(61, preAlertSeconds = 0, run = true)
        harness.advanceSeconds(61)
        deliver()
        assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly(BREW_ALERT)
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)

        controller.send(TimerSlot.BREW, end)
        deliver()

        assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly(BREW_ALERT, QUICK).inOrder()
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT)
        assertThat(posted(TimerNotifications.ID_ALERT).text()).isEqualTo(QUICK)
        assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_ALERT)
    }

    @Test
    fun `a brew deadline during the quick alert takes the screen with a new alert, the quick one waits as 5`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(40, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
            harness.advanceSeconds(30)
            deliver()
            assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly(QUICK)

            harness.advanceSeconds(10)
            deliver()

            assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly(QUICK, BREW_ALERT).inOrder()
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
            assertThat(posted(TimerNotifications.ID_ALERT).text()).isEqualTo(BREW_ALERT)
            assertThat(posted(TimerNotifications.ID_OTHER).text()).isEqualTo("Fertig, wartet auf den Aufguss")
        }

    @Test
    fun `the other timer's start during an alert does not take the screen a second time`() = runBlocking<Unit> {
        controller.startSession(brewStart(infusion(60, preAlert = 0)))
        controller.startQuickTimer(30, preAlertSeconds = 0, run = true)
        harness.advanceSeconds(30)
        deliver()

        // The brew's running step needs the wake lock: the service gets the quick alert again with the longer one.
        controller.send(TimerSlot.BREW, TimerEvent.Start)
        deliver()

        assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly(QUICK)
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_ALERT, TimerNotifications.ID_OTHER)
        assertThat(BrewTimerService.wakeLockMsOf(lastCommand)).isGreaterThan(60_000L)
    }

    @Test
    fun `after a process death the timer due next is the Live Update again, without a second notification`() =
        runBlocking<Unit> {
            controller.startSession(brewStart(infusion(120, preAlert = 0)))
            controller.send(TimerSlot.BREW, TimerEvent.Start)
            controller.startQuickTimer(60, preAlertSeconds = 0, run = true)
            deliver()
            assertLiveUpdate(QUICK, other = BREW)

            val after = processDied()
            after.clock.advanceBy(5_000)
            after.controller.recover(RecoveryTrigger.APP_START)
            deliver()

            assertLiveUpdate(QUICK, other = BREW)
        }

    @Test
    fun `a quick timer that ended while the process was dead leaves no notification 5 behind`() = runBlocking<Unit> {
        controller.startSession(brewStart(infusion(120, preAlert = 0)))
        controller.startQuickTimer(60, preAlertSeconds = 0)
        deliver()
        assertLiveUpdate(BREW, other = QUICK)

        // Dead for 31 min: the waiting quick timer's 30-min cancel passed (04 §10).
        val after = processDied()
        after.clock.advanceBy(31 * 60_000L)
        after.controller.recover(RecoveryTrigger.APP_START)
        deliver()

        assertThat(after.controller.state(TimerSlot.QUICK).value).isEqualTo(TimerUiState.Idle)
        assertThat(activeIds()).doesNotContain(TimerNotifications.ID_OTHER)
        assertThat(activeIds()).hasSize(1)
        assertThat(posted(activeIds().single()).title()).startsWith(BREW)
    }

    /**
     * The process dies: its service goes and the system removes the service's foreground notification; the
     * notifications posted without the service stay. A new process with fresh ports starts on the same database.
     */
    private fun processDied(): ControllerHarness {
        service?.let { system.systemCancel(shadowOf(it.get()).lastForegroundNotificationId) }
        service = null
        ForegroundServiceState.reset()
        return harness.processRestarted()
    }

    /**
     * Notification 1 is [lead]'s timer (its title), the service's foreground notification, and the Live Update:
     * it requests promotion and is promotable. Notification 5 is [other]'s timer, on `timer_other` without the
     * request; null: no notification 5. Nothing else of the timer is showing.
     */
    private fun assertLiveUpdate(lead: String, other: String?) {
        val liveUpdate = posted(TimerNotifications.ID_LIVE_UPDATE)
        assertWithMessage("notification 1's timer").that(liveUpdate.title()).startsWith(lead)
        assertThat(foregroundId()).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(NotificationCompat.isRequestPromotedOngoing(liveUpdate)).isTrue()
        assertThat(liveUpdate.hasPromotableCharacteristics()).isTrue()
        if (other == null) {
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_LIVE_UPDATE)
            return
        }
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_LIVE_UPDATE, TimerNotifications.ID_OTHER)
        val quiet = posted(TimerNotifications.ID_OTHER)
        assertWithMessage("notification 5's timer").that(quiet.title()).startsWith(other)
        assertThat(quiet.channelId).isEqualTo(TimerNotifications.CHANNEL_TIMER_OTHER)
        assertThat(NotificationCompat.isRequestPromotedOngoing(quiet)).isFalse()
    }

    private fun activeIds(): List<Int> = notificationManager.activeNotifications.map { it.id }

    /** The timer notification [id]'s tap opens (04 §8.2); null: the one in front. */
    private fun tapOpens(id: Int): TimerSlot? = TimerScreenIntents.slotOf(shadowOf(posted(id).contentIntent).savedIntent)

    private fun posted(id: Int): Notification =
        checkNotNull(system.getNotification(id)) { "notification $id is not showing: ${activeIds()}" }

    private fun foregroundId(): Int? = service?.let { shadowOf(it.get()).lastForegroundNotificationId }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private lateinit var lastCommand: android.content.Intent

    /**
     * Delivers the commands sent to the service, in order, as Android would; then a stop the port sent: Android
     * destroys the service and removes its foreground notification (`ActiveServices.bringDownServiceLocked`).
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
        /** The titles of the two timers' notifications. */
        const val BREW = ControllerHarness.TEA_NAME
        const val QUICK = "Schnelltimer"

        /** The text of the brew's alert „Jetzt abgießen!" (the quick timer's is [QUICK]). */
        const val BREW_ALERT = "$BREW · Aufguss 1"
    }
}
