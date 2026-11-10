package com.umamit.feature.home

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TransitionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Start holds still while *Nochmal brühen* opens the timer (SCR-02, D-111): from just before an accepted start until
 * Start has left the screen, Start keeps its card and *Aufguss starten* from before the tap, and the shell's active
 * brew bar does not grow in. Presentation only: the start, the timer, an alert and the question are never delayed
 * (rule 8). A start that is not accepted releases it at once, a new alert too, and it ends by itself after about a
 * second if the timer never covers Start.
 */
class StartHandOff internal constructor(private val scope: CoroutineScope) {
    private val held = MutableStateFlow<HeldStart?>(null)
    private val holding = mutableStateOf(false)
    private var timeout: Job? = null

    /**
     * Start hands off to the timer now. Snapshot state, written on the tap's thread before the start, so the shell's
     * bar reads it in the same frame as the new brew.
     */
    val handingOff: State<Boolean> get() = holding

    /** Every change of the hold, for Start's state to apply it again. */
    internal val changes: Flow<Any?> get() = held

    /**
     * [home] with the hold as it is now, not as an emission saw it: it is set before the start, so no state from the
     * start on shows the new card while it lasts. Only the card and *Aufguss starten*; the rest stays live.
     */
    internal fun applyTo(home: HomeUiState): HomeUiState =
        held.value?.let { home.copy(active = it.active, twoTimersActive = it.twoTimersActive) } ?: home

    /**
     * Runs [start] with Start held at [shown] (a hold still on keeps what Start showed before it); [alertKey] is the
     * alert playing at the tap. Not accepted, it ends at once.
     */
    internal suspend fun holdingStill(
        shown: HomeUiState,
        alertKey: String?,
        start: suspend () -> TransitionOutcome,
    ): TransitionOutcome {
        held.update { it ?: HeldStart(shown.active, shown.twoTimersActive, alertKey) }
        holding.value = true
        timeout?.cancel()
        // The timer never opened: Start shows itself as it is.
        timeout = scope.launch {
            delay(TIMEOUT_MS)
            release()
        }
        var outcome: TransitionOutcome = TransitionOutcome.Ignored
        try {
            outcome = start()
        } finally {
            // Refused, ignored, a question or a failure: nothing to hold.
            if (outcome != TransitionOutcome.Accepted) release()
        }
        return outcome
    }

    /** A new alert is never held back (rule 8). */
    internal fun alertChanged(alertKey: String?) {
        if (alertKey != null && held.value?.let { alertKey != it.alertKey } == true) release()
    }

    /**
     * Start has left the screen (the timer covers it, or another tab shows): the hold ends, so the way back shows
     * the card and the bar already laid out.
     */
    fun startLeft() = release()

    private fun release() {
        timeout?.cancel()
        timeout = null
        held.value = null
        holding.value = false
    }

    /** What Start showed before the tap, and the alert playing then. */
    private class HeldStart(val active: TimerUiState.Active?, val twoTimersActive: Boolean, val alertKey: String?)

    private companion object {
        /** The hold ends by itself if the timer never covers Start. */
        const val TIMEOUT_MS = 1_000L
    }
}
