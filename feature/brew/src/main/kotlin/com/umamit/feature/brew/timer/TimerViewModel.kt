package com.umamit.feature.brew.timer

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.firstMissingIntents
import com.umamit.core.timer.reliability.hasProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The timer screen's view model (SCR-05): the controller's state and user
 * events through [BrewTimer], the monotonic clock the deadlines are on, the
 * screen settings (SET-17, SET-25), the reliability checks (TMR-31) and the
 * session tea's photo (D-058). It never touches the engine, and reads the
 * database only for that photo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timer: BrewTimer,
    private val clock: MonotonicClock,
    settings: SettingsRepository,
    private val reliability: TimerReliability,
    sessions: SessionRepository,
    teas: TeaRepository,
    photos: PhotoFiles,
) : ViewModel() {

    /** The controller's state. */
    val state: StateFlow<TimerUiState> get() = timer.state

    /**
     * What the brew's seal shows (SCR-05, D-095): the session tea's style and the file of
     * its cover photo; null for the quick timer (no seal) or before the session is read.
     * The screen shows the type-coloured leaf until the photo is read, or without one.
     */
    val teaSeal: StateFlow<TimerTeaSeal?> = timer.state
        .map { (it as? TimerUiState.Active)?.sessionId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            val snapshot = id?.let { sessions.getSession(it)?.snapshot }
            val teaId = snapshot?.teaId
            when {
                snapshot == null -> flowOf(null)

                teaId == null -> flowOf(TimerTeaSeal(snapshot.styleKey, photoPath = null))

                else -> teas.observeTea(teaId).map { tea ->
                    val path = tea?.coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path
                    TimerTeaSeal(snapshot.styleKey, path)
                }
            }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The controller has recovered: an `Idle` [state] now means no session (GEN-07, D-068). */
    val recovered: StateFlow<Boolean> get() = timer.recovered

    val screenSettings: StateFlow<TimerScreenSettings> = settings.settings
        .map { TimerScreenSettings(keepScreenOn = it.keepScreenOn, dimWhileSteeping = it.dimWhileSteeping) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerScreenSettings())

    private val problem = MutableStateFlow(reliability.hasProblem())

    /** A reliability check fails: the screen shows the banner (TMR-31). */
    val reliabilityProblem: StateFlow<Boolean> = problem.asStateFlow()

    /** The monotonic clock (`elapsedRealtime`). */
    fun nowElapsedMs(): Long = clock.elapsedMs()

    /**
     * Sends a user event of [slot]'s timer, the one the screen showed when it was tapped (04 §3.3, D-098), to
     * the controller. The controller finishes a turn once it has started, so leaving the screen right after a
     * tap never drops an *Abgegossen* (D-056).
     */
    fun send(slot: TimerSlot, event: TimerEvent) {
        viewModelScope.launch { timer.send(slot, event) }
    }

    /** Reads the checks again; the screen calls it on every resume (the user may come back from the settings). */
    fun refreshReliability() {
        problem.value = reliability.hasProblem()
    }

    /** The banner's *Beheben*: the pages of the first missing check, for the screen to start. */
    fun fixReliability(): List<Intent> = reliability.firstMissingIntents()
}
