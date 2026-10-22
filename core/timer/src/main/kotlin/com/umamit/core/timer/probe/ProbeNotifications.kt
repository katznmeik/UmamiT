package com.umamit.core.timer.probe

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umamit.core.timer.LiveUpdateContent
import com.umamit.core.timer.LiveUpdateNotification
import com.umamit.core.timer.R
import com.umamit.core.timer.TimerNotifications

/** Notifications of the probe step (M1-03a): the Live Update (ID 1) and the alert (ID 2), 04 §8.2. */
internal object ProbeNotifications {
    private const val RC_CONTENT = 201
    private const val RC_CANCEL = 202
    private const val RC_FULL_SCREEN = 203
    private const val RC_DONE = 204

    /**
     * The Live Update for [step] in [phase]. ProgressStyle shows the steep
     * time and the pre-alert tail as two segments with a point at the
     * pre-alert; the progress is the elapsed time in milliseconds.
     */
    fun liveUpdate(context: Context, step: ProbeStep, phase: ProbePhase, nowElapsedMs: Long): Notification {
        val matcha = ContextCompat.getColor(context, R.color.timer_matcha)
        val accent = ContextCompat.getColor(context, R.color.timer_warm_accent)
        val duration = step.durationMs.toInt()
        val preAlertAt = step.preAlertOffsetMs?.toInt()
        val segments = if (preAlertAt == null) {
            listOf(LiveUpdateContent.Segment(duration, matcha))
        } else {
            listOf(
                LiveUpdateContent.Segment(preAlertAt, matcha),
                LiveUpdateContent.Segment(duration - preAlertAt, accent),
            )
        }
        val text = when (phase) {
            ProbePhase.RUNNING -> R.string.timer_probe_text_running
            ProbePhase.PRE_ALERTED -> R.string.timer_probe_text_pre_alert
            ProbePhase.FINISHED -> R.string.timer_probe_text_finished
        }
        val finished = phase == ProbePhase.FINISHED
        return LiveUpdateNotification.build(
            context,
            LiveUpdateContent(
                title = context.getString(R.string.timer_probe_step_title),
                text = context.getString(text),
                countdownToWallMs = if (finished) null else step.deadlineWallMs,
                shortCriticalText = if (finished) context.getString(R.string.timer_probe_chip_finished) else null,
                segments = segments,
                points = listOfNotNull(preAlertAt?.let { LiveUpdateContent.Point(it, accent) }),
                progress = if (finished) duration else step.elapsedAt(nowElapsedMs).toInt(),
                contentIntent = launchAppIntent(context),
                actions = listOf(
                    NotificationCompat.Action.Builder(
                        null,
                        context.getString(R.string.timer_probe_action_cancel),
                        ProbeActionReceiver.stopIntent(context, RC_CANCEL),
                    ).build(),
                ),
            ),
        )
    }

    /**
     * „Jetzt abgießen!" on `steep_finished` with the full-screen intent to
     * [ProbeAlertActivity] (04 §8.2). Visual only; the AlertPlayer plays sound
     * and vibration (#16). [ProbeTimerService] posts it as its foreground
     * notification in place of the Live Update (see [TimerNotifications.ID_ALERT]),
     * so it is shown at once rather than after the FGS display delay.
     */
    fun alert(context: Context): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            RC_FULL_SCREEN,
            ProbeAlertActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, TimerNotifications.CHANNEL_STEEP_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_alert_title))
            .setContentText(context.getString(R.string.timer_probe_step_title))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    context.getString(R.string.timer_alert_done),
                    ProbeActionReceiver.stopIntent(context, RC_DONE),
                ).build(),
            )
            .build()
    }

    private fun launchAppIntent(context: Context): PendingIntent? {
        val launch: Intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            RC_CONTENT,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
