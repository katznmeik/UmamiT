package com.umamit.core.timer.probe

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.timer.ExactAlarmReceiver
import com.umamit.core.timer.TimerAlarm
import com.umamit.core.timer.TimerNotifications
import com.umamit.core.timer.alert.Haptic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Probe controller and service (M1-03a): alarms, foreground service, alert (04 §12 Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProbeTimerTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    @Before
    fun setUp() {
        shadowOf(context).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        // USE_EXACT_ALARM is granted at install on a device; Robolectric defaults to false.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        ProbeTimer.stepDurationOverride = null
        ProbeTimer.stop(context)
    }

    @Test
    fun startSchedulesPreAlertAndDeadlineAndStartsTheForegroundService() {
        ProbeTimer.stepDurationOverride = 15.seconds
        val now = SystemClock.elapsedRealtime()

        val active = ProbeTimer.start(context)

        assertThat(active.exactAlarms).isTrue()
        assertThat(active.step.durationMs).isEqualTo(15_000L)
        assertThat(ProbeTimer.status.value).isEqualTo(active)
        val alarms = shadowOf(alarmManager).scheduledAlarms.sortedBy { it.triggerAtMs }
        assertThat(alarms.map { it.triggerAtMs }).containsExactly(now + 5_000L, now + 15_000L).inOrder()
        assertThat(alarms.map { it.type }).containsExactly(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
        )
        val started = shadowOf(context).nextStartedService
        assertThat(started.component?.className).isEqualTo(ProbeTimerService::class.java.name)
        assertThat(started.action).isEqualTo(ProbeTimerService.ACTION_START)
        assertThat(ProbeStep.readFrom(started)).isEqualTo(active.step)
    }

    @Test
    fun defaultStepIsOneMinuteWithPreAlertTenSecondsBeforeTheEnd() {
        val active = ProbeTimer.start(context)

        assertThat(active.step.durationMs).isEqualTo(60_000L)
        assertThat(active.step.preAlertOffsetMs).isEqualTo(50_000L)
    }

    @Test
    fun serviceRunsInTheForegroundWithTheLiveUpdate() {
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)

        val service = shadowOf(controller.get())
        assertThat(service.isLastForegroundNotificationAttached).isTrue()
        assertThat(service.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        val notification = service.lastForegroundNotification
        assertThat(notification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notificationManager.getNotificationChannel(TimerNotifications.CHANNEL_BREW_TIMER).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
        assertThat(notificationManager.getNotificationChannel(TimerNotifications.CHANNEL_STEEP_FINISHED).importance)
            .isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun deadlineReplacesTheLiveUpdateWithTheFullScreenAlert() {
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)

        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step))
            .startCommand(0, 2)

        // The alert is the foreground notification and the only notification left, so
        // Android 16 cannot force-group it with the Live Update and mark it silent.
        val service = shadowOf(controller.get())
        assertThat(service.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_ALERT)
        val alert = shadowOf(notificationManager).getNotification(TimerNotifications.ID_ALERT)
        assertThat(alert).isNotNull()
        assertThat(alert.fullScreenIntent).isNotNull()
        assertThat(alert.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(service.lastForegroundNotification.channelId).isEqualTo(TimerNotifications.CHANNEL_STEEP_FINISHED)
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)).isNull()
        assertThat(shadowOf(notificationManager).allNotifications).hasSize(1)
    }

    @Test
    fun aRepeatedDeadlineKeepsTheAlertAsTheOnlyNotification() {
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)
        val deadline = ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step)

        controller.withIntent(deadline).startCommand(0, 2)
        controller.withIntent(deadline).startCommand(0, 3)
        // The 15 s refresh no longer posts the Live Update once the alert replaced it.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))

        assertThat(shadowOf(controller.get()).lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_ALERT)
        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_LIVE_UPDATE)).isNull()
    }

    @Test
    fun aNewStepAfterTheAlertMakesTheLiveUpdateTheForegroundNotificationAgain() {
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)
        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step))
            .startCommand(0, 2)

        val next = testStep().copy(startElapsedMs = step.startElapsedMs + 1)
        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, next))
            .startCommand(0, 3)

        val service = shadowOf(controller.get())
        assertThat(service.lastForegroundNotificationId).isEqualTo(TimerNotifications.ID_LIVE_UPDATE)
        assertThat(service.lastForegroundNotification.channelId).isEqualTo(TimerNotifications.CHANNEL_BREW_TIMER)
    }

    @Test
    fun deadlineInVibrateModeVibratesTheAlertPatternUntilTheServiceStops() {
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)

        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_PRE_ALERT, step))
            .startCommand(0, 2)
        assertThat(shadowOf(vibrator).pattern.toList()).isEqualTo(Haptic.PRE_ALERT.timings)
        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step))
            .startCommand(0, 3)

        assertThat(shadowOf(vibrator).isVibrating).isTrue()
        assertThat(shadowOf(vibrator).pattern.toList()).isEqualTo(Haptic.ALERT.timings)
        assertThat(shadowOf(audioManager).lastAudioFocusRequest).isNull()

        controller.destroy()
        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }

    @Test
    fun deadlineInSilentModeIsVisualOnly() {
        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)

        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step))
            .startCommand(0, 2)

        assertThat(shadowOf(notificationManager).getNotification(TimerNotifications.ID_ALERT)).isNotNull()
        assertThat(shadowOf(vibrator).isVibrating).isFalse()
        assertThat(shadowOf(audioManager).lastAudioFocusRequest).isNull()
    }

    @Test
    fun deadlineInNormalModeTakesTheFocusAndVibrates() {
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val step = testStep()
        val controller = Robolectric.buildService(
            ProbeTimerService::class.java,
            ProbeTimerService.intent(context, ProbeTimerService.ACTION_START, step),
        ).create().startCommand(0, 1)

        controller.withIntent(ProbeTimerService.intent(context, ProbeTimerService.ACTION_DEADLINE, step))
            .startCommand(0, 2)

        val focus = shadowOf(audioManager).lastAudioFocusRequest.audioFocusRequest
        assertThat(focus.audioAttributes.usage).isEqualTo(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        assertThat(shadowOf(vibrator).pattern.toList()).isEqualTo(Haptic.ALERT.timings)

        controller.destroy()
        assertThat(shadowOf(audioManager).lastAbandonedAudioFocusRequest).isSameInstanceAs(focus)
        assertThat(shadowOf(vibrator).isVibrating).isFalse()
    }

    @Test
    fun alarmsAdvanceThePhaseOnlyForwardAndStartTheService() {
        val active = ProbeTimer.start(context)
        shadowOf(context).clearStartedServices()

        ProbeTimer.onAlarm(context, TimerAlarm.DEADLINE, alarmIntent(TimerAlarm.DEADLINE, active.step))
        ProbeTimer.onAlarm(context, TimerAlarm.PRE_ALERT, alarmIntent(TimerAlarm.PRE_ALERT, active.step))

        assertThat((ProbeTimer.status.value as ProbeStatus.Active).phase).isEqualTo(ProbePhase.FINISHED)
        assertThat(shadowOf(context).nextStartedService.action).isEqualTo(ProbeTimerService.ACTION_DEADLINE)
        assertThat(shadowOf(context).nextStartedService.action).isEqualTo(ProbeTimerService.ACTION_PRE_ALERT)
    }

    @Test
    fun stopCancelsAlarmsAndReturnsToIdle() {
        ProbeTimer.start(context)

        ProbeTimer.stop(context)

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
        assertThat(ProbeTimer.status.value).isEqualTo(ProbeStatus.Idle)
    }

    private fun testStep() = ProbeStep(
        startElapsedMs = SystemClock.elapsedRealtime(),
        startWallMs = System.currentTimeMillis(),
        durationMs = 15_000L,
        preAlertLeadMs = 10_000L,
    )

    private fun alarmIntent(alarm: TimerAlarm, step: ProbeStep): Intent =
        step.writeTo(Intent(context, ExactAlarmReceiver::class.java).setAction(alarm.action))
}
