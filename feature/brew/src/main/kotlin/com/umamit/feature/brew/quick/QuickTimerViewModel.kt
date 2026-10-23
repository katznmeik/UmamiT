package com.umamit.feature.brew.quick

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.setup.ActiveSessionQuestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What SCR-07 shows before its *Start*.
 *
 * @property durationSeconds the chosen duration (a chip or the picker).
 * @property question „Erst die … -Sitzung beenden?" is open (TMR-21).
 * @property alertKey the alert playing now, or null: nothing of this screen may cover it (rule 8).
 */
@Immutable
data class QuickTimerUiState(
    val durationSeconds: Int = QuickDuration.DEFAULT_SECONDS,
    val question: ActiveSessionQuestion? = null,
    val alertKey: String? = null,
)

/** One-shot requests of SCR-07 to its host. */
enum class QuickTimerEvent {
    /** The quick timer runs, or another timer alerts: the timer (SCR-05) takes over. */
    OpenTimer,
}

/**
 * The quick timer's view model (SCR-07, TMR-22): the chosen duration, kept
 * across process death (GEN-07), and *Start* through the start seam. While
 * another timer is active, *Start* asks first (TMR-21); while it alerts, the
 * alerting timer opens instead (rule 8, D-062).
 */
@HiltViewModel
class QuickTimerViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val starter: BrewStarter,
) : ViewModel() {

    private val duration = savedState.getStateFlow(DURATION, QuickDuration.DEFAULT_SECONDS)
    private val pending = MutableStateFlow<Pending?>(null)
    private val navigation = Channel<QuickTimerEvent>(Channel.BUFFERED)

    /** One-shot requests to the host: open the timer. */
    val events: Flow<QuickTimerEvent> = navigation.receiveAsFlow()

    val state: StateFlow<QuickTimerUiState> = combine(duration, pending, starter.state) { d, p, timer ->
        QuickTimerUiState(durationSeconds = d, question = p?.question, alertKey = timer.alertKey())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, QuickTimerUiState(duration.value))

    init {
        // Alerts first (rule 8): a new alert closes the question.
        viewModelScope.launch {
            starter.state.collect { timer ->
                val alert = timer.alertKey()
                if (alert != null) pending.update { it?.takeIf { p -> p.openedIn == alert } }
            }
        }
    }

    /** A chip, or *Übernehmen* in the duration picker. */
    fun select(seconds: Int) {
        savedState[DURATION] = seconds.coerceIn(QuickDuration.MIN_SECONDS, QuickDuration.MAX_SECONDS)
    }

    /** *Start*: the quick timer runs and the timer opens. */
    fun start() {
        val seconds = duration.value
        viewModelScope.launch { handle(starter.startQuickTimer(seconds)) }
    }

    /** *Beenden & neu starten* (TMR-21). */
    fun confirmRestart() {
        if (pending.value == null) return
        pending.value = null
        val seconds = duration.value
        viewModelScope.launch { handle(starter.finishActiveAndStartQuickTimer(seconds)) }
    }

    /** *Abbrechen*: the active timer stays. */
    fun dismissQuestion() {
        pending.value = null
    }

    private suspend fun handle(outcome: TransitionOutcome) {
        when (outcome) {
            TransitionOutcome.Accepted -> navigation.send(QuickTimerEvent.OpenTimer)

            is TransitionOutcome.Rejected -> if (outcome.reason is RejectionReason.ActiveTimerExists) {
                val timer = starter.state.value
                if (timer.alertKey() != null) {
                    // No question over an alert (rule 8): the alerting timer is where to go now.
                    navigation.send(QuickTimerEvent.OpenTimer)
                } else {
                    val active = timer as? TimerUiState.Active
                    pending.value = Pending(ActiveSessionQuestion(active?.teaName), openedIn = null)
                }
            }

            TransitionOutcome.Ignored -> Unit
        }
    }

    private data class Pending(val question: ActiveSessionQuestion, val openedIn: String?)

    private companion object {
        const val DURATION = "quick_duration_seconds"
    }
}
