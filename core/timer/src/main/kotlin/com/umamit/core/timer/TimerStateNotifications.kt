package com.umamit.core.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.notification.TimerActionReceiver
import com.umamit.core.timer.notification.TimerNotificationKey
import com.umamit.core.timer.notification.TimerScreenIntents
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * The notification of a timer state (04 §8.2, §8.3; TMR-06, TMR-14, TMR-18, TMR-41), on the
 * channels and IDs the probe verified on the Galaxy (D-043, D-044):
 *
 * - **Alerting:** the alert „Jetzt abgießen!" (ID 2, `steep_finished`, `CATEGORY_ALARM`) with
 *   *Abgegossen* (*Weggegossen* for a rinse, *Fertig* for the quick timer); its full-screen
 *   intent opens the timer screen only when the alert starts in the same transition.
 * - **With the service:** the Live Update (ID 1, `brew_timer`, promotion always requested):
 *   title, text and actions per phase, the chronometer chip while running, a short critical
 *   text otherwise, and one ProgressStyle segment per step with the pre-alert point.
 * - **Without the service** ([ID_QUIET][TimerNotifications.ID_QUIET]): a long running step as
 *   a Live Update „Fertig um 14:30", and a demoted session as a regular ongoing notification
 *   with *Start* or *Weiter* and *Sitzung beenden* (D-NNN).
 *
 * Every one opens the timer screen (SCR-05) when tapped; a Live Update the user dismisses
 * reports it through its delete intent (04 §8.3).
 */
class TimerStateNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    /** The notification ID of [state]: the alert while alerting, the Live Update with the service, else the quiet one. */
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

    private fun liveUpdate(state: TimerUiState.Active, now: ClockReading): Notification {
        val progress = LiveUpdateProgress.of(state, now.elapsedMs)
        val matcha = ContextCompat.getColor(context, R.color.timer_matcha)
        val rinse = ContextCompat.getColor(context, R.color.timer_rinse)
        val accent = ContextCompat.getColor(context, R.color.timer_warm_accent)
        val running = state.phase == TimerPhase.RUNNING
        return LiveUpdateNotification.build(
            context,
            LiveUpdateContent(
                title = title(state),
                text = text(state, now),
                countdownToWallMs = if (running) state.deadlineWallMs else null,
                shortCriticalText = if (running) null else chip(state),
                segments = progress.segments.map {
                    LiveUpdateContent.Segment(it.length, if (it.rinse) rinse else matcha)
                },
                points = listOfNotNull(progress.preAlertAt?.let { LiveUpdateContent.Point(it, accent) }),
                progress = progress.progress,
                contentIntent = openTimerIntent(RC_CONTENT),
                actions = actionsOf(state).map(::action),
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

    private fun alert(state: TimerUiState.Active, fullScreen: Boolean): Notification {
        val step = state.currentStep
        val text = when {
            state.kind == SessionKind.Quick || step == null -> context.getString(R.string.timer_quick_title)
            else -> context.getString(R.string.timer_alert_text, teaOf(state), stepWord(step.kind), state.stepIndex + 1)
        }
        return NotificationCompat.Builder(context, TimerNotifications.CHANNEL_STEEP_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_umamit)
            .setColor(ContextCompat.getColor(context, R.color.timer_matcha))
            .setContentTitle(context.getString(R.string.timer_alert_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openTimerIntent(RC_CONTENT))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { actionsOf(state).forEach { addAction(action(it)) } }
            .apply {
                // The alert takes over the screen once, when it starts; shown again (an update in
                // the same alert, a re-sent start) it must not alert a second time (D-057).
                if (fullScreen) setFullScreenIntent(openTimerIntent(RC_FULL_SCREEN), true) else setOnlyAlertOnce(true)
            }
            .build()
    }

    private fun title(state: TimerUiState.Active): String {
        if (state.kind == SessionKind.Quick) return context.getString(R.string.timer_quick_title)
        val step = state.currentStep
            ?: return context.getString(R.string.timer_title_finished, teaOf(state))
        return context.getString(
            R.string.timer_title_step,
            teaOf(state),
            stepWord(step.kind),
            state.stepIndex + 1,
            state.steps.size,
        )
    }

    private fun text(state: TimerUiState.Active, now: ClockReading): String {
        val temp = state.kettleTempC
        val planned = state.currentStep?.plannedSeconds?.toLong() ?: 0L
        return when (state.phase) {
            TimerPhase.READY -> if (temp != null) {
                context.getString(R.string.timer_text_ready_kettle, clock(planned), temp)
            } else {
                context.getString(R.string.timer_text_ready, clock(planned))
            }

            TimerPhase.RUNNING -> when {
                state.longStep -> context.getString(R.string.timer_text_long_step, timeOfDay(state, now))
                state.preAlertPlayed -> context.getString(R.string.timer_text_pre_alert)
                temp != null -> context.getString(R.string.timer_text_running_temp, temp)
                else -> context.getString(R.string.timer_text_running)
            }

            TimerPhase.PAUSED -> context.getString(
                R.string.timer_text_paused,
                clock(state.display(now.elapsedMs).remainingSeconds),
            )

            TimerPhase.FINISHED -> context.getString(R.string.timer_text_finished)

            TimerPhase.ALERTING -> context.getString(R.string.timer_alert_title)
        }
    }

    /** The short critical text of the chip outside RUNNING (04 §8.3). */
    private fun chip(state: TimerUiState.Active): String = when (state.phase) {
        TimerPhase.PAUSED -> context.getString(R.string.timer_chip_paused)
        TimerPhase.FINISHED -> context.getString(R.string.timer_chip_finished)
        else -> state.kettleTempC?.let { context.getString(R.string.timer_chip_temp, it) }
            ?: context.getString(R.string.timer_chip_ready)
    }

    /** The actions of [state] (04 §8.2, §10; TMR-14, TMR-41), the primary one first. */
    private fun actionsOf(state: TimerUiState.Active): List<NotificationAction> {
        val quick = state.kind == SessionKind.Quick
        val primary = when (state.phase) {
            TimerPhase.READY -> NotificationAction(R.string.timer_action_start, TimerEvent.Start)
            TimerPhase.RUNNING -> NotificationAction(R.string.timer_action_pause, TimerEvent.Pause)
            TimerPhase.PAUSED -> NotificationAction(R.string.timer_action_resume, TimerEvent.Resume)
            TimerPhase.ALERTING -> NotificationAction(pouredLabel(state), TimerEvent.Poured)
            TimerPhase.FINISHED -> finishAction(state)
        }
        val secondary = when {
            quick && state.phase != TimerPhase.ALERTING -> NotificationAction(
                R.string.timer_action_cancel,
                TimerEvent.Cancel,
            )

            state.demoted && state.phase != TimerPhase.FINISHED -> finishAction(state)

            else -> null
        }
        return listOfNotNull(primary, secondary)
    }

    /** *Sitzung beenden*, unless it would discard the session: only the app may ask first (TMR-12 AC1). */
    private fun finishAction(state: TimerUiState.Active): NotificationAction? =
        NotificationAction(R.string.timer_action_finish_session, TimerEvent.FinishSession)
            .takeUnless { state.finishWouldDiscard }

    private fun pouredLabel(state: TimerUiState.Active): Int = when {
        state.kind == SessionKind.Quick -> R.string.timer_action_quick_done
        state.currentStep?.kind == StepKind.RINSE -> R.string.timer_action_poured_rinse
        else -> R.string.timer_action_poured
    }

    private fun action(action: NotificationAction): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            TimerActionReceiver.requestCodeOf(action.event),
            TimerActionReceiver.intent(context, action.event),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(null, context.getString(action.label), intent).build()
    }

    private fun stepWord(kind: StepKind): String = context.getString(
        when (kind) {
            StepKind.INFUSION -> R.string.timer_step_infusion
            StepKind.RINSE -> R.string.timer_step_rinse
            StepKind.WHISK -> R.string.timer_step_whisk
        },
    )

    private fun teaOf(state: TimerUiState.Active): String = state.teaName ?: context.getString(R.string.timer_quick_title)

    /** „14:30": the wall-clock end of a long step in the zone of [now] (TMR-18). */
    private fun timeOfDay(state: TimerUiState.Active, now: ClockReading): String {
        val end = Instant.ofEpochMilli(checkNotNull(state.deadlineWallMs)).atZone(now.zone)
        return TIME_OF_DAY.format(end)
    }

    private fun openTimerIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        TimerScreenIntents.openTimer(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun launchAppIntent(): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            RC_SESSION_SAVED,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class NotificationAction(val label: Int, val event: TimerEvent)

    private companion object {
        const val RC_CONTENT = 301
        const val RC_FULL_SCREEN = 302
        const val RC_DISMISS = 303
        const val RC_SESSION_SAVED = 304
        val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.GERMANY)
    }
}

/** Whether [this] state runs with the foreground service: all but a demoted session and a long running step (D-053). */
internal val TimerUiState.Active.runsWithService: Boolean
    get() = !demoted && !(phase == TimerPhase.RUNNING && longStep)

/** „0:12", „1:00", „1:02:03": a duration in the timer's digits. */
internal fun clock(totalSeconds: Long): String {
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.GERMANY, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.GERMANY, "%d:%02d", minutes, seconds)
    }
}

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

/**
 * The ProgressStyle of the Live Update (04 §8.3): one segment per step (at most 30, PRF-03),
 * its length in tenths of a second of the planned time but at least a twentieth of the plan,
 * so a short step stays visible; a point at the pre-alert of the current step; the progress is
 * the session's elapsed share of the plan on that scale.
 */
internal data class LiveUpdateProgress(val segments: List<Segment>, val preAlertAt: Int?, val progress: Int) {
    data class Segment(val length: Int, val rinse: Boolean)

    companion object {
        private const val MAX_SEGMENTS = 30
        private const val MIN_SHARE = 20
        private const val UNITS_PER_SECOND = 10
        private const val MS_PER_UNIT = 100L

        fun of(state: TimerUiState.Active, nowElapsedMs: Long): LiveUpdateProgress {
            val steps = state.steps.take(MAX_SEGMENTS)
            val planned = steps.map { (it.plannedSeconds * UNITS_PER_SECOND).coerceAtLeast(1) }
            val min = (planned.sum() + MIN_SHARE - 1) / MIN_SHARE
            val lengths = planned.map { it.coerceAtLeast(min) }
            val segments = steps.mapIndexed { index, step -> Segment(lengths[index], step.kind == StepKind.RINSE) }
            val index = state.stepIndex
            val before = lengths.take(index.coerceAtMost(lengths.size)).sum()
            val current = lengths.getOrNull(index)
            val plannedMs = (state.currentStep?.plannedSeconds ?: 0) * MS_PER_UNIT * UNITS_PER_SECOND
            val elapsedMs = when (state.phase) {
                TimerPhase.RUNNING -> plannedMs - (checkNotNull(state.deadlineElapsedMs) - nowElapsedMs)
                TimerPhase.PAUSED -> plannedMs - checkNotNull(state.pausedRemainingMs)
                TimerPhase.ALERTING -> plannedMs
                TimerPhase.READY, TimerPhase.FINISHED -> 0L
            }.coerceIn(0, plannedMs)
            val within = if (current == null || plannedMs == 0L) 0 else (current * elapsedMs / plannedMs).toInt()
            val lead = state.preAlertLeadSeconds?.takeUnless { state.longStep }
            val preAlertAt = if (current != null && lead != null && plannedMs > 0) {
                before + (current * (plannedMs - lead * MS_PER_UNIT * UNITS_PER_SECOND) / plannedMs).toInt()
            } else {
                null
            }
            return LiveUpdateProgress(segments, preAlertAt, before + within)
        }
    }
}
