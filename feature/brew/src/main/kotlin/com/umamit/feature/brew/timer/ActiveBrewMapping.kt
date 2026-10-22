package com.umamit.feature.brew.timer

import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import com.umamit.core.designsystem.component.ActiveBrew
import com.umamit.core.model.TimerPhase
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.feature.brew.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The active brew bar's content for [state] at [nowElapsedMs] (GEN-06, 03 §3.8):
 * the tea („Schnelltimer" for the quick timer), „Aufguss 2/4", the remaining
 * time, and in ALERTING „Jetzt abgießen!" with the overtime (D-055).
 */
fun activeBrewOf(state: TimerUiState.Active, nowElapsedMs: Long, resources: Resources, liquorColor: Color): ActiveBrew {
    val display = state.display(nowElapsedMs)
    val step = state.currentStep
    val kind = step?.let { resources.getString(it.kind.label) }
    return ActiveBrew(
        title = state.teaName ?: resources.getString(R.string.brew_timer_quick_title),
        step = when {
            state.phase == TimerPhase.FINISHED -> resources.getString(R.string.brew_timer_caption_finished)
            kind == null || state.teaName == null -> null
            else -> resources.getString(R.string.brew_timer_step_short, kind, state.stepIndex + 1, state.steps.size)
        },
        remainingSeconds = display.remainingSeconds.toInt(),
        liquorColor = liquorColor,
        stepDescription = kind?.takeIf { state.teaName != null }?.let {
            resources.getString(R.string.brew_timer_step_label, it, state.stepIndex + 1, state.steps.size)
        },
        longStep = state.longStep,
        alerting = state.alerting,
        overtimeSeconds = display.overtimeSeconds.toInt(),
    )
}

/**
 * The clock readings at which [state]'s display changes: now, then at every
 * second boundary of the countdown or the overtime (and the pre-alert), until
 * nothing moves by itself any more. A collector that stops collecting (the
 * app in the background) stops the ticks.
 */
fun TimerUiState.Active.displayTicks(nowElapsedMs: () -> Long): Flow<Long> = flow {
    var now = nowElapsedMs()
    while (true) {
        emit(now)
        val delayMs = nextTickDelayMs(now) ?: break
        delay(delayMs)
        now = nowElapsedMs()
    }
}
