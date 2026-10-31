package com.umamit.core.timer.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.umamit.core.model.TimerSlot
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.ControllerHarness
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.brewStart
import com.umamit.core.timer.controller.infusion
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
import java.time.Duration

/**
 * Android drops an update of a showing notification while the app posts faster than 5 per second (AOSP's rate
 * limit, [SystemLikeNotificationManager.limitUpdateRate]; #135, TMR-21, TMR-22). With a brew session and a quick
 * timer, one tap updates notifications 5 and 1 at once, right after the start's burst: the update that moves the
 * Live Update to the other timer can be the one dropped. Nothing may leave notification 1 on the wrong timer: the
 * newest content must be showing once the burst is over, well before the 15 s refresh.
 *
 * The timings are those of the CI emulator run that failed (run 41167064240, API 36): the package had posted busily
 * until 2.7 s before (the previous device test), the brew's *Start* 0.29 s after its READY (the service's
 * `startForeground`), the quick timer 0.17 s later, its *Pause* 0.58 s after that: just after the service's re-post
 * and 1.03 s after the start, outside D-071's window, which the earlier runs that passed had tapped within.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [SystemLikeNotificationManager::class, SystemLikeService::class])
class ShedUpdateTest {
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
        system.limitUpdateRate = true
    }

    @After
    fun tearDown() {
        ForegroundServiceState.reset()
        SystemLikeService.reset()
        harness.close()
    }

    @Test
    fun `the quick timer's Pause right after the start moves the Live Update to the brew even if Android drops it`() =
        runBlocking<Unit> {
            bothRunning()

            controller.send(TimerSlot.QUICK, TimerEvent.Pause)
            deliver()
            idle(SETTLED_MS)

            assertLiveUpdate(BREW, other = QUICK)
            assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo(BREW_RUNNING)
            assertThat(posted(TimerNotifications.ID_OTHER).text()).startsWith("Pausiert")
        }

    @Test
    fun `the quick timer's Weiter right after its Pause takes the Live Update back`() = runBlocking<Unit> {
        bothRunning()
        controller.send(TimerSlot.QUICK, TimerEvent.Pause)
        deliver()
        idle(WEITER_AFTER_MS)

        controller.send(TimerSlot.QUICK, TimerEvent.Resume)
        deliver()
        idle(SETTLED_MS)

        assertLiveUpdate(QUICK, other = BREW)
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo(QUICK_RUNNING)
        assertThat(posted(TimerNotifications.ID_OTHER).text()).isEqualTo(BREW_RUNNING)
    }

    @Test
    fun `the brew's Pause right after the start changes notification 5 and leaves the Live Update`() =
        runBlocking<Unit> {
            bothRunning()

            controller.send(TimerSlot.BREW, TimerEvent.Pause)
            deliver()
            idle(SETTLED_MS)

            assertLiveUpdate(QUICK, other = BREW)
            assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo(QUICK_RUNNING)
            assertThat(posted(TimerNotifications.ID_OTHER).text()).startsWith("Pausiert")
        }

    @Test
    fun `the quick timer's end right after the start leaves the brew's Live Update alone`() = runBlocking<Unit> {
        bothRunning()

        controller.send(TimerSlot.QUICK, TimerEvent.Cancel)
        deliver()
        idle(SETTLED_MS)

        assertLiveUpdate(BREW, other = null)
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo(BREW_RUNNING)
    }

    /**
     * Notification 5 posted by the port, outside the service: the brew leads, and a *Pause* of the quick timer on
     * notification 5 updates only 5. Right after both timers' start (the package posted busily until 0.3 s before),
     * that update and the quick timer's start on 5 are dropped; 5 must not keep showing the quick timer as waiting
     * or running while it is paused.
     */
    @Test
    fun `the quick timer's Pause on notification 5 right after its start shows it paused even if Android drops it`() =
        runBlocking<Unit> {
            start(brewSeconds = 60, quickSeconds = 120, quietMs = HOT_QUIET_MS)
            idle(QUICK_TAP_AFTER_MS)

            controller.send(TimerSlot.QUICK, TimerEvent.Pause)
            deliver()
            idle(SETTLED_MS)

            assertLiveUpdate(BREW, other = QUICK)
            assertThat(posted(TimerNotifications.ID_LIVE_UPDATE).text()).isEqualTo(BREW_RUNNING)
            assertWithMessage("notification 5 (dropped: ${system.shed.map { it.first to it.second.text() }})")
                .that(posted(TimerNotifications.ID_OTHER).text()).startsWith("Pausiert")
        }

    /** The brew's step (120 s) and the quick timer (60 s) run; the quick timer, ending first, is the Live Update. */
    private suspend fun bothRunning() {
        start()
        idle(TAP_AFTER_MS)
        assertLiveUpdate(QUICK, other = BREW)
    }

    /** A busy past, then the brew's step and the quick timer, started as the device test starts them. */
    private suspend fun start(brewSeconds: Int = 120, quickSeconds: Int = 60, quietMs: Long = QUIET_MS) {
        busyPast(quietMs)
        controller.startSession(brewStart(infusion(brewSeconds, preAlert = 0)))
        deliver()
        idle(START_AFTER_MS)
        controller.send(TimerSlot.BREW, TimerEvent.Start)
        deliver()
        idle(QUICK_AFTER_MS)
        controller.startQuickTimer(quickSeconds, preAlertSeconds = 0, run = true)
        deliver()
    }

    /** The package posted busily until [quietMs] ago, as the device test before this one did on CI. */
    private fun busyPast(quietMs: Long) {
        val ids = (0 until BUSY_POSTS).map { BUSY_ID + it }
        ids.forEach {
            notificationManager.notify(it, plain())
            idle(BUSY_GAP_MS)
        }
        ids.forEach(notificationManager::cancel)
        idle(quietMs)
    }

    private fun plain(): Notification = Notification.Builder(app, TimerNotifications.CHANNEL_SESSIONS)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .build()

    /**
     * Notification 1 is [lead]'s timer, the service's; notification 5 is [other]'s (null: none); nothing else of the
     * timer is showing.
     */
    private fun assertLiveUpdate(lead: String, other: String?) {
        assertWithMessage("notification 1's timer (dropped: ${system.shed.map { it.first to it.second.title() }})")
            .that(posted(TimerNotifications.ID_LIVE_UPDATE).title()).startsWith(lead)
        assertThat(service?.let { shadowOf(it.get()).lastForegroundNotificationId })
            .isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        if (other == null) {
            assertThat(activeIds()).containsExactly(TimerNotifications.ID_LIVE_UPDATE)
            return
        }
        assertThat(activeIds()).containsExactly(TimerNotifications.ID_LIVE_UPDATE, TimerNotifications.ID_OTHER)
        assertWithMessage("notification 5's timer").that(posted(TimerNotifications.ID_OTHER).title()).startsWith(other)
    }

    private fun activeIds(): List<Int> = notificationManager.activeNotifications.map { it.id }

    private fun posted(id: Int): Notification =
        checkNotNull(system.getNotification(id)) { "notification $id is not showing: ${activeIds()}" }

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    /** Lets [ms] pass on the main looper (and `SystemClock`), running what comes due, the service's re-posts too. */
    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Delivers the commands sent to the service, in order, as Android would (as in `NextDueLiveUpdateTest`). */
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

    private companion object {
        const val BREW = ControllerHarness.TEA_NAME
        const val QUICK = "Schnelltimer"

        /** The texts of the running brew step and the running quick timer. */
        const val BREW_RUNNING = "Zieht bei 60 °C"
        const val QUICK_RUNNING = "Zieht …"

        /** Notifications of the busy past: not the timer's. */
        const val BUSY_ID = 100
        const val BUSY_POSTS = 40
        const val BUSY_GAP_MS = 100L
        const val QUIET_MS = 2_700L

        const val START_AFTER_MS = 286L
        const val QUICK_AFTER_MS = 165L
        const val TAP_AFTER_MS = 575L

        /** *Weiter* after *Pause*: before the Pause's re-post would land. */
        const val WEITER_AFTER_MS = 300L

        /** A busier past for notification 5: the package's last posts 0.3 s before the start. */
        const val HOT_QUIET_MS = 300L

        /** A tap on notification 5 right after the quick timer's start, before the start's re-posts. */
        const val QUICK_TAP_AFTER_MS = 100L

        /** Long enough for any repair, well before the 15 s refresh (04 §8.3) and the device test's 10 s. */
        const val SETTLED_MS = 9_000L
    }
}
