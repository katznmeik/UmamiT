package com.umamit.core.timer.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.TimerNotifications
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
 * A notification that a killed process left flagged as a foreground notification, although no
 * service owns it any more (D-084, 04 §6.3). AOSP creates one when the process dies while it
 * posts an update of its foreground notification: the system cancels the notification for the
 * dead service, and the update, already flagged, is posted after it. The app can never cancel
 * it; only a foreground switch away from its ID removes it, as the system.
 *
 * Starting in the foreground, the service first takes such a notification of the other timer ID
 * over, so the switch to its own ID removes it before its own notification is posted: the
 * alert never appears next to notification 1 (D-044), nor a stale alert next to notification 1.
 *
 * Limits: the shadows ([SystemLikeNotificationManager], [SystemLikeService]) model the AOSP
 * rules that make the notification stay and go, not the race that leaves it behind, nor the
 * asynchronous order of the system's posts and cancels. The emulator test `ProcessDeathTest`
 * stays the end-to-end check.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [SystemLikeNotificationManager::class, SystemLikeService::class])
class StaleForegroundNotificationTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val system = SystemLikeNotificationManager.of(notificationManager)
    private val reports = mutableListOf<ServiceReport>()
    private val liveUpdate = TimerNotifications.ID_LIVE_UPDATE
    private val alert = TimerNotifications.ID_ALERT

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        SystemLikeService.reset()
        TimerNotifications.ensureChannels(app)
        ForegroundServiceState.reporter = { reports += it }
    }

    @After
    fun tearDown() {
        ForegroundServiceState.reset()
        ForegroundServiceState.reporter = null
        SystemLikeService.reset()
    }

    @Test
    fun `the app cannot cancel a stale foreground notification`() {
        leftByKilledProcess(liveUpdate)

        TimerNotifications.cancel(app, liveUpdate)

        assertThat(showing()).containsExactly(liveUpdate)
    }

    @Test
    fun `a killed step's notification 1 is gone before the new process's alert appears`() {
        leftByKilledProcess(liveUpdate)

        val service = start(command(alert, "Jetzt abgießen!", foregroundRequired = true))

        assertThat(showing()).containsExactly(alert)
        assertThat(system.showingWhenFirstPosted[alert]).doesNotContain(liveUpdate)
        assertThat(shadowOf(service.get()).lastForegroundNotificationId).isEqualTo(alert)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(alert)
        assertThat(reports).containsExactly(
            ServiceReport.TookOverStale(liveUpdate),
            ServiceReport.InForeground(alert),
        ).inOrder()
    }

    @Test
    fun `a killed alert does not stay next to the new process's notification 1`() {
        leftByKilledProcess(alert)

        start(command(liveUpdate, "Bereit · 0:20", foregroundRequired = true))

        assertThat(showing()).containsExactly(liveUpdate)
        assertThat(system.showingWhenFirstPosted[liveUpdate]).doesNotContain(alert)
    }

    @Test
    fun `the taken-over copy never alerts again and opens no screen`() {
        leftByKilledProcess(alert, fullScreen = true)
        val copies = mutableListOf<Notification>()
        system.onPost = { id, posted -> if (id == alert) copies += posted }

        start(command(liveUpdate, "Bereit · 0:20", foregroundRequired = true))

        val copy = copies.single()
        assertThat(copy.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
        assertThat(copy.fullScreenIntent).isNull()
    }

    @Test
    fun `notification 1 posted without the service is cancelled without a takeover`() {
        // A refused start posted it alone (04 §6.3): an ordinary notification the app may cancel.
        notificationManager.notify(liveUpdate, notification(TimerNotifications.CHANNEL_BREW_TIMER, "Läuft · 0:12"))

        start(command(alert, "Jetzt abgießen!", foregroundRequired = true))

        assertThat(showing()).containsExactly(alert)
        assertThat(reports).containsExactly(ServiceReport.InForeground(alert))
    }

    @Test
    fun `a service in the foreground switches to the alert without a takeover`() {
        val service = start(command(liveUpdate, "Läuft · 0:12", foregroundRequired = true))

        service.withIntent(command(alert, "Jetzt abgießen!", foregroundRequired = false)).startCommand(0, 2)

        assertThat(showing()).containsExactly(alert)
        assertThat(reports).containsExactly(
            ServiceReport.InForeground(liveUpdate),
            ServiceReport.InForeground(alert),
        ).inOrder()
    }

    @Test
    fun `a refused takeover leaves the alert to start as usual`() {
        leftByKilledProcess(liveUpdate)
        SystemLikeService.refuseStartForegroundWith(liveUpdate)

        val service = start(command(alert, "Jetzt abgießen!", foregroundRequired = true))

        assertThat(shadowOf(service.get()).lastForegroundNotificationId).isEqualTo(alert)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(alert)
        assertThat(reports).containsExactly(ServiceReport.InForeground(alert))
        // Nothing else can remove it; the alert is shown anyway.
        assertThat(showing()).containsExactly(liveUpdate, alert)
    }

    @Test
    fun `an alert refused after the takeover is posted alone and the stale notification is gone`() {
        leftByKilledProcess(liveUpdate)
        SystemLikeService.refuseStartForegroundWith(alert)

        start(command(alert, "Jetzt abgießen!", foregroundRequired = true))

        assertThat(showing()).containsExactly(alert)
        assertThat(notificationManager.activeNotifications.single().notification.flags and FOREGROUND)
            .isEqualTo(0)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(0)
        assertThat(reports.map { it::class }).containsExactly(
            ServiceReport.TookOverStale::class,
            ServiceReport.Refused::class,
        ).inOrder()
    }

    /** What NotificationManagerService shows after the death race: flagged, ongoing, no clear. */
    private fun leftByKilledProcess(id: Int, fullScreen: Boolean = false) {
        val stale = notification(channelOf(id), "vom beendeten Prozess", fullScreen)
        stale.flags = stale.flags or FOREGROUND or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR
        notificationManager.notify(id, stale)
    }

    private fun start(command: Intent): ServiceController<BrewTimerService> =
        Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, 1)

    private fun command(id: Int, text: String, foregroundRequired: Boolean): Intent {
        ForegroundServiceState.commandSent()
        return BrewTimerService.command(app, id, notification(channelOf(id), text), wakeLockMs = 0, foregroundRequired)
    }

    private fun channelOf(id: Int): String =
        if (id == alert) TimerNotifications.CHANNEL_STEEP_FINISHED else TimerNotifications.CHANNEL_BREW_TIMER

    private fun notification(channel: String, text: String, fullScreen: Boolean = false): Notification =
        Notification.Builder(app, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentText(text)
            .apply {
                if (fullScreen) {
                    setFullScreenIntent(
                        android.app.PendingIntent.getActivity(
                            app,
                            0,
                            Intent(Intent.ACTION_MAIN),
                            android.app.PendingIntent.FLAG_IMMUTABLE,
                        ),
                        true,
                    )
                }
            }
            .build()

    private fun showing(): List<Int> = notificationManager.activeNotifications.map { it.id }.sorted()

    private companion object {
        const val FOREGROUND = Notification.FLAG_FOREGROUND_SERVICE
    }
}
