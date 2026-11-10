package com.umamit.feature.home

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.LowStockRepository
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.BrewSession
import com.umamit.core.model.LowStockCard
import com.umamit.core.model.Photo
import com.umamit.core.model.StepKind
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.TimerSlot
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.StartRefusal
import com.umamit.core.timer.controller.TimerToEnd
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.activeSessionIds
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.controller.brewAgainStart
import com.umamit.core.timer.controller.refusalOf
import com.umamit.core.timer.controller.startedSlot
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.timer.reliability.ReliabilityProblem
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.firstMissingIntents
import com.umamit.core.timer.reliability.problem
import com.umamit.core.ui.alertCovers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** A tea of *Favoriten*, with its cover photo file (null: the leaf placeholder). */
data class HomeTea(val id: String, val name: String, val photoPath: String?)

/**
 * A row of *Zuletzt gebrüht* (SCR-02, BRW-11, D-110): a tea of the collection with its last session, which a
 * tap brews again. The `TeaListItem` meta line „8. Juni · 1 Min. · 65 °C" comes from that session's start, its
 * first brewed step's planned duration and its temperature.
 *
 * @property teaId the tea to brew again.
 * @property sessionId the tea's last session.
 */
data class RecentBrew(
    val teaId: String,
    val sessionId: String,
    val teaName: String,
    val photoPath: String?,
    val startedAtMs: Long,
    val zoneId: String,
    val firstStepSeconds: Int?,
    val tempC: Int?,
    val infusions: Int,
)

/** „Welchen Timer beenden?" (TMR-21, D-108): one answer per active timer, in the slot order. */
data class HomeQuestion(val timers: List<TimerToEnd>)

/**
 * What Start (SCR-02) shows.
 *
 * @property active the active brew session: *Aufguss fortsetzen* with a live countdown in place of *Aufguss starten*
 *   (GEN-06). Never the quick timer (SCR-02, D-098): alone it leaves *Aufguss starten*, beside a brew the card stays
 *   the brew's; the active brew bar shows it. With two brew sessions the first in the slot order (`BREW`, then
 *   `BREW_2`; D-108); the bar shows both.
 * @property twoTimersActive two timers are active, the most there may be (D-108): *Aufguss starten* is hidden, and a
 *   start from *Zuletzt gebrüht* or the setup asks „Welchen Timer beenden?". With fewer it shows, also below the
 *   card of one active brew (v0.5.0; v0.3.0 hid it while a brew was active).
 * @property recent *Zuletzt gebrüht*: the six most recently brewed distinct teas of the collection, each with its
 *   last session (BRW-11, D-110; until v0.5.0 three tiles *Nochmal brühen* and the last three sessions apart).
 * @property collectionEmpty no tea to brew yet: the empty state (GEN-09).
 * @property alertKey the alert playing now, or null: nothing may cover it (rule 8).
 * @property reliabilityProblem a reliability check is missing: the banner with its message (TMR-31); null: none.
 * @property lowStock the nudge slot's low-stock card (INV-10), or null.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val collectionEmpty: Boolean = false,
    val active: TimerUiState.Active? = null,
    val twoTimersActive: Boolean = false,
    val recent: List<RecentBrew> = emptyList(),
    val favorites: List<HomeTea> = emptyList(),
    val question: HomeQuestion? = null,
    val alertKey: String? = null,
    val reliabilityProblem: ReliabilityProblem? = null,
    val lowStock: LowStockCard? = null,
)

/** What Start asks its host to do once. */
sealed interface HomeEvent {
    /** Open the timer of [slot] (the brew just started, or the tea's active session); null: the one in front. */
    data class OpenTimer(val slot: TimerSlot?) : HomeEvent
}

/**
 * Start's view model (SCR-02): the collection, the recent sessions and the
 * timer's state. *Nochmal brühen* (a tap on a tea of *Zuletzt gebrüht*) starts the tea's last session again through
 * the [BrewStarter] (BRW-11), beside one other active timer (D-108); a tea with an active session opens it, and
 * while two timers are active it asks „Welchen Timer beenden?" (TMR-21).
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    teas: TeaRepository,
    private val sessions: SessionRepository,
    private val starter: BrewStarter,
    private val clock: MonotonicClock,
    private val photos: PhotoFiles,
    private val reliability: TimerReliability,
    private val lowStock: LowStockRepository,
) : ViewModel() {
    private val pending = MutableStateFlow<Pending?>(null)
    private val reliabilityProblem = MutableStateFlow(reliability.problem())
    private val navigation = Channel<HomeEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<HomeEvent> = navigation.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        teas.observeTeas(),
        sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW),
        // The card is the brew's (SCR-02, D-098): a quick timer alone leaves *Aufguss starten*, the bar shows it.
        combine(
            starter.state,
            starter.observeActive(TimerSlot.BREW),
            starter.observeActive(TimerSlot.BREW_2),
            starter.observeActive(TimerSlot.QUICK),
        ) { front, brew, brew2, quick ->
            Timers(front, brew ?: brew2, active = listOfNotNull(brew, brew2, quick).size)
        },
        pending,
        reliabilityProblem,
    ) { overviews, recentSessions, timers, question, problem ->
        val owned = overviews.filter { !it.tea.isArchived }
        val byId = owned.associateBy { it.tea.id }
        HomeUiState(
            loading = false,
            collectionEmpty = owned.isEmpty(),
            active = timers.brew,
            twoTimersActive = timers.active >= MAX_ACTIVE_TIMERS,
            // Newest first, so a tea's first session here is its last brew; a removed tea is left out (BRW-11).
            recent = recentSessions.distinctBy { it.snapshot.teaId }
                .mapNotNull { session -> session.snapshot.teaId?.let(byId::get)?.let { session.toRecent(it) } }
                .take(SHOWN),
            favorites = owned.filter {
                it.tea.isFavorite
            }.map { HomeTea(it.tea.id, it.tea.name, it.coverPhoto?.path()) },
            question = question?.question,
            // Either timer's alert: no question over it (rule 8).
            alertKey = timers.front.alertKey(),
            reliabilityProblem = problem,
        )
    }.combine(
        // The card never holds back or breaks Start's state, which carries the alert (rule 8): none until
        // the stock is read, and none after a read error.
        lowStock.observeCard().onStart { emit(null) }.catch { emit(null) },
    ) { home, card ->
        // A plain card of the list (rule 8: never over an alert); only with teas to remind of.
        home.copy(lowStock = card.takeUnless { home.collectionEmpty })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        // Alerts first (rule 8): a new alert closes the question.
        viewModelScope.launch {
            starter.state.collect { timer ->
                val alert = timer.alertKey()
                pending.update { it?.takeUnless { p -> alertCovers(alert, p.openedIn) } }
            }
        }
    }

    /** Reads the reliability checks again; Start calls it on every resume (TMR-31). */
    fun refreshReliability() {
        reliabilityProblem.value = reliability.problem()
    }

    /** The banner's *Beheben*: the pages of the first missing check, for Start to start. */
    fun fixReliability(): List<Intent> = reliability.firstMissingIntents()

    /**
     * *Ausblenden* on the low-stock card: hidden until the tea's next purchase (INV-10 AC2). A failed write
     * never crashes the app (a crash would take a running timer's process down, rule 8): it is logged and the
     * card stays.
     */
    fun hideLowStock() {
        val card = state.value.lowStock ?: return
        viewModelScope.launch {
            try {
                lowStock.hide(card)
            } catch (e: IOException) {
                Log.w(TAG, "Low-stock card not hidden", e)
            }
        }
    }

    /** The monotonic clock of the deadlines, for the live countdown of *Aufguss fortsetzen*. */
    fun nowElapsedMs(): Long = clock.elapsedMs()

    /** *Nochmal brühen* (BRW-11): the tea's last session again, waiting in READY. */
    fun brewAgain(teaId: String) {
        // A second tap (on this or another row) while the first start is in flight would only be
        // refused and leave a stale TMR-21 question on Start (#54 S6, as D-065 for the quick timer).
        // A tap on another row right after a start is the same gesture, not a second brew (#54 S6, D-108).
        if (starting || startedJustNow) return
        starting = true
        viewModelScope.launch {
            try {
                val last = sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW).first()
                    .firstOrNull { it.snapshot.teaId == teaId } ?: return@launch
                val request = brewAgainStart(last)
                val before = starter.activeSessionIds()
                handle(starter.startSession(request), request, before)
            } finally {
                starting = false
            }
        }
    }

    private var starting = false

    /** The session this view model started last, and when: a refusal by it right after is a double tap. */
    private var started: Started? = null

    /** An answer of „Welchen Timer beenden?" (TMR-21, D-108): the timer of [slot] ends, then the brew starts. */
    fun endTimerAndStart(slot: TimerSlot) {
        val asked = pending.value ?: return
        // The session the answer named: one that started in its slot meanwhile is not ended (#194).
        val session = asked.question.timers.firstOrNull { it.slot == slot }?.sessionId
        pending.value = null
        viewModelScope.launch {
            val before = starter.activeSessionIds()
            val outcome = starter.finishActiveAndStart(asked.request, ending = slot, endingSession = session)
            handle(outcome, asked.request, before)
        }
    }

    /** *Abbrechen*: both active timers stay. */
    fun dismissQuestion() {
        pending.value = null
    }

    private suspend fun handle(outcome: TransitionOutcome, request: BrewStart, before: List<String>) {
        when (outcome) {
            TransitionOutcome.Accepted -> {
                val slot = starter.startedSlot(before)
                started = slot?.let { starter.activeIn(it)?.sessionId }?.let { Started(it, clock.elapsedMs()) }
                navigation.send(HomeEvent.OpenTimer(slot))
            }

            is TransitionOutcome.Rejected -> when (val refusal = starter.refusalOf(outcome.reason)) {
                // The tea's own session (a tea has at most one, D-108): open it, also during an alert, where the
                // timer shows the ringing alert first (rule 8).
                is StartRefusal.OpenSession -> if (!isDoubleTap(refusal.slot)) {
                    navigation.send(HomeEvent.OpenTimer(refusal.slot))
                }

                is StartRefusal.ChooseTimer -> if (starter.state.value.alertKey() != null) {
                    // No question over an alert (rule 8): the alerting timer is where to go now.
                    navigation.send(HomeEvent.OpenTimer(null))
                } else {
                    pending.value = Pending(request, HomeQuestion(refusal.timers), null)
                }

                // A brew start is never refused for a quick timer of its own (D-098).
                StartRefusal.RestartQuick, null -> Unit
            }

            TransitionOutcome.Ignored -> Unit
        }
    }

    /** The session this view model has just started is in [slot]: the timer is opening already (#54 S6). */
    private fun isDoubleTap(slot: TimerSlot): Boolean =
        startedJustNow && starter.activeIn(slot)?.sessionId == started?.sessionId

    /** This view model started a session less than a tap's window ago. */
    private val startedJustNow: Boolean
        get() = started?.let { clock.elapsedMs() - it.atElapsedMs < DOUBLE_TAP_WINDOW_MS } ?: false

    private fun Photo.path(): String? = photos.file(id).takeIf { it.isFile }?.path

    /** The session's ended rows only: from v0.4.0 it keeps its never-started ones too (Q-19). */
    private fun BrewSession.toRecent(tea: TeaOverview): RecentBrew {
        val brewed = endedSteps.sortedBy { it.position }.filter { it.kind != StepKind.RINSE && !it.skipped }
        val first = brewed.firstOrNull()
        return RecentBrew(
            teaId = tea.tea.id,
            sessionId = id,
            // The tea as it is called now: the row brews the tea again, the journal keeps the session's name.
            teaName = tea.tea.name,
            photoPath = tea.coverPhoto?.path(),
            startedAtMs = startedAtMs,
            zoneId = zoneId,
            firstStepSeconds = first?.plannedSeconds,
            tempC = first?.tempC ?: snapshot.baseTempC,
            infusions = brewed.size,
        )
    }

    private class Pending(val request: BrewStart, val question: HomeQuestion, val openedIn: String?)

    /** The timer in front of the controller (its alert first) and the first active brew session (D-108). */
    private class Timers(val front: TimerUiState, val brew: TimerUiState.Active?, val active: Int)

    private class Started(val sessionId: String, val atElapsedMs: Long)

    private companion object {
        /** *Zuletzt gebrüht* shows six teas (SCR-02, BRW-11, D-110). */
        const val SHOWN = 6

        /** Enough recent sessions to find six distinct teas. */
        const val RECENT_SESSIONS_WINDOW = 50

        /** A second row tap this soon after a start is the same gesture, not a new brew (#54 S6). */
        const val DOUBLE_TAP_WINDOW_MS = 1_000L

        const val TAG = "UmamiTHome"

        /** Any two timers at once, never a third (D-108). */
        const val MAX_ACTIVE_TIMERS = 2
    }
}
