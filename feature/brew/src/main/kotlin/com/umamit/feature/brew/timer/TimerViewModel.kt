package com.umamit.feature.brew.timer

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TastingWrite
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.StepTasting
import com.umamit.core.model.StockLevel
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TastingLimits
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.reliability.ReliabilityProblem
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.firstMissingIntents
import com.umamit.core.timer.reliability.problem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The timer screen's view model (SCR-05): the controller's state and user
 * events through [BrewTimer], the monotonic clock the deadlines are on, the
 * screen settings (SET-17, SET-25), the reliability checks (TMR-31) and the
 * session tea's photo (D-058) and stock (BRW-11, D-098), and the taste of the
 * infusion ended last (JRN-03, JRN-05, D-114). It never touches the engine, and
 * reads the database only for that photo, that stock and that taste, beside the
 * controller's state: nothing on the timer's path waits for them (rule 8). The
 * taste writes go through [SessionTasting] only, never as an engine event.
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
    journal: JournalRepository,
    tasting: SessionTasting,
) : ViewModel() {

    /** The controller's state: the timer in front of the screens (04 §3.3). */
    val state: StateFlow<TimerUiState> get() = timer.state

    /** [slot]'s timer, Idle without one (D-098): a route shows one timer and the other as its row. */
    fun state(slot: TimerSlot): StateFlow<TimerUiState> = timer.state(slot)

    /**
     * What [slot]'s seal shows (SCR-05, D-095): its session tea's style and the file of
     * its cover photo; null without a brew session (the quick timer has no seal) or before the session is read.
     * It follows that brew slot, so it is there while another timer is in front of the controller (D-098), and each
     * of two brews has its own (D-108). The screen shows the type-coloured leaf until the photo is read, or without
     * one.
     */
    fun teaSeal(slot: TimerSlot): StateFlow<TimerTeaSeal?> = seals[slot] ?: NO_SEAL

    /**
     * [slot]'s session tea's stock for READY's line (BRW-11, D-098): „38 g übrig", „14 g übrig · knapp" or „Leer",
     * live as the stock changes; each of two brews has its own (D-108). Read here from the inventory, never through
     * the engine or the controller, so nothing on the alarm path waits for it. Null for the quick timer, a session
     * whose tea is not in the collection (none, deleted or removed), before it is read and after a failed read
     * (logged).
     */
    fun teaStock(slot: TimerSlot): StateFlow<TimerStock?> = stocks[slot] ?: NO_STOCK

    /**
     * The taste and note stored per step of [slot]'s session (JRN-03, JRN-05, D-114), for the timer's
     * „Geschmack · N. Aufguss" button („✓" with data) and its sheet; each of two brews has its own. Read from the
     * journal beside the timer, never through the engine or the controller: only the step rows' taste columns, and
     * only while a screen collects it (the slot the route shows), so a taste write re-reads no whole session and not
     * the other slot's. Null for the quick timer, before the session is read and after a failed read (logged): the
     * sheet's editor then waits, so its note field never starts empty over a stored note.
     */
    fun stepTastings(slot: TimerSlot): StateFlow<TimerSessionTastings?> = tastings[slot] ?: NO_TASTINGS

    // Read from the start for both brew slots, so a screen opened on either has them as soon as they are read.
    private val seals = BREW_SLOTS.associateWith { slot -> slotSeal(slot, sessions, teas, photos) }
    private val stocks = BREW_SLOTS.associateWith { slot -> slotStock(slot, sessions, teas, stock, settings) }

    // Read only while the route shows the slot (MIN-3): each taste write invalidates the step table.
    private val tastings = BREW_SLOTS.associateWith { slot ->
        slotTastings(timer.state(slot), journal).stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)
    }

    /** The taste sheet's writes (JRN-03, JRN-05, D-114), beside the timer: never an engine event. */
    val tasteWrites = TimerTasteWrites(tasting, viewModelScope)

    private fun slotSeal(
        slot: TimerSlot,
        sessions: SessionRepository,
        teas: TeaRepository,
        photos: PhotoFiles,
    ): StateFlow<TimerTeaSeal?> = timer.state(slot)
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

    private fun slotStock(
        slot: TimerSlot,
        sessions: SessionRepository,
        teas: TeaRepository,
        stock: StockRepository,
        settings: SettingsRepository,
    ): StateFlow<TimerStock?> = timer.state(slot)
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

private const val TAG = "UmamiTTimerScreen"

private val BREW_SLOTS = listOf(TimerSlot.BREW, TimerSlot.BREW_2)
private val NO_SEAL: StateFlow<TimerTeaSeal?> = MutableStateFlow<TimerTeaSeal?>(null).asStateFlow()
private val NO_STOCK: StateFlow<TimerStock?> = MutableStateFlow<TimerStock?>(null).asStateFlow()
private val NO_TASTINGS: StateFlow<TimerSessionTastings?> = MutableStateFlow<TimerSessionTastings?>(null).asStateFlow()

/** The taste and note stored per step `position` of the session [sessionId]; steps without any are absent (D-114). */
data class TimerSessionTastings(val sessionId: String, val byPosition: Map<Int, StepTasting>)

/**
 * The taste and note per step of the session in [state]'s slot, as the journal reads them (D-114): the step rows'
 * taste columns only. Null without a session and after a failed read (logged), until the slot's next session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun slotTastings(state: Flow<TimerUiState>, journal: JournalRepository): Flow<TimerSessionTastings?> = state
    .map { (it as? TimerUiState.Active)?.sessionId }
    .distinctUntilChanged()
    .flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            journal.observeStepTastings(id)
                .map<Map<Int, StepTasting>, TimerSessionTastings?> { TimerSessionTastings(id, it) }
                .catch { e ->
                    // Not an empty map: the editor would then start with an empty note over a stored one.
                    Log.w(TAG, "Taste of the session not read", e)
                    emit(null)
                }
        }
    }
    .distinctUntilChanged()

/**
 * The timer's taste writes (JRN-03, JRN-05, D-114) through [SessionTasting], addressed by session and step position.
 * They run one after another in the order given, so the last one is the one stored, and each completes even when the
 * sheet closes on an alert, the screen is left or the view model is cleared meanwhile (rule 8); none waits on, or for,
 * the timer. A refused or failed write changes nothing: it is logged and counted in [refusals], so the sheet shows
 * the stored values again (no message, SCR-05).
 */
class TimerTasteWrites(private val tasting: SessionTasting, private val scope: CoroutineScope) {
    private val lock = Mutex()
    private val refused = MutableStateFlow(0)

    /** How many writes were refused or failed so far; the sheet starts again from the stored values on a change. */
    val refusals: StateFlow<Int> = refused.asStateFlow()

    /** The sheet's slider for [axis] of the infusion at [position] of [sessionId] released on [value]. */
    fun setTaste(sessionId: String, position: Int, axis: TasteAxis, value: Int) {
        write { tasting.setInfusionTaste(sessionId, position, axis, value) }
    }

    /** The sheet's *Zurücksetzen*: the infusion's six values unset; its note stays (D-114). */
    fun clearTaste(sessionId: String, position: Int) {
        write { tasting.clearInfusionTaste(sessionId, position) }
    }

    /** The infusion note as the shared editor hands it over (1 s after the last key, or when the sheet closes). */
    fun setNote(sessionId: String, position: Int, note: String) {
        val text = note.take(TastingLimits.MAX_INFUSION_NOTE_LENGTH)
        write { tasting.setInfusionNote(sessionId, position, text) }
    }

    private fun write(block: suspend () -> TastingWrite) {
        scope.launch(start = CoroutineStart.ATOMIC) {
            withContext(NonCancellable) {
                lock.withLock {
                    val result = try {
                        block()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        Log.w(TAG, "Taste write failed", e)
                        null
                    }
                    if (result != TastingWrite.SAVED) {
                        if (result != null) Log.w(TAG, "Taste write refused: $result")
                        refused.update { it + 1 }
                    }
                }
            }
        }
    }
}
