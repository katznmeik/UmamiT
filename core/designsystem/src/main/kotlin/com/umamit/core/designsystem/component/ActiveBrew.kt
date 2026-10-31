package com.umamit.core.designsystem.component

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * What the [ActiveBrewBar] shows about the running session or quick timer. The
 * step's progress is passed separately, as a lambda read only while drawing.
 *
 * @param title the tea name, or „Schnelltimer".
 * @param step „Aufguss 2/4"; `null` for a quick timer.
 * @param stepDescription the step for TalkBack („Aufguss 2 von 4"); defaults to [step].
 * @param longStep the step is ≥ 10 min: the source updates the progress only about every
 *   `UmamiMotion.longStepUpdateIntervalMillis`, and the mini ring glides there (03 §3.6).
 * @param alerting the step has ended and waits for *Abgegossen* (ALERTING): the bar turns
 *   static matcha with „Jetzt abgießen!" and the [overtimeSeconds] counting up (03 §3.8).
 * @param overtimeSeconds time since the step ended; shown only while [alerting].
 * @param held the quick timer's alert waits for the brew's (D-098): the half reads „fertig" without the alert
 *   style until it rings.
 */
@Immutable
data class ActiveBrew(
    val title: String,
    val step: String?,
    val remainingSeconds: Int,
    val liquorColor: Color,
    val stepDescription: String? = step,
    val longStep: Boolean = false,
    val alerting: Boolean = false,
    val overtimeSeconds: Int = 0,
    val held: Boolean = false,
)
