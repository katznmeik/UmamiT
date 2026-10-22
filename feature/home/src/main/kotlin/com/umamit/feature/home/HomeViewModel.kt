package com.umamit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.MonotonicClock
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.BrewSession
import com.umamit.core.model.Photo
import com.umamit.core.model.StepKind
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.controller.brewAgainStart
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.TransitionOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A tea of *Nochmal brühen* (BRW-11) or *Favoriten*, with its cover photo file (null: the leaf placeholder). */
data class HomeTea(val id: String, val name: String, val photoPath: String?)

/**
 * A row of *Zuletzt gebrüht* (SCR-02): the `TeaListItem` meta line
 * „8. Juni · 1 Min · 65 °C" from the start, the first brewed step's planned
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
) : ViewModel() {
    private val pending = MutableStateFlow<Pending?>(null)
    private val navigation = Channel<HomeEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<HomeEvent> = navigation.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(
        teas.observeTeas(),
        sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW),
        starter.state,
        pending,
    ) { overviews, recentSessions, timer, question ->
        val owned = overviews.filter { !it.tea.isArchived }
        val byId = owned.associateBy { it.tea.id }
        val photoOf = { id: String -> byId[id]?.coverPhoto?.path() }
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
        )
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

    /** The monotonic clock of the deadlines, for the live countdown of *Aufguss fortsetzen*. */
    fun nowElapsedMs(): Long = clock.elapsedMs()

    /** *Nochmal brühen* (BRW-11): the tea's last session again, waiting in READY. */
    fun brewAgain(teaId: String) {
        viewModelScope.launch {
            val last = sessions.observeRecentSessions(RECENT_SESSIONS_WINDOW).first()
                .firstOrNull { it.snapshot.teaId == teaId } ?: return@launch
            val request = brewAgainStart(last)
            handle(starter.startSession(request), request)
        }
    }

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
            TransitionOutcome.Accepted -> navigation.send(HomeEvent.OpenTimer)

            is TransitionOutcome.Rejected -> if (outcome.reason is RejectionReason.ActiveTimerExists) {
                val timer = starter.state.value
                pending.value =
                    Pending(request, HomeQuestion((timer as? TimerUiState.Active)?.teaName), timer.alertKey())
            }

            TransitionOutcome.Ignored -> Unit
        }
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

    private companion object {
        /** *Nochmal brühen* and *Zuletzt gebrüht* show three each (SCR-02, BRW-11). */
        const val SHOWN = 3

        /** Enough recent sessions to find three distinct teas. */
        const val RECENT_SESSIONS_WINDOW = 50
    }
}
