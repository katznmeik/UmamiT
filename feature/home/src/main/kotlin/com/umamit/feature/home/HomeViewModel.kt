package com.umamit.feature.home

import android.content.Intent
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
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.controller.brewAgainStart
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.core.timer.reliability.TimerReliability
import com.umamit.core.timer.reliability.firstMissingIntents
import com.umamit.core.timer.reliability.hasProblem
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
import javax.inject.Inject

/** A tea of *Nochmal brühen* (BRW-11) or *Favoriten*, with its cover photo file (null: the leaf placeholder). */
data class HomeTea(val id: String, val name: String, val photoPath: String?)

/**
 * A row of *Zuletzt gebrüht* (SCR-02): the `TeaListItem` meta line
 * „8. Juni · 1 Min. · 65 °C" from the start, the first brewed step's planned
 * duration and its temperature.
 */
data class RecentBrew(
    val sessionId: String,
    val teaName: String,
    val photoPath: String?,
    val startedAtMs: Long,
    val zoneId: String,
    val firstStepSeconds: Int?,
    val tempC: Int?,
    val infusions: Int,
)

/** „Erst die *[teaName]*-Sitzung beenden?" (TMR-21); [teaName] is null for the quick timer. */
data class HomeQuestion(val teaName: String?)

/**
 * What Start (SCR-02) shows.
 *
 * @property active the active session or quick timer: *Aufguss fortsetzen* with a live countdown (GEN-06).
 * @property collectionEmpty no tea to brew yet: the empty state (GEN-09).
 * @property alertKey the alert playing now, or null: nothing may cover it (rule 8).
 * @property reliabilityProblem a reliability check is missing: the banner (TMR-31).
 * @property lowStock the nudge slot's low-stock card (INV-10), or null.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val collectionEmpty: Boolean = false,
    val active: TimerUiState.Active? = null,
    val brewAgain: List<HomeTea> = emptyList(),
    val recent: List<RecentBrew> = emptyList(),
    val favorites: List<HomeTea> = emptyList(),
    val question: HomeQuestion? = null,
    val alertKey: String? = null,
    val reliabilityProblem: Boolean = false,
    val lowStock: LowStockCard? = null,
)

/** What Start asks its host to do once. */
enum class HomeEvent {
    /** A brew waits in READY: open the timer. */
    OpenTimer,
}

/**
 * Start's view model (SCR-02): the collection, the recent sessions and the
 * timer's state. *Nochmal brühen* starts the tea's last session again through
 * the [BrewStarter] (BRW-11) and asks first while another timer is active
 * (TMR-21).
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
    private val reliabilityProblem = MutableStateFlow(reliability.hasProblem())
    private val navigation = Channel<HomeEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<HomeEvent> = navigation.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        teas.observeTeas(),
        sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW),
        starter.state,
        pending,
        reliabilityProblem,
    ) { overviews, recentSessions, timer, question, problem ->
        val owned = overviews.filter { !it.tea.isArchived }
        val byId = owned.associateBy { it.tea.id }
        // A session row keeps its tea's photo when the tea was removed, as in the journal (D-093 Q13).
        val covers = overviews.associate { it.tea.id to it.coverPhoto }
        val photoOf = { id: String -> covers[id]?.path() }
        HomeUiState(
            loading = false,
            collectionEmpty = owned.isEmpty(),
            active = timer as? TimerUiState.Active,
            brewAgain = recentSessions.mapNotNull { it.snapshot.teaId }.distinct()
                .mapNotNull { byId[it] }
                .take(SHOWN)
                .map { HomeTea(it.tea.id, it.tea.name, it.coverPhoto?.path()) },
            recent = recentSessions.take(SHOWN).map { it.toRecent(it.snapshot.teaId?.let(photoOf)) },
            favorites = owned.filter {
                it.tea.isFavorite
            }.map { HomeTea(it.tea.id, it.tea.name, it.coverPhoto?.path()) },
            question = question?.question,
            alertKey = timer.alertKey(),
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
                if (alert != null) pending.update { it?.takeIf { p -> p.openedIn == alert } }
            }
        }
    }

    /** Reads the reliability checks again; Start calls it on every resume (TMR-31). */
    fun refreshReliability() {
        reliabilityProblem.value = reliability.hasProblem()
    }

    /** The banner's *Beheben*: the pages of the first missing check, for Start to start. */
    fun fixReliability(): List<Intent> = reliability.firstMissingIntents()

    /** *Ausblenden* on the low-stock card: hidden until the tea's next purchase (INV-10 AC2). */
    fun hideLowStock() {
        val card = state.value.lowStock ?: return
        viewModelScope.launch { lowStock.hide(card) }
    }

    /** The monotonic clock of the deadlines, for the live countdown of *Aufguss fortsetzen*. */
    fun nowElapsedMs(): Long = clock.elapsedMs()

    /** *Nochmal brühen* (BRW-11): the tea's last session again, waiting in READY. */
    fun brewAgain(teaId: String) {
        // A second tap (on this or another tile) while the first start is in flight would only be
        // refused and leave a stale TMR-21 question on Start (#54 S6, as D-065 for the quick timer).
        if (starting) return
        starting = true
        viewModelScope.launch {
            try {
                val last = sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW).first()
                    .firstOrNull { it.snapshot.teaId == teaId } ?: return@launch
                val request = brewAgainStart(last)
                handle(starter.startSession(request), request)
            } finally {
                starting = false
            }
        }
    }

    private var starting = false

    /** The session this view model started last, and when: a refusal by it right after is a double tap. */
    private var started: Started? = null

    /** *Beenden & neu starten* (TMR-21). */
    fun confirmRestart() {
        val request = pending.value?.request ?: return
        pending.value = null
        viewModelScope.launch { handle(starter.finishActiveAndStart(request), request) }
    }

    /** *Abbrechen*: the active session stays. */
    fun dismissQuestion() {
        pending.value = null
    }

    private suspend fun handle(outcome: TransitionOutcome, request: BrewStart) {
        when (outcome) {
            TransitionOutcome.Accepted -> {
                started =
                    (starter.state.value as? TimerUiState.Active)?.sessionId?.let { Started(it, clock.elapsedMs()) }
                navigation.send(HomeEvent.OpenTimer)
            }

            is TransitionOutcome.Rejected -> if (outcome.reason is RejectionReason.ActiveTimerExists) {
                val timer = starter.state.value
                if (isDoubleTap(timer)) {
                    // The brew this screen has just started: the timer is opening already, nothing to ask.
                    Unit
                } else if (timer.alertKey() != null) {
                    // No question over an alert (rule 8): the alerting timer is where to go now.
                    navigation.send(HomeEvent.OpenTimer)
                } else {
                    pending.value = Pending(request, HomeQuestion((timer as? TimerUiState.Active)?.teaName), null)
                }
            }

            TransitionOutcome.Ignored -> Unit
        }
    }

    private fun isDoubleTap(timer: TimerUiState): Boolean {
        val last = started ?: return false
        val active = (timer as? TimerUiState.Active)?.sessionId
        return active == last.sessionId && clock.elapsedMs() - last.atElapsedMs < DOUBLE_TAP_WINDOW_MS
    }

    private fun Photo.path(): String? = photos.file(id).takeIf { it.isFile }?.path

    private fun BrewSession.toRecent(photoPath: String?): RecentBrew {
        val brewed = steps.sortedBy { it.position }.filter { it.kind != StepKind.RINSE && !it.skipped }
        val first = brewed.firstOrNull()
        return RecentBrew(
            sessionId = id,
            teaName = snapshot.teaName,
            photoPath = photoPath,
            startedAtMs = startedAtMs,
            zoneId = zoneId,
            firstStepSeconds = first?.plannedSeconds,
            tempC = first?.tempC ?: snapshot.baseTempC,
            infusions = brewed.size,
        )
    }

    private class Pending(val request: BrewStart, val question: HomeQuestion, val openedIn: String?)

    private class Started(val sessionId: String, val atElapsedMs: Long)

    private companion object {
        /** *Nochmal brühen* and *Zuletzt gebrüht* show three each (SCR-02, BRW-11). */
        const val SHOWN = 3

        /** Enough recent sessions to find three distinct teas. */
        const val RECENT_SESSIONS_WINDOW = 50

        /** A second tile tap this soon after a start is the same gesture, not a new brew (#54 S6). */
        const val DOUBLE_TAP_WINDOW_MS = 1_000L
    }
}
