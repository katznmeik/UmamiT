package com.umamit.core.timer.notification

import android.content.Context
import com.umamit.core.common.ClockReading
import com.umamit.core.model.StepKind
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.R
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.SessionKind
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The German texts of the timer's notifications per phase (04 §8.2, §8.3; D-064): the step
 * word follows the step kind as on SCR-05 (D-058), the quick timer is „Schnelltimer".
 */
internal class TimerNotificationTexts(private val context: Context) {

    /** „Gyokuro · Aufguss 2 von 3", „Gyokuro · Alle Aufgüsse fertig" or „Schnelltimer". */
    fun title(state: TimerUiState.Active): String {
        val step = state.currentStep
        return when {
            state.kind == SessionKind.Quick -> context.getString(R.string.timer_quick_title)

            step == null -> context.getString(R.string.timer_title_finished, teaOf(state))

            else -> context.getString(
                R.string.timer_title_step,
                teaOf(state),
                stepWord(step.kind),
                state.stepIndex + 1,
                state.steps.size,
            )
        }
    }

    /** The text of a phase other than ALERTING at [now]. */
    fun text(state: TimerUiState.Active, now: ClockReading): String {
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

    /** The alert's text: „Gyokuro · Aufguss 2", or „Schnelltimer". */
    fun alertText(state: TimerUiState.Active): String {
        val step = state.currentStep
        return if (state.kind == SessionKind.Quick || step == null) {
            context.getString(R.string.timer_quick_title)
        } else {
            context.getString(R.string.timer_alert_text, teaOf(state), stepWord(step.kind), state.stepIndex + 1)
        }
    }

    /** The short critical text of the chip outside RUNNING (04 §8.3): „60 °C", „Pause", „Fertig". */
    fun chip(state: TimerUiState.Active): String = when (state.phase) {
        TimerPhase.PAUSED -> context.getString(R.string.timer_chip_paused)

        TimerPhase.FINISHED -> context.getString(R.string.timer_chip_finished)

        else -> state.kettleTempC?.let { context.getString(R.string.timer_chip_temp, it) }
            ?: context.getString(R.string.timer_chip_ready)
    }

    private fun stepWord(kind: StepKind): String = context.getString(
        when (kind) {
            StepKind.INFUSION -> R.string.timer_step_infusion
            StepKind.RINSE -> R.string.timer_step_rinse
            StepKind.WHISK -> R.string.timer_step_whisk
        },
    )

    /** The tea of [state], or „Schnelltimer". */
    fun teaOf(state: TimerUiState.Active): String =
        state.teaName ?: context.getString(R.string.timer_quick_title)

    /** „14:30": the wall-clock end of a long step in the zone of [now] (TMR-18). */
    private fun timeOfDay(state: TimerUiState.Active, now: ClockReading): String {
        val end = Instant.ofEpochMilli(checkNotNull(state.deadlineWallMs)).atZone(now.zone)
        return TIME_OF_DAY.format(end)
    }

    private companion object {
        val TIME_OF_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.GERMANY)
        const val SECONDS_PER_MINUTE = 60L
        const val SECONDS_PER_HOUR = 3_600L

        /** „0:12", „1:00", „1:02:03": a duration in the timer's digits. */
        fun clock(totalSeconds: Long): String {
            val hours = totalSeconds / SECONDS_PER_HOUR
            val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
            val seconds = totalSeconds % SECONDS_PER_MINUTE
            return if (hours > 0) {
                String.format(Locale.GERMANY, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.GERMANY, "%d:%02d", minutes, seconds)
            }
        }
    }
}
