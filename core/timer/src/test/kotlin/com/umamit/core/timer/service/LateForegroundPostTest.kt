package com.umamit.core.timer.service

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
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
import java.time.Duration

/**
 * Android posts the notification of `startForeground` asynchronously (AOSP queues it on the
 * ActivityManager's handler), while an update the service posts itself with `notify` goes
 * straight to the NotificationManager. An update right after the foreground start can arrive
 * first and be overwritten by the start's late post (D-071, 04 §6.3). The service posts such
 * an update once more, shortly after, so the newest content wins; the copy never alerts again.
 *
 * Here, the start's late post is simulated by posting the start's notification again from the
 * test, after the update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LateForegroundPostTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val liveUpdate = TimerNotifications.ID_LIVE_UPDATE

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ForegroundServiceState.reset()
    }

    @After
    fun tearDown() = ForegroundServiceState.reset()

    @Test
    fun `an update right after the foreground start is posted once more and wins over the start's late post`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))

        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)
        lateStartPost("Bereit · 2:00")
        idle(REPOST_DELAY_MS)

        assertThat(shownText(liveUpdate)).isEqualTo("Läuft · 1:59")
        // The copy never rings or vibrates again.
        assertThat(shown(liveUpdate).flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)
        // Exactly one re-post.
        lateStartPost("Bereit · 2:00")
        idle(LONG_MS)
        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
    }

    @Test
    fun `a newer update within the window replaces the pending re-post`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))
        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)
        idle(STEP_MS)

        service.withIntent(command(liveUpdate, "Pausiert · 1:58", foregroundRequired = false)).startCommand(0, 3)
        lateStartPost("Bereit · 2:00")

        // The first update's re-post is gone: nothing is posted when it was due.
        idle(REPOST_DELAY_MS - STEP_MS + 1)
        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
        idle(STEP_MS)
        assertThat(shownText(liveUpdate)).isEqualTo("Pausiert · 1:58")
        lateStartPost("Bereit · 2:00")
        idle(LONG_MS)
        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
    }

    @Test
    fun `an update long after the foreground start is not posted again`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))
        idle(REPOST_WINDOW_MS + 1)

        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)
        lateStartPost("Bereit · 2:00")
        idle(LONG_MS)

        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
    }

    @Test
    fun `a destroyed service posts nothing again`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))
        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)

        service.destroy()
        lateStartPost("Bereit · 2:00")
        idle(LONG_MS)

        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
    }

    @Test
    fun `an update is not posted again once another notification is the foreground notification`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))
        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)

        service.withIntent(command(TimerNotifications.ID_ALERT, "Fertig", foregroundRequired = false))
            .startCommand(0, 3)
        idle(LONG_MS)

        // D-044: the alert stays the only notification.
        assertThat(shadowOf(notificationManager).getNotification(liveUpdate)).isNull()
        assertThat(shownText(TimerNotifications.ID_ALERT)).isEqualTo("Fertig")
    }

    @Test
    fun `an update that left the foreground is not posted again`() {
        val service = start(command(liveUpdate, "Bereit · 2:00", foregroundRequired = true))
        service.withIntent(command(liveUpdate, "Läuft · 1:59", foregroundRequired = false)).startCommand(0, 2)

        // Something else took the service out of the foreground before the re-post was due.
        ForegroundServiceState.foregroundId = 0
        lateStartPost("Bereit · 2:00")
        idle(LONG_MS)

        assertThat(shownText(liveUpdate)).isEqualTo("Bereit · 2:00")
    }

    private fun start(command: Intent): ServiceController<BrewTimerService> =
        Robolectric.buildService(BrewTimerService::class.java, command).create().startCommand(0, 1)

    private fun command(id: Int, text: String, foregroundRequired: Boolean): Intent {
        ForegroundServiceState.commandSent()
        return BrewTimerService.command(app, id, notification(text), wakeLockMs = 0, foregroundRequired)
    }

    /** A notification that would alert (no only-alert-once of its own). */
    private fun notification(text: String): Notification =
        Notification.Builder(app, TimerNotifications.CHANNEL_BREW_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentText(text)
            .build()

    /** The queued post of `startForeground` lands now, after the update (AOSP `ServiceRecord.postNotification`). */
    private fun lateStartPost(text: String) = notificationManager.notify(liveUpdate, notification(text))

    private fun shown(id: Int): Notification = checkNotNull(shadowOf(notificationManager).getNotification(id))

    private fun shownText(id: Int): String? = shown(id).extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private companion object {
        const val REPOST_DELAY_MS = 500L
        const val REPOST_WINDOW_MS = 1_000L
        const val STEP_MS = 100L
        const val LONG_MS = 10_000L
    }
}
