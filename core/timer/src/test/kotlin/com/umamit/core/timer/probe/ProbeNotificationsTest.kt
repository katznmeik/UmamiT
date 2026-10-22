package com.umamit.core.timer.probe

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.TimerNotifications
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Notification content of the probe step (04 §8.2, §8.3, §12 Robolectric). */
@RunWith(RobolectricTestRunner::class)
class ProbeNotificationsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val step =
        ProbeStep(startElapsedMs = 1_000L, startWallMs = 50_000L, durationMs = 60_000L, preAlertLeadMs = 10_000L)

    @Test
    @Config(sdk = [36])
    fun runningLiveUpdateIsAPromotableChronometerCountdownWithProgressStyle() {
        val notification = ProbeNotifications.liveUpdate(context, step, ProbePhase.RUNNING, 16_000L, promote = true)

        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Probe · Testaufguss")
        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Zieht …")
        // Chronometer countdown chip towards the wall-clock deadline.
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isTrue()
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        assertThat(notification.`when`).isEqualTo(step.deadlineWallMs)
        // Promotion request and the platform's own promotability check (Android 16).
        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isTrue()
        assertThat(notification.hasPromotableCharacteristics()).isTrue()

        val style = Notification.Builder.recoverBuilder(context, notification).style
        assertThat(style).isInstanceOf(Notification.ProgressStyle::class.java)
        style as Notification.ProgressStyle
        assertThat(style.progressSegments.map { it.length }).containsExactly(50_000, 10_000).inOrder()
        assertThat(style.progressPoints.map { it.position }).containsExactly(50_000)
        assertThat(style.progress).isEqualTo(15_000)
    }

    @Test
    @Config(sdk = [36])
    fun liveUpdateIsNotPromotedWhenPromotionIsNotAllowed() {
        val notification = ProbeNotifications.liveUpdate(context, step, ProbePhase.RUNNING, 16_000L, promote = false)

        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isFalse()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    @Test
    @Config(sdk = [36])
    fun preAlertedLiveUpdateSaysAlmostDone() {
        val notification = ProbeNotifications.liveUpdate(context, step, ProbePhase.PRE_ALERTED, 52_000L, promote = true)

        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Gleich fertig …")
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
    }

    @Test
    @Config(sdk = [36])
    fun finishedLiveUpdateUsesAShortCriticalTextInsteadOfTheChronometer() {
        val notification = ProbeNotifications.liveUpdate(context, step, ProbePhase.FINISHED, 61_000L, promote = true)

        assertThat(notification.extras.getString(Notification.EXTRA_TEXT)).isEqualTo("Jetzt abgießen!")
        assertThat(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)).isFalse()
        assertThat(notification.shortCriticalText).isEqualTo("Fertig")
    }

    @Test
    @Config(sdk = [35])
    fun onAndroid15TheLiveUpdateIsANormalOngoingNotification() {
        val promote = TimerNotifications.canPromote(context)
        val notification = ProbeNotifications.liveUpdate(context, step, ProbePhase.RUNNING, 16_000L, promote)

        assertThat(promote).isFalse()
        assertThat(NotificationCompat.isRequestPromotedOngoing(notification)).isFalse()
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)).isTrue()
        // ProgressStyle falls back to a plain progress bar.
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX)).isEqualTo(60_000)
        assertThat(notification.extras.getInt(Notification.EXTRA_PROGRESS)).isEqualTo(15_000)
    }

    @Test
    @Config(sdk = [36])
    fun alertHasTheFullScreenIntentOnTheHighChannel() {
        val notification = ProbeNotifications.alert(context)

        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(notification.fullScreenIntent).isNotNull()
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Jetzt abgießen!")
        assertThat(notification.actions.map { it.title.toString() }).containsExactly("Abgegossen")
    }
}
