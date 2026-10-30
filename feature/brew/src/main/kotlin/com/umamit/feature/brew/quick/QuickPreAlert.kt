package com.umamit.feature.brew.quick

import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.feature.brew.steps.StepPreAlert
import com.umamit.feature.brew.steps.stepPreAlertOf

/*
 * SCR-07's *Vorwarnung* before *Start* (TMR-22, D-097): the selector's label and the ring's mark, from the engine's
 * rules for the quick timer, so the screen never re-implements them and shows what *Start* will run.
 */

/** The lead the quick timer will get with [choice] (04 §5): null without a pre-alert. */
internal fun QuickTimerUiState.preAlertLeadSeconds(choice: PreAlertChoice = preAlertChoice): Int? =
    BrewTimerEngine.quickTimerPreAlertLeadSeconds(durationSeconds, preAlertBaseSeconds, choice.explicitSeconds)

/** What the selector shows for [choice] (its own by default): „Auto · 10 s", „Aus", „20 s", „20 s · zu kurz" … */
internal fun QuickTimerUiState.preAlertShown(choice: PreAlertChoice = preAlertChoice): StepPreAlert = stepPreAlertOf(
    choice = choice,
    leadSeconds = preAlertLeadSeconds(choice),
    longStep = BrewTimerEngine.isLongStep(PlannedStep(StepKind.INFUSION, durationSeconds, tempC = null), false),
)

/**
 * The pre-alert mark on the ring before *Start*, as a fraction of the duration: where the running quick timer
 * shows it (`TimerUiState.Active.preAlertPosition`), so it does not move at *Start*. Null without a pre-alert.
 */
internal fun QuickTimerUiState.preAlertPosition(): Float? =
    preAlertLeadSeconds()?.let { 1f - it.toFloat() / durationSeconds }
