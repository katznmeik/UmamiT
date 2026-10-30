package com.umamit.feature.brew.quick

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.SettingsRepository
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.setup.ActiveSessionQuestion
import com.umamit.feature.brew.steps.PreAlertOptions
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
import java.io.IOException
import javax.inject.Inject

/**
 * What SCR-07 shows before its *Start*.
 *
 * @property durationSeconds the chosen duration (a chip or the picker).
 * @property preAlertChoice the *Vorwarnung* chosen for it (TMR-22, D-097): the last choice, *Automatisch* at first.
 * @property preAlertBaseSeconds the global *Vorwarnung* (SET-10), B of *Automatisch* (04 §5).
 * @property question „Erst die … -Sitzung beenden?" is open (TMR-21).
 * @property alertKey the alert playing now, or null: nothing of this screen may cover it (rule 8).
 */
@Immutable
data class QuickTimerUiState(
    val durationSeconds: Int = QuickDuration.DEFAULT_SECONDS,
    val preAlertChoice: PreAlertChoice = PreAlertChoice.Inherit,
    val preAlertBaseSeconds: Int = PreAlertSettings.DEFAULT_GLOBAL_SECONDS,
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
 * across process death (GEN-07), the *Vorwarnung* choice, remembered in the
 * setting `quickTimerPreAlert` for the next quick timer (D-097), and *Start*
 * through the start seam. While another timer is active, *Start* asks first
 * (TMR-21); while it alerts, the alerting timer opens instead (rule 8, D-062).
 */
@HiltViewModel
class QuickTimerViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val starter: BrewStarter,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val duration = savedState.getStateFlow(DURATION, QuickDuration.DEFAULT_SECONDS)
    private val pending = MutableStateFlow<Pending?>(null)
    private val navigation = Channel<QuickTimerEvent>(Channel.BUFFERED)

    /** The choice made on this screen, ahead of its write to the settings (and kept if that write fails). */
    private val chosen = MutableStateFlow<PreAlertChoice?>(null)

    /** One-shot requests to the host: open the timer. */
    val events: Flow<QuickTimerEvent> = navigation.receiveAsFlow()

    val state: StateFlow<QuickTimerUiState> =
        combine(duration, pending, starter.state, settings.settings, chosen) { d, p, timer, s, c ->
            QuickTimerUiState(
                durationSeconds = d,
                preAlertChoice = c ?: rememberedChoice(s.quickTimerPreAlert),
                preAlertBaseSeconds = s.preAlertSeconds,
                question = p?.question,
                alertKey = timer.alertKey(),
            )
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

    /**
     * A *Vorwarnung* option (TMR-22, D-097): it applies to this quick timer and is remembered for the next one. A
     * write that fails (a full disk) never reaches the app; the choice then still applies, only not remembered.
     */
    fun selectPreAlert(choice: PreAlertChoice) {
        chosen.value = choice
        viewModelScope.launch {
            try {
                settings.update { it.copy(quickTimerPreAlert = choice.explicitSeconds) }
            } catch (_: IOException) {
                // Not remembered for the next quick timer; this one still uses it.
            }
        }
    }

    /** The choice *Start* passes on: the one made here, else the remembered one as shown. */
    private fun preAlertSeconds(): Int? = (chosen.value ?: state.value.preAlertChoice).explicitSeconds

    /** *Start*: the quick timer runs and the timer opens. */
    fun start() {
        // A second tap while the first start is in flight would only be refused and ask (D-065).
        if (starting) return
        starting = true
        val seconds = duration.value
        val preAlert = preAlertSeconds()
        viewModelScope.launch {
            try {
                handle(starter.startQuickTimer(seconds, preAlert))
            } finally {
                starting = false
            }
        }
    }

    private var starting = false

    /** *Beenden & neu starten* (TMR-21). */
    fun confirmRestart() {
        if (pending.value == null) return
        pending.value = null
        val seconds = duration.value
        val preAlert = preAlertSeconds()
        viewModelScope.launch { handle(starter.finishActiveAndStartQuickTimer(seconds, preAlert)) }
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

        /** The stored choice as an option of the selector; a value it never offers reads as *Automatisch*. */
        fun rememberedChoice(seconds: Int?): PreAlertChoice =
            PreAlertOptions.firstOrNull { it.explicitSeconds == seconds } ?: PreAlertChoice.Inherit
    }
}
