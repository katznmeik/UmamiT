package com.umamit.feature.brew.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.SettingsRepository
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The timer screen's view model (SCR-05): the controller's state and user
 * events through [BrewTimer], the monotonic clock the deadlines are on, the
 * screen settings (SET-17, SET-25) and the reliability checks (TMR-31). It
 * touches neither the engine nor the database.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timer: BrewTimer,
    private val clock: MonotonicClock,
    settings: SettingsRepository,
    private val reliability: TimerReliability,
) : ViewModel() {

    /** The controller's state. */
    val state: StateFlow<TimerUiState> get() = timer.state

    val screenSettings: StateFlow<TimerScreenSettings> = settings.settings
        .map { TimerScreenSettings(keepScreenOn = it.keepScreenOn, dimWhileSteeping = it.dimWhileSteeping) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerScreenSettings())

    private val problem = MutableStateFlow(reliability.hasProblem())

    /** A reliability check fails: the screen shows the banner (TMR-31). */
    val reliabilityProblem: StateFlow<Boolean> = problem.asStateFlow()

    /** The monotonic clock (`elapsedRealtime`). */
    fun nowElapsedMs(): Long = clock.elapsedMs()

    /**
     * Sends a user event to the controller. The controller finishes a turn
     * once it has started, so leaving the screen right after a tap never
     * drops an *Abgegossen* (D-056).
     */
    fun send(event: TimerEvent) {
        viewModelScope.launch { timer.send(event) }
    }

    /** Reads the checks again; the screen calls it on every resume (the user may come back from the settings). */
    fun refreshReliability() {
        problem.value = reliability.hasProblem()
    }

    fun fixReliability() = reliability.fix()
}
