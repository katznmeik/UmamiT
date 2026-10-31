package com.umamit.core.timer.notification

import android.Manifest
import android.app.Application
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.testing.FakeTimerWakeUp
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerWakeUpPort
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
 * The notifier and the service port together (04 §8.2, §8.3; D-044, D-057): which notification
 * of 1, 2 and the quiet one is shown, and how. While the service runs, every change goes
 * through it, so the notifier never posts notification 2 next to the service's notification 1
 * (Android 16 would group the two and silence the alert). Without the service the notifier
 * posts the notification itself, and the alert is still the only one. The Live Update is
 * refreshed every 15 s while a step runs, never after a newer state, and a dismissed Live
 * Update stays dismissed until the state changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidTimerNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock()
    private val refresh = FakeTimerWakeUp(clock)
    private val states = NotificationStates(clock)
    private val port = portOn(app)
    private val notifier = AndroidTimerNotifier(app, port, TimerStateNotifications(app))

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        LiveUpdateDismissal.dismissedKey = null
    }

    @After
    fun tearDown() = ForegroundServiceState.reset()

    @Test
    fun `while the service runs, the alert goes through the service and is never posted next to notification 1`() {
        port.startForeground(states.running(), fullScreen = false)
        val service = run(startedCommand())

        // The deadline: StartForeground, StartAlert, UpdateNotification (04 §4).
        port.startForeground(states.alerting(), fullScreen = true)
        val switch = startedCommand()
        notifier.update(states.alerting(), fullScreen = true)

        // The same alert again is not sent twice; the service switches its own notification.
        assertThat(shadowOf(app).nextStartedService).isNull()
        assertThat(posted(TimerNotifications.ID_ALERT)).isNull()
        service.withIntent(switch).startCommand(0, 2)
        assertThat(shadowOf(service.get()).lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_ALERT)
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE)).isNull()
    }

    @Test
    fun `while the service runs, an update of the state goes to the service`() {
        port.startForeground(states.running(), fullScreen = false)
        val service = run(startedCommand())

        notifier.update(states.paused(), fullScreen = false)

        val command = startedCommand()
        assertThat(BrewTimerService.notificationIdOf(command)).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        service.withIntent(command).startCommand(0, 2)
        assertThat(posted(TimerNotifications.ID_LIVE_UPDATE)?.text()).isEqualTo("Pausiert · noch 0:12")
    }

    @Test
    fun `without the service the alert is posted alone, full screen`() {
        val refusing = portOn(refusingContext())
        val notifier = AndroidTimerNotifier(app, refusing, TimerStateNotifications(app))
        notifier.update(states.longStep(remainingMs = 60_000), fullScreen = false)
        assertThat(posted(TimerNotifications.ID_QUIET)).isNotNull()

        refusing.startForeground(states.alerting(), fullScreen = true)
        notifier.update(states.alerting(), fullScreen = true)

        assertThat(posted(TimerNotifications.ID_ALERT)?.fullScreenIntent).isNotNull()
        assertThat(notificationManager.activeNotifications.map { it.id }).containsExactly(TimerNotifications.ID_ALERT)
    }

    @Test
    fun `a long step is the quiet notification, posted without the service`() {
        notifier.update(states.longStep(remainingMs = 60 * 60_000L), fullScreen = false)

        assertThat(shadowOf(app).nextStartedService).isNull()
        assertThat(posted(TimerNotifications.ID_QUIET)?.text()).isEqualTo("Fertig um 11:00")
    }

    @Test
    fun `the service never starts while the quiet notification is posted`() {
        // A demoted session: *Start* from the notification brings the service back (D-053).
        notifier.update(states.ready().copy(demoted = true), fullScreen = false)
        assertThat(posted(TimerNotifications.ID_QUIET)).isNotNull()

        port.startForeground(states.running(), fullScreen = false)

        assertThat(posted(TimerNotifications.ID_QUIET)).isNull()
        assertThat(BrewTimerService.notificationIdOf(startedCommand())).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
    }

    @Test
    fun `stopping removes the quiet notification too`() {
        notifier.update(states.longStep(remainingMs = 60_000), fullScreen = false)

        port.stopForeground()

        assertThat(notificationManager.activeNotifications).isEmpty()
    }

    @Test
    fun `a running step's Live Update is refreshed every 15 s`() = runBlocking<Unit> {
        port.startForeground(states.running(remainingMs = 45_000), fullScreen = false)
        startedCommand()
        ForegroundServiceState.foregroundId = TimerNotifications.ID_LIVE_UPDATE

        refresh.advanceBy(15_000)

        val refreshed = checkNotNull(BrewTimerService.notificationOf(startedCommand()))
        assertThat(progressOf(refreshed)).isEqualTo(47 + 300)
        refresh.advanceBy(15_000)
        assertThat(progressOf(checkNotNull(BrewTimerService.notificationOf(startedCommand())))).isEqualTo(47 + 450)
    }

    @Test
    fun `no refresh after a newer state, and none while waiting`() = runBlocking<Unit> {
        port.startForeground(states.running(), fullScreen = false)
        startedCommand()
        ForegroundServiceState.foregroundId = TimerNotifications.ID_LIVE_UPDATE

        notifier.update(states.paused(), fullScreen = false)
        startedCommand()
        refresh.advanceBy(60_000)

        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `a refresh already due when a newer state comes sends nothing`() = runBlocking<Unit> {
        // The refresh fired and waits for the lock while the controller sends the alert: it must
        // not switch the service back to the Live Update (D-044).
        val armed = mutableListOf<suspend () -> Unit>()
        val keepsFired = object : TimerWakeUpPort {
            override fun arm(delayMs: Long, onDue: suspend () -> Unit) {
                armed += onDue
            }

            override fun cancel() = Unit
        }
        val port = BrewTimerServicePort(
            app,
            TimerClocks(clock, clock),
            FakeTimerDiagnostics(),
            TimerStateNotifications(app),
            keepsFired,
        )
        port.startForeground(states.running(), fullScreen = false)
        startedCommand()
        ForegroundServiceState.foregroundId = TimerNotifications.ID_LIVE_UPDATE
        port.startForeground(states.alerting(), fullScreen = true)
        startedCommand()

        armed.first().invoke()

        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `a dismissed Live Update is not posted again until the state changes`() = runBlocking<Unit> {
        notifier.update(states.longStep(remainingMs = 60 * 60_000L), fullScreen = false)
        dismiss(checkNotNull(posted(TimerNotifications.ID_QUIET)))
        notificationManager.cancel(TimerNotifications.ID_QUIET)

        refresh.advanceBy(15_000)
        notifier.update(states.longStep(remainingMs = 60 * 60_000L - 15_000), fullScreen = false)
        assertThat(posted(TimerNotifications.ID_QUIET)).isNull()

        notifier.update(states.paused().copy(demoted = true), fullScreen = false)
        assertThat(posted(TimerNotifications.ID_QUIET)).isNotNull()
    }

    @Test
    fun `the saved session notification names the session's tea`() {
        notifier.update(states.ready().copy(demoted = true), fullScreen = false)

        notifier.notifySessionSaved("s1")

        val saved = checkNotNull(posted(TimerNotifications.ID_SESSION_SAVED))
        assertThat(saved.channelId).isEqualTo(TimerNotifications.CHANNEL_SESSIONS)
        assertThat(saved.text()).isEqualTo("Gyokuro · im Journal")
        // On a LOW channel it never groups with the alert (D-044).
        assertThat(notificationManager.getNotificationChannel(TimerNotifications.CHANNEL_SESSIONS).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun `after a reboot the session's notice names its tea, on the quiet sessions channel`() {
        notifier.notifyTimersResumed(listOf(states.running()))

        val notice = checkNotNull(posted(TimerNotifications.ID_SESSION_SAVED))
        assertThat(notice.channelId).isEqualTo(TimerNotifications.CHANNEL_SESSIONS)
        assertThat(notice.title()).isEqualTo("Dein Timer läuft weiter")
        assertThat(notice.text()).isEqualTo("Gyokuro · nach dem Neustart")
        assertThat(notice.contentIntent).isNotNull()
    }

    @Test
    fun `after a reboot a quick timer's notice says Schnelltimer`() {
        notifier.notifyTimersResumed(listOf(states.quick(TimerPhase.PAUSED)))

        assertThat(posted(TimerNotifications.ID_SESSION_SAVED)?.text()).isEqualTo("Schnelltimer · nach dem Neustart")
    }

    @Test
    fun `after a reboot with both timers one notice names both (04 7_3)`() {
        notifier.notifyTimersResumed(listOf(states.running(), states.quick(TimerPhase.PAUSED)))

        val notice = checkNotNull(posted(TimerNotifications.ID_SESSION_SAVED))
        assertThat(notice.channelId).isEqualTo(TimerNotifications.CHANNEL_SESSIONS)
        assertThat(notice.title()).isEqualTo("Deine Timer laufen weiter")
        assertThat(notice.text()).isEqualTo("Gyokuro und Schnelltimer · nach dem Neustart")
    }

    private fun dismiss(notification: Notification) {
        TimerActionReceiver().onReceive(app, shadowOf(notification.deleteIntent).savedIntent)
    }

    private fun posted(id: Int): Notification? = shadowOf(notificationManager).getNotification(id)

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun Notification.title() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun progressOf(notification: Notification): Int =
        (Notification.Builder.recoverBuilder(app, notification).style as Notification.ProgressStyle).progress

    private fun startedCommand(): Intent = checkNotNull(shadowOf(app).nextStartedService) { "no command sent" }

    private fun run(command: Intent): ServiceController<BrewTimerService> =
        Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, 1)

    private fun refusingContext() = object : ContextWrapper(app) {
        override fun startForegroundService(service: Intent): ComponentName =
            throw ForegroundServiceStartNotAllowedException("app is in the background (test)")
    }

    private fun portOn(context: android.content.Context) = BrewTimerServicePort(
        context = context,
        clocks = TimerClocks(clock, clock),
        diagnostics = FakeTimerDiagnostics(),
        notifications = TimerStateNotifications(app),
        refresh = refresh,
    )
}
