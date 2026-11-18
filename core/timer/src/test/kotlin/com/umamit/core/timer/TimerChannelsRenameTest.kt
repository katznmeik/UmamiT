package com.umamit.core.timer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The channels renamed in v0.8.0 (GEN-04, D-128, 03 §3.7): an install that created them with the old names keeps
 * their ids, so the per-channel settings stay, and carries the new names once [TimerNotifications.ensureChannels]
 * runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimerChannelsRenameTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(NotificationManager::class.java)

    @Test
    fun `the old Bruehtimer and Sitzungen channels keep their ids and take the new names`() {
        manager.createNotificationChannel(
            NotificationChannel(
                TimerNotifications.CHANNEL_BREW_TIMER,
                "Brühtimer",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(TimerNotifications.CHANNEL_SESSIONS, "Sitzungen", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Automatisch gespeicherte Sitzungen" },
        )

        TimerNotifications.ensureChannels(app)

        val brew = manager.getNotificationChannel(TimerNotifications.CHANNEL_BREW_TIMER)
        val sessions = manager.getNotificationChannel(TimerNotifications.CHANNEL_SESSIONS)
        assertThat(brew.id).isEqualTo("brew_timer")
        assertThat(brew.name.toString()).isEqualTo("Aufguss-Timer")
        assertThat(sessions.id).isEqualTo("sessions")
        assertThat(sessions.name.toString()).isEqualTo("Sessions")
        assertThat(sessions.description).isEqualTo("Automatisch gespeicherte Sessions")
        assertThat(manager.notificationChannels.map { it.id })
            .containsExactly("brew_timer", "steep_finished", "sessions", "timer_other")
    }
}
