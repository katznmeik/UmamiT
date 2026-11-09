package com.umamit.feature.brew.setup

import androidx.compose.runtime.Composable
import com.umamit.core.designsystem.component.ChooseTimerToEndDialog
import com.umamit.core.designsystem.component.TimerChoice
import com.umamit.core.designsystem.component.FinishActiveSessionDialog
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.StartRefusal
import com.umamit.core.timer.controller.TimerToEnd

/** What a refused start asks before it starts (TMR-21, TMR-22, D-108). */
sealed interface StartQuestion {
    /** „Schnelltimer beenden und neu starten?" (D-098): a second quick timer; the quick timer ends. */
    data object RestartQuick : StartQuestion

    /** „Welchen Timer beenden?" (D-108): two timers are active; one answer per timer, in the slot order. */
    data class ChooseTimer(val timers: List<TimerToEnd>) : StartQuestion
}

/** The question of [refusal], or null where it asks none (a tea's own session opens instead). */
internal fun StartRefusal.question(): StartQuestion? = when (this) {
    is StartRefusal.ChooseTimer -> StartQuestion.ChooseTimer(timers)
    StartRefusal.RestartQuick -> StartQuestion.RestartQuick
    is StartRefusal.OpenSession -> null
}

/**
 * The dialog of [question]: [onEnd] with the slot whose timer ends (the quick slot for *Beenden & neu starten*), then
 * the start goes on; [onCancel] keeps every timer. Hosts show it only while no alert plays (rule 8).
 */
@Composable
internal fun StartQuestionDialog(question: StartQuestion, onEnd: (TimerSlot) -> Unit, onCancel: () -> Unit) {
    when (question) {
        StartQuestion.RestartQuick -> FinishActiveSessionDialog(
            teaName = null,
            onRestart = { onEnd(TimerSlot.QUICK) },
            onCancel = onCancel,
        )

        is StartQuestion.ChooseTimer -> ChooseTimerToEndDialog(
            timers = question.timers.map { TimerChoice(it.teaName, it.quick) },
            onEnd = { index -> onEnd(question.timers[index].slot) },
            onCancel = onCancel,
        )
    }
}
