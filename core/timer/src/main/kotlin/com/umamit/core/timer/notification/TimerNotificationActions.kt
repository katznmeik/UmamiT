package com.umamit.core.timer.notification

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.R
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.SessionKind
import com.umamit.core.timer.engine.TimerEvent

/**
 * The actions of the timer's notifications (04 §8.2, §10; TMR-14, TMR-41; D-064), the primary
 * one first: *Start*, *Pause* or *Weiter* by phase; *Abgegossen* (*Weggegossen* for a rinse,
 * *Fertig* for the quick timer); *Sitzung beenden* in FINISHED and on a demoted session;
 * *Abbrechen* for a quick timer that is not alerting. Each sends its event through
 * [TimerActionReceiver].
 */
internal class TimerNotificationActions(private val context: Context) {

    fun of(state: TimerUiState.Active): List<NotificationCompat.Action> = actionsOf(state).map(::action)

    private fun actionsOf(state: TimerUiState.Active): List<Action> {
        val primary = when (state.phase) {
            TimerPhase.READY -> Action(R.string.timer_action_start, TimerEvent.Start)
            TimerPhase.RUNNING -> Action(R.string.timer_action_pause, TimerEvent.Pause)
            TimerPhase.PAUSED -> Action(R.string.timer_action_resume, TimerEvent.Resume)
            TimerPhase.ALERTING -> Action(pouredLabel(state), TimerEvent.Poured)
            TimerPhase.FINISHED -> finishAction(state)
        }
        val secondary = when {
            state.kind == SessionKind.Quick && !state.alerting -> Action(
                R.string.timer_action_cancel,
                TimerEvent.Cancel,
            )

            state.demoted && state.phase != TimerPhase.FINISHED -> finishAction(state)

            else -> null
        }
        return listOfNotNull(primary, secondary)
    }

    /** *Sitzung beenden*, unless it would discard the session: only the app may ask first (TMR-12 AC1). */
    private fun finishAction(state: TimerUiState.Active): Action? =
        Action(R.string.timer_action_finish_session, TimerEvent.FinishSession).takeUnless { state.finishWouldDiscard }

    private fun pouredLabel(state: TimerUiState.Active): Int = when {
        state.kind == SessionKind.Quick -> R.string.timer_action_quick_done
        state.currentStep?.kind == StepKind.RINSE -> R.string.timer_action_poured_rinse
        else -> R.string.timer_action_poured
    }

    private fun action(action: Action): NotificationCompat.Action {
        val intent = PendingIntent.getBroadcast(
            context,
            TimerActionReceiver.requestCodeOf(action.event),
            TimerActionReceiver.intent(context, action.event),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(null, context.getString(action.label), intent).build()
    }

    private data class Action(val label: Int, val event: TimerEvent)
}
