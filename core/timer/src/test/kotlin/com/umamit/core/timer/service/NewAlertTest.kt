package com.umamit.core.timer.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
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
 * An alert that starts while another alert is the service's notification 2 (04 §8.2, §9.3; D-098, #135): the
 * brew's alert taking over a ringing quick alert, or a held quick alert released when the brew session ends during
 * its alert. System UI launches a full-screen intent only for a notification it adds, never for an update
 * ([SystemLikeNotificationManager.fullScreenLaunches]), so the service posts the new alert as a new notification:
 * the earlier one goes with the foreground first. The same alert shown again stays an update and never takes the
 * screen twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [SystemLikeNotificationManager::class, SystemLikeService::class])
class NewAlertTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val system = SystemLikeNotificationManager.of(notificationManager)
    private val alert = TimerNotifications.ID_ALERT

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
        SystemLikeService.reset()
        TimerNotifications.ensureChannels(app)
    }

    @After
    fun tearDown() {
        ForegroundServiceState.reset()
        SystemLikeService.reset()
    }

    @Test
    fun `an alert that starts while another alert is shown is a new notification and takes the screen`() {
        val service = start(command(alertOf("Schnelltimer", startsNow = true), foregroundRequired = true))

        service.withIntent(command(alertOf("Gyokuro · Aufguss 2", startsNow = true))).startCommand(0, 2)

        assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly("Schnelltimer", "Gyokuro · Aufguss 2")
            .inOrder()
        assertThat(notificationManager.activeNotifications.map { it.id }).containsExactly(alert)
        assertThat(shadowOf(service.get()).lastForegroundNotificationId).isEqualTo(alert)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(alert)
    }

    @Test
    fun `the same alert shown again is updated in place and never takes the screen twice`() {
        val service = start(command(alertOf("Schnelltimer", startsNow = true), foregroundRequired = true))
        // A re-sent start (a longer wake lock, D-098): only alert once.
        val again = alertOf("Schnelltimer", startsNow = true).apply {
            flags = flags or Notification.FLAG_ONLY_ALERT_ONCE
        }

        service.withIntent(command(again)).startCommand(0, 2)
        service.withIntent(command(alertOf("Schnelltimer", startsNow = false))).startCommand(0, 3)

        assertThat(system.fullScreenLaunches).hasSize(1)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(alert)
    }

    @Test
    fun `a new alert whose foreground start is refused is posted alone and still takes the screen`() {
        val service = start(command(alertOf("Schnelltimer", startsNow = true), foregroundRequired = true))
        SystemLikeService.refuseStartForegroundWith(alert)

        service.withIntent(command(alertOf("Gyokuro · Aufguss 2", startsNow = true))).startCommand(0, 2)

        assertThat(system.fullScreenLaunches.map { it.text() }).containsExactly("Schnelltimer", "Gyokuro · Aufguss 2")
            .inOrder()
        assertThat(notificationManager.activeNotifications.map { it.id }).containsExactly(alert)
        assertThat(ForegroundServiceState.foregroundId).isEqualTo(0)
    }

    private fun start(command: Intent): ServiceController<BrewTimerService> =
        Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, 1)

    private fun command(notification: Notification, foregroundRequired: Boolean = false): Intent {
        ForegroundServiceState.commandSent()
        return BrewTimerService.command(app, alert, notification, wakeLockMs = 0, foregroundRequired)
    }

    /** An alert notification as `TimerStateNotifications` builds it: full screen when it [startsNow], else quiet. */
    private fun alertOf(text: String, startsNow: Boolean): Notification =
        Notification.Builder(app, TimerNotifications.CHANNEL_STEEP_FINISHED)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Jetzt abgießen!")
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .apply {
                if (startsNow) {
                    setFullScreenIntent(
                        PendingIntent.getActivity(app, 0, Intent(Intent.ACTION_MAIN), PendingIntent.FLAG_IMMUTABLE),
                        true,
                    )
                } else {
                    setOnlyAlertOnce(true)
                }
            }
            .build()

    private fun Notification.text() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
}
