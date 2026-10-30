package com.umamit.core.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umamit.core.common.ClockReading
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.notification.LiveUpdateProgress
import com.umamit.core.timer.notification.TimerActionReceiver
import com.umamit.core.timer.notification.TimerNotificationActions
import com.umamit.core.timer.notification.TimerNotificationKey
import com.umamit.core.timer.notification.TimerNotificationTexts
import com.umamit.core.timer.notification.TimerScreenIntents
import com.umamit.core.timer.notification.runsWithService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The notification of a timer state (04 §8.2, §8.3; TMR-06, TMR-14, TMR-18, TMR-41), on the
 * channels and IDs verified on the Galaxy (D-043, D-044):
 *
 * - **Alerting:** the alert „Jetzt abgießen!" (ID 2, `steep_finished`, `CATEGORY_ALARM`) with
 *   *Abgegossen* (*Weggegossen* for a rinse, *Fertig* for the quick timer); its full-screen
 *   intent opens the timer screen only when the alert starts in the same transition.
 * - **With the service:** the Live Update (ID 1, `brew_timer`, promotion always requested):
 *   title, text and actions per phase, the chronometer chip while running, a short critical
 *   text otherwise, and one ProgressStyle segment per step with the pre-alert point.
 * - **Without the service** ([ID_QUIET][TimerNotifications.ID_QUIET]): a long running step as
 *   a Live Update „Fertig um 14:30", and a demoted session as a regular ongoing notification
 *   with *Start* or *Weiter* and *Sitzung beenden* (D-064).
 *
 * Every one opens the timer screen (SCR-05) when tapped; a Live Update the user dismisses
 * reports it through its delete intent (04 §8.3).
 */
class TimerStateNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val texts = TimerNotificationTexts(context)
    private val actions = TimerNotificationActions(context)

    /** The ID of [state]'s notification: the alert while alerting, the Live Update with the service, else quiet. */
    fun idFor(state: TimerUiState.Active): Int = when {
        state.alerting -> TimerNotifications.ID_ALERT
        state.runsWithService -> TimerNotifications.ID_LIVE_UPDATE
        else -> TimerNotifications.ID_QUIET
    }

    /**
     * The notification of [state] at [now]. The alert carries its full-screen intent only when
     * [fullScreen] (its alert starts now, 04 §4); shown again, it does not alert once more.
     */
    fun build(state: TimerUiState.Active, fullScreen: Boolean, now: ClockReading): Notification =
        if (state.alerting) alert(state, fullScreen) else liveUpdate(state, now)

    /**
     * Notification 5, the other active timer (04 §8.2, D-098), on `timer_other` (LOW, silent, never promoted):
     * the title and text of its phase, a countdown while it runs, its own actions; a held quick alert reads
     * „Fertig, wartet auf den Aufguss" and has no full-screen intent. It opens the timer.
     */
    fun other(state: TimerUiState.Active, now: ClockReading): Notification {
        val countdown = state.phase == TimerPhase.RUNNING && !state.longStep
        val text = when {
            state.alertHeld -> context.getString(R.string.timer_text_quick_held)
            state.alerting -> texts.alertText(state)
            else -> texts.text(state, now)
        }
        return NotificationCompat.Builder(context, TimerNotifications.CHANNEL_TIMER_OTHER)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(texts.title(state))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(countdown)
            .setUsesChronometer(countdown)
            .apply { if (countdown) setWhen(checkNotNull(state.deadlineWallMs)).setChronometerCountDown(true) }
            .setContentIntent(openTimerIntent(RC_OTHER))
            .apply { actions.of(state).forEach(::addAction) }
            .build()
    }

    /** „Sitzung gespeichert" (ID 3, `sessions`, TMR-14) for the session of [teaName]. */
    fun sessionSaved(teaName: String?): Notification =
        NotificationCompat.Builder(context, TimerNotifications.CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_session_saved_title))
            .setContentText(
                teaName?.let { context.getString(R.string.timer_session_saved_text, it) }
                    ?: context.getString(R.string.timer_session_saved_text_no_tea),
            )
            .setAutoCancel(true)
            .setContentIntent(launchAppIntent())
            .build()

    /**
     * „Dein Timer läuft weiter" after a reboot (04 §7.3, D-063), on `sessions` (LOW) with ID 3 like
     * „Sitzung gespeichert", so it never sits next to the alert (D-044). It opens the timer.
     */
    fun timerResumed(state: TimerUiState.Active): Notification =
        NotificationCompat.Builder(context, TimerNotifications.CHANNEL_SESSIONS)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_resumed_title))
            .setContentText(context.getString(R.string.timer_resumed_text, texts.teaOf(state)))
            .setAutoCancel(true)
            .setContentIntent(openTimerIntent(RC_RESUMED))
            .build()

    private fun liveUpdate(state: TimerUiState.Active, now: ClockReading): Notification {
        val progress = LiveUpdateProgress.of(state, now.elapsedMs)
        val matcha = ContextCompat.getColor(context, R.color.timer_matcha)
        val rinse = ContextCompat.getColor(context, R.color.timer_rinse)
        val accent = ContextCompat.getColor(context, R.color.timer_warm_accent)
        val running = state.phase == TimerPhase.RUNNING
        return LiveUpdateNotification.build(
            context,
            LiveUpdateContent(
                title = texts.title(state),
                text = texts.text(state, now),
                countdownToWallMs = if (running) state.deadlineWallMs else null,
                shortCriticalText = if (running) null else texts.chip(state),
                segments = progress.segments.map {
                    LiveUpdateContent.Segment(it.length, if (it.rinse) rinse else matcha)
                },
                points = listOfNotNull(progress.preAlertAt?.let { LiveUpdateContent.Point(it, accent) }),
                progress = progress.progress,
                contentIntent = openTimerIntent(RC_CONTENT),
                actions = actions.of(state),
                // A demoted session is a regular, quiet notification (TMR-14).
                requestPromotion = !state.demoted,
                deleteIntent = PendingIntent.getBroadcast(
                    context,
                    RC_DISMISS,
                    TimerActionReceiver.dismissIntent(context, TimerNotificationKey.of(state)),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            ),
        )
    }

    private fun alert(state: TimerUiState.Active, fullScreen: Boolean): Notification =
        NotificationCompat.Builder(context, TimerNotifications.CHANNEL_STEEP_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_alert_title))
            .setContentText(texts.alertText(state))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openTimerIntent(RC_CONTENT))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { actions.of(state).forEach(::addAction) }
            .apply {
                // The alert takes over the screen once, when it starts; shown again (an update in
                // the same alert, a re-sent start) it must not alert a second time (D-057).
                if (fullScreen) setFullScreenIntent(openTimerIntent(RC_FULL_SCREEN), true) else setOnlyAlertOnce(true)
            }
            .build()

    private fun openTimerIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        TimerScreenIntents.openTimer(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun launchAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                RC_SESSION_SAVED,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    private companion object {
        const val RC_CONTENT = 301
        const val RC_FULL_SCREEN = 302
        const val RC_DISMISS = 303
        const val RC_SESSION_SAVED = 304
        const val RC_RESUMED = 305
        const val RC_OTHER = 306
    }
}
