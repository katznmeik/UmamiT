package com.umamit.core.timer.notification

import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.SessionKind

/**
 * Which state a timer notification shows, for the dismissal rule of 04 §8.3: a Live Update
 * the user dismissed is not posted again until the next state change. A new phase, step,
 * the pre-alert („Gleich fertig …") and the demotion are state changes; the 15 s refresh of
 * the same state is not.
 */
object TimerNotificationKey {
    fun of(state: TimerUiState.Active): String {
        val session = when (val kind = state.kind) {
            is SessionKind.Brew -> kind.sessionId
            SessionKind.Quick -> "quick"
        }
        return listOf(session, state.stepIndex, state.phase, state.preAlertPlayed, state.demoted).joinToString("|")
    }
}

/**
 * The notification the user dismissed last (04 §8.3), in this process: the delete intent
 * records it, and nothing posts that state again. It dies with the process; recovery then
 * posts the state once more, which is the next state change the user sees.
 */
internal object LiveUpdateDismissal {
    @Volatile
    var dismissedKey: String? = null

    fun isDismissed(state: TimerUiState.Active): Boolean = dismissedKey == TimerNotificationKey.of(state)
}
