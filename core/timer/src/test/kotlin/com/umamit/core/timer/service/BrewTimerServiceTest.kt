package com.umamit.core.timer.service

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
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeTimerDiagnostics
import com.umamit.core.timer.TimerAlertActivity
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.TimerStateNotifications
import com.umamit.core.timer.controller.TimerClocks
import com.umamit.core.timer.controller.TimerDiagnostic
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.TimerUiStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.StepStatus
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/**
 * The controller's service port and the `specialUse` foreground service it starts (04 §6.1,
 * §6.3, §8.2; D-044, D-053; #42): the notification of the state is the foreground
 * notification, the alert replaces notification 1, the wake lock covers a running step and
 * the alert, a stop never comes before the promised `startForeground`, and a refused start
 * falls back to the notification without the service and is logged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrewTimerServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val clock = FakeClock()
    private val diagnostics = FakeTimerDiagnostics()
    private val port = portOn(app)

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        ShadowPowerManager.clearWakeLocks()
    }

    @After
    fun tearDown() = ForegroundServiceState.reset()

    @Test
    fun `a running step starts the service with notification 1 and a wake lock past the alert`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)

        val command = startedCommand()
        assertThat(command.component?.className).isEqualTo(BrewTimerService::class.java.name)
        assertThat(BrewTimerService.isForegroundRequired(command)).isTrue()
        // 04 §6.3: remaining + the longest alert (2 min) + 30 s.
        assertThat(BrewTimerService.wakeLockMsOf(command)).isEqualTo(60_000L + 120_000L + 30_000L)

        val service = shadowOf(run(command).get())
        assertThat(service.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(service.lastForegroundNotification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
        assertThat(service.lastForegroundNotification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
    }

    @Test
    fun `the service creates the timer channels before its first foreground notification`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val command = startedCommand()
        // A fresh install has no channels; a foreground notification on a missing channel crashes the app.
        assertThat(notificationManager.notificationChannels).isEmpty()

        run(command)

        assertTimerChannels()
    }

    @Test
    fun `a refused start posts its notification into the timer channels`() {
        val refusing = portOn(
            object : ContextWrapper(app) {
                override fun startForegroundService(service: Intent): ComponentName =
                    throw ForegroundServiceStartNotAllowedException("app is in the background (test)")
            },
        )

        refusing.startForeground(alerting(), fullScreen = true)

        assertTimerChannels()
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_ALERT)).isNotNull()
    }

    @Test
    fun `the alert replaces notification 1 as the foreground notification, full screen`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val service = run(startedCommand())

        port.startForeground(alerting(), fullScreen = true)

        // The service already runs in the foreground: no new foreground start is needed (04 §6.3).
        val command = startedCommand()
        assertThat(BrewTimerService.isForegroundRequired(command)).isFalse()
        service.withIntent(command).startCommand(0, 2)
        val shadow = shadowOf(service.get())
        assertThat(shadow.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_ALERT)
        val alert = shadow.lastForegroundNotification
        assertThat(alert.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(alert.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(shadowOf(alert.fullScreenIntent).savedIntent.component)
            .isEqualTo(ComponentName(app, TimerAlertActivity::class.java))
        // D-044: the alert is the app's only notification of importance DEFAULT or higher.
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)).isNull()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
    }

    @Test
    fun `an alert shown again without its start has no full-screen intent`() {
        port.startForeground(alerting(), fullScreen = false)

        val alert = checkNotNull(BrewTimerService.notificationOf(startedCommand()))
        assertThat(alert.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(alert.fullScreenIntent).isNull()
    }

    @Test
    fun `a waiting state keeps the service and its notification but releases the wake lock`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val service = run(startedCommand())

        port.startForeground(ready(), fullScreen = false)
        val command = startedCommand()
        service.withIntent(command).startCommand(0, 2)

        assertThat(BrewTimerService.wakeLockMsOf(command)).isEqualTo(0L)
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
        val shown = shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(shown.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Bereit")
        assertThat(shadowOf(service.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `stopping stops the service and removes notifications 1 and 2`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        run(startedCommand())
        TimerNotifications.post(app, TimerNotifications.ID_ALERT, Notification.Builder(app, "x").build())

        port.stopForeground()

        assertThat(shadowOf(app).nextStoppedService?.component?.className)
            .isEqualTo(BrewTimerService::class.java.name)
        assertThat(shadowOf(notificationManager).allNotifications).isEmpty()
    }

    @Test
    fun `a start right after a stop starts the service in the foreground again`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        run(startedCommand())
        port.stopForeground()

        // *Beenden* and a new session at once, before the old service is destroyed.
        port.startForeground(ready(), fullScreen = false)

        assertThat(BrewTimerService.isForegroundRequired(startedCommand())).isTrue()
    }

    @Test
    fun `a stop before the service started shows its notification first and then stops it`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val pending = startedCommand()

        port.stopForeground()

        // Stopping a service before its promised startForeground would crash the app.
        assertThat(shadowOf(app).nextStoppedService).isNull()
        val service = shadowOf(run(pending).get())
        assertThat(service.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(service.isForegroundStopped).isTrue()
        assertThat(service.isStoppedBySelf).isTrue()
    }

    @Test
    fun `the deferred stop names its start, so a start sent meanwhile keeps the service`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val pending = startedCommand()
        port.stopForeground()

        val service = Robolectric.buildService(BrewTimerService::class.java, pending).create().startCommand(0, 7)

        // stopSelf() would stop unconditionally: a start queued meanwhile would crash the app
        // (startForegroundService without startForeground) or be dropped. With its start ID,
        // Android ignores the stop when a newer start exists.
        assertThat(shadowOf(service.get()).stopSelfResultId).isEqualTo(7)
        assertThat(shadowOf(service.get()).isForegroundStopped).isTrue()
    }

    @Test
    fun `a refused start shows the alert without the service and is logged`() {
        val refusing = portOn(
            object : ContextWrapper(app) {
                override fun startForegroundService(service: Intent): ComponentName =
                    throw ForegroundServiceStartNotAllowedException("app is in the background (test)")
            },
        )
        TimerNotifications.post(app, TimerNotifications.ID_LIVE_UPDATE, Notification.Builder(app, "x").build())

        refusing.startForeground(alerting(), fullScreen = true)

        val alert = shadowOf(notificationManager).getNotification(TimerNotifications.ID_ALERT)
        assertThat(alert.fullScreenIntent).isNotNull()
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)).isNull()
        val refusal = diagnostics.entriesOf<TimerDiagnostic.ServiceStartRefused>().single()
        assertThat(refusal.notificationId).isEqualTo(TimerNotifications.ID_ALERT)
        assertThat(refusal.error).isInstanceOf(ForegroundServiceStartNotAllowedException::class.java)
    }

    @Test
    fun `a refused start is sent again once, with the state given now, and a newer command drops it`() {
        val refusing = refusingPort()
        refusing.startForeground(alerting(), fullScreen = true)

        // *Abgegossen* came meanwhile: the start goes again for READY, not for the refused alert (D-063).
        port.retryRefusedStart(ready(), fullScreen = false)
        val command = startedCommand()
        assertThat(BrewTimerService.notificationIdOf(command)).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(BrewTimerService.isForegroundRequired(command)).isTrue()
        port.retryRefusedStart(ready(), fullScreen = false)
        assertThat(shadowOf(app).nextStartedService).isNull()

        refusing.startForeground(alerting(), fullScreen = true)
        port.startForeground(ready(), fullScreen = false)
        startedCommand()
        port.retryRefusedStart(ready(), fullScreen = false)
        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `an alert sent again keeps its full-screen intent and does not alert twice`() {
        refusingPort().startForeground(alerting(), fullScreen = true)

        port.retryRefusedStart(alerting(), fullScreen = true)

        val alert = checkNotNull(BrewTimerService.notificationOf(startedCommand()))
        assertThat(alert.fullScreenIntent).isNotNull()
        assertThat(alert.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
    }

    @Test
    fun `a start refused again is logged as such and waits for the next try`() {
        val refusing = refusingPort()
        refusing.startForeground(running(remainingMs = 60_000), fullScreen = false)

        refusing.retryRefusedStart(running(remainingMs = 50_000), fullScreen = false)

        assertThat(diagnostics.entriesOf<TimerDiagnostic.ServiceStartRefused>().map { it.again })
            .containsExactly(false, true)
            .inOrder()
        port.retryRefusedStart(running(remainingMs = 40_000), fullScreen = false)
        assertThat(BrewTimerService.notificationIdOf(startedCommand())).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
    }

    @Test
    fun `a state that needs no service drops the refused start`() {
        refusingPort().startForeground(running(remainingMs = 60_000), fullScreen = false)

        port.retryRefusedStart(null, fullScreen = false)
        port.retryRefusedStart(running(remainingMs = 50_000), fullScreen = false)

        assertThat(shadowOf(app).nextStartedService).isNull()
    }

    @Test
    fun `the cpu stays awake from the command until the service holds its own wake lock`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        // The alarm's wake lock ends with the receiver; the service starts later on the main thread.
        val handover = ShadowPowerManager.getLatestWakeLock()
        assertThat(handover.isHeld).isTrue()

        run(startedCommand())

        assertThat(handover.isHeld).isFalse()
        assertThat(ShadowPowerManager.getLatestWakeLock()).isNotSameInstanceAs(handover)
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
    }

    @Test
    fun `a switch to the alert refused in the service leaves the alert as the only notification`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val service = run(startedCommand())
        shadowOf(service.get()).setThrowInStartForeground(
            ForegroundServiceStartNotAllowedException("startForeground() not allowed (test)"),
        )

        port.startForeground(alerting(), fullScreen = true)
        service.withIntent(startedCommand()).startCommand(0, 2)

        assertThat(shadowOf(service.get()).isForegroundStopped).isTrue()
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)).isNull()
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_ALERT).fullScreenIntent)
            .isNotNull()
        assertThat(diagnostics.entriesOf<TimerDiagnostic.ServiceStartRefused>().single().notificationId)
            .isEqualTo(TimerNotifications.ID_ALERT)
        // The wake lock still covers the alert.
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
    }

    @Test
    fun `the service releases its wake lock when it is destroyed`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val service = run(startedCommand())

        service.destroy()

        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
    }

    @Test
    fun `each foreground start of the service and its end are logged`() {
        port.startForeground(running(remainingMs = 60_000), fullScreen = false)
        val service = run(startedCommand())
        // An update of the same notification is no new foreground start.
        port.startForeground(running(remainingMs = 50_000), fullScreen = false)
        service.withIntent(startedCommand()).startCommand(0, 2)
        port.startForeground(alerting(), fullScreen = true)
        service.withIntent(startedCommand()).startCommand(0, 3)

        port.stopForeground()
        service.destroy()

        assertThat(diagnostics.entries.map { it.describe() }).containsExactly(
            "foreground ${TimerNotifications.ID_LIVE_UPDATE}",
            "foreground ${TimerNotifications.ID_ALERT}",
            "stopped",
        ).inOrder()
        assertThat(diagnostics.entries.first().at).isEqualTo(clock.reading())
    }

    private fun TimerDiagnostic.describe(): String = when (this) {
        is TimerDiagnostic.ServiceInForeground -> "foreground $notificationId"
        is TimerDiagnostic.ServiceStopped -> "stopped"
        else -> toString()
    }

    /** A port whose context Android refuses every `startForegroundService` (the background, no exemption). */
    private fun refusingPort() = portOn(
        object : ContextWrapper(app) {
            override fun startForegroundService(service: Intent): ComponentName =
                throw ForegroundServiceStartNotAllowedException("app is in the background (test)")
        },
    )

    private fun portOn(context: android.content.Context) = BrewTimerServicePort(
        context = context,
        clocks = TimerClocks(clock, clock),
        diagnostics = diagnostics,
        notifications = TimerStateNotifications(app),
    )

    /** `brew_timer` (DEFAULT, for promotion) and `steep_finished` (HIGH, for the full-screen alert), 04 §8.1. */
    private fun assertTimerChannels() {
        assertThat(notificationManager.getNotificationChannel(TimerNotifications.CHANNEL_BREW_TIMER)?.importance)
            .isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(notificationManager.getNotificationChannel(TimerNotifications.CHANNEL_STEEP_FINISHED)?.importance)
            .isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    private fun startedCommand(): Intent = checkNotNull(shadowOf(app).nextStartedService) { "no command sent" }

    private fun run(command: Intent): ServiceController<BrewTimerService> =
        Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, 1)

    private fun running(remainingMs: Long) = state(TimerPhase.RUNNING).copy(
        deadlineElapsedMs = clock.elapsed + remainingMs,
        deadlineWallMs = clock.wall + remainingMs,
    )

    private fun alerting() = state(TimerPhase.ALERTING).copy(
        deadlineElapsedMs = clock.elapsed,
        deadlineWallMs = clock.wall,
    )

    private fun ready() = state(TimerPhase.READY).copy(
        steps = listOf(TimerUiStep(StepKind.INFUSION, 60, 60, StepStatus.PENDING)),
    )

    private fun state(phase: TimerPhase) = TimerUiState.Active(
        kind = SessionKind.Brew("s1"),
        teaName = "Bio Sencha Hisui",
        phase = phase,
        stepIndex = 0,
        steps = listOf(TimerUiStep(StepKind.INFUSION, 60, 60, StepStatus.STARTED)),
        deadlineElapsedMs = null,
        deadlineWallMs = null,
        pausedRemainingMs = null,
        preAlertLeadSeconds = 10,
        preAlertAtElapsedMs = null,
        preAlertPlayed = false,
        longStep = false,
        kettleTempC = 60,
        demoted = false,
        finishWouldDiscard = false,
        preAlert = PreAlertChoice.Inherit,
    )
}
