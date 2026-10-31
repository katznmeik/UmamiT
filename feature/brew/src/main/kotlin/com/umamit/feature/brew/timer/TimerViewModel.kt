package com.umamit.feature.brew.timer

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.reliability.ReliabilityProblem
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.firstMissingIntents
import com.umamit.core.timer.reliability.problem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The timer screen's view model (SCR-05): the controller's state and user
 * events through [BrewTimer], the monotonic clock the deadlines are on, the
 * screen settings (SET-17, SET-25), the reliability checks (TMR-31) and the
 * session tea's photo (D-058) and stock (BRW-11, D-098). It never touches the
 * engine, and reads the database only for that photo and that stock, beside the
 * controller's state: nothing on the timer's path waits for them (rule 8).
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
    stock: StockRepository,
) : ViewModel() {

    /** The controller's state: the timer in front of the screens (04 §3.3). */
    val state: StateFlow<TimerUiState> get() = timer.state

    /** [slot]'s timer, Idle without one (D-098): a route shows one timer and the other as its row. */
    fun state(slot: TimerSlot): StateFlow<TimerUiState> = timer.state(slot)

    /**
     * What the brew's seal shows (SCR-05, D-095): the session tea's style and the file of
     * its cover photo; null without a brew session (the quick timer has no seal) or before the session is read.
     * It follows the brew's slot, so it is there while the quick timer is in front of the controller (D-098).
     * The screen shows the type-coloured leaf until the photo is read, or without one.
     */
    val teaSeal: StateFlow<TimerTeaSeal?> = timer.state(TimerSlot.BREW)
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

    /**
     * The session tea's stock for READY's line (BRW-11, D-098): „38 g übrig", „14 g übrig · knapp" or „Leer",
     * live as the stock changes. Read here from the inventory, never through the engine or the controller, so
     * nothing on the alarm path waits for it. Null for the quick timer, a session whose tea is not in the
     * collection (none, deleted or removed), before it is read and after a failed read (logged).
     */
    val teaStock: StateFlow<TimerStock?> = timer.state(TimerSlot.BREW)
        .map { (it as? TimerUiState.Active)?.sessionId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                flow {
                    val teaId = sessions.getSession(id)?.snapshot?.teaId
                    emitAll(if (teaId == null) flowOf(null) else stockOf(teaId, teas, stock, settings))
                }.catch { e ->
                    // This session shows no line; the next one reads again (BRW-11).
                    Log.w(TAG, "Stock of the session's tea not read", e)
                    emit(null)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The controller has recovered: an `Idle` [state] now means no session (GEN-07, D-068). */
    val recovered: StateFlow<Boolean> get() = timer.recovered

    val screenSettings: StateFlow<TimerScreenSettings> = settings.settings
        .map { TimerScreenSettings(keepScreenOn = it.keepScreenOn, dimWhileSteeping = it.dimWhileSteeping) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerScreenSettings())

    private val problem = MutableStateFlow(reliability.problem())

    /** A reliability check fails: the screen shows the banner with this message (TMR-31); null: none. */
    val reliabilityProblem: StateFlow<ReliabilityProblem?> = problem.asStateFlow()

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
        problem.value = reliability.problem()
    }

    /** The banner's *Beheben*: the pages of the first missing check, for the screen to start. */
    fun fixReliability(): List<Intent> = reliability.firstMissingIntents()
}

/**
 * [teaId]'s stock with its INV-05 status by the tea's own „Knapp ab", else SET-16's, as the setup shows it
 * (BRW-08, D-097); null while the tea is not in the collection (deleted, or removed: archived).
 */
private fun stockOf(
    teaId: String,
    teas: TeaRepository,
    stock: StockRepository,
    settings: SettingsRepository,
): Flow<TimerStock?> = combine(
    teas.observeTea(teaId).map { overview -> overview?.tea?.takeUnless { it.isArchived } },
    stock.observeStock(teaId).map { it.remainingGrams },
    settings.settings.map { it.lowStockGrams }.distinctUntilChanged(),
) { tea, grams, global ->
    tea?.let { TimerStock(grams, StockLevel.of(grams, it.lowStockGrams ?: global)) }
}

private const val TAG = "TimerViewModel"
