package com.umamit.core.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The notification of a timer state (04 §8.2), on the channels and IDs the
 * probe verified on the Galaxy (D-043, D-044): the alert „Jetzt abgießen!"
 * (ID 2, `steep_finished`) while alerting, otherwise the Live Update (ID 1,
 * `brew_timer`, promotion always requested).
 *
 * The content is minimal for now (#42, D-057): the tea or „Schnelltimer", the
 * phase, the countdown chip while running and one progress segment for the
 * step. The content per phase of 04 §8.2, §8.3 with its actions and the
 * full-screen intent to SCR-05 come with M1-11 (#47).
 */
class TimerStateNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    /** The notification ID of [state]: the alert while alerting, else the Live Update (D-044). */
    fun idFor(state: TimerUiState.Active): Int =
        if (state.alerting) TimerNotifications.ID_ALERT else TimerNotifications.ID_LIVE_UPDATE

    /**
     * The notification of [state] at [nowElapsedMs]. The alert carries its
     * full-screen intent only when [fullScreen] (its alert starts now, 04 §4).
     */
    fun build(state: TimerUiState.Active, fullScreen: Boolean, nowElapsedMs: Long): Notification =
        if (state.alerting) alert(state, fullScreen) else liveUpdate(state, nowElapsedMs)

    private fun liveUpdate(state: TimerUiState.Active, nowElapsedMs: Long): Notification {
        val matcha = ContextCompat.getColor(context, R.color.timer_matcha)
        val accent = ContextCompat.getColor(context, R.color.timer_warm_accent)
        val stepMs = state.currentStep?.plannedSeconds?.times(MS_PER_SECOND)?.toInt() ?: 1
        val leadMs = state.preAlertLeadSeconds?.times(MS_PER_SECOND)?.toInt()
        val preAlertAt = leadMs?.let { stepMs - it }?.takeIf { it > 0 }
        val running = state.phase == TimerPhase.RUNNING
        return LiveUpdateNotification.build(
            context,
            LiveUpdateContent(
                title = title(state),
                text = context.getString(phaseText(state)),
                countdownToWallMs = if (running) state.deadlineWallMs else null,
                shortCriticalText = null,
                segments = listOf(LiveUpdateContent.Segment(stepMs, matcha)),
                points = listOfNotNull(preAlertAt?.let { LiveUpdateContent.Point(it, accent) }),
                progress = progressMs(state, stepMs, nowElapsedMs),
                contentIntent = launchAppIntent(),
            ),
        )
    }

    private fun alert(state: TimerUiState.Active, fullScreen: Boolean): Notification {
        val subtitle = title(state)
        val open = PendingIntent.getActivity(
            context,
            RC_ALERT,
            TimerAlertActivity.intent(context, subtitle),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, TimerNotifications.CHANNEL_STEEP_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_alert_title))
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (fullScreen) setFullScreenIntent(open, true) }
            .build()
    }

    private fun title(state: TimerUiState.Active): String =
        state.teaName ?: context.getString(R.string.timer_quick_title)

    private fun phaseText(state: TimerUiState.Active): Int = when (state.phase) {
        TimerPhase.READY -> R.string.timer_state_ready
        TimerPhase.RUNNING -> if (state.preAlertPlayed) R.string.timer_state_pre_alert else R.string.timer_state_running
        TimerPhase.PAUSED -> R.string.timer_state_paused
        TimerPhase.ALERTING -> R.string.timer_alert_title
        TimerPhase.FINISHED -> R.string.timer_state_finished
    }

    /** The elapsed time of the step in ms, within its segment. */
    private fun progressMs(state: TimerUiState.Active, stepMs: Int, nowElapsedMs: Long): Int {
        val remaining = when (state.phase) {
            TimerPhase.RUNNING -> state.deadlineElapsedMs?.let { it - nowElapsedMs }
            TimerPhase.PAUSED -> state.pausedRemainingMs
            TimerPhase.FINISHED -> 0L
            TimerPhase.READY, TimerPhase.ALERTING -> null
        } ?: stepMs.toLong()
        return (stepMs - remaining).coerceIn(0, stepMs.toLong()).toInt()
    }

    private fun launchAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            RC_CONTENT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val MS_PER_SECOND = 1_000
        const val RC_CONTENT = 301
        const val RC_ALERT = 302
    }
}
