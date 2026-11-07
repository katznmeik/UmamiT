package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the session page (SCR-16) shows. */
sealed interface SessionPageUiState {
    data object Loading : SessionPageUiState

    /** The session is gone (GEN-09). */
    data object NotFound : SessionPageUiState

    data class Ready(val page: SessionPage) : SessionPageUiState
}

/** What the session page tells its host once. */
sealed interface SessionPageEvent {
    /** *Löschen* removed the session (JRN-10, D-102): the host leaves the page. */
    data object Deleted : SessionPageEvent
}

/**
 * The session page's view model (SCR-16, BRW-06, D-100) of the session `sessionId`. The session comes from
 * the journal's read and its tea's state from the collection, both as flows, so *Wiederherstellen* enables *So
 * nochmal brühen* at once and a deleted tea drops the link. Nothing here starts a brew: *So nochmal brühen* is the
 * host's way to the setup (BRW-01). *Löschen* (D-102) removes a finished session and tells the host.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionPageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val journal: JournalRepository,
    private val teas: TeaRepository,
    private val archive: TeaArchive,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedState[SESSION_ID]) { "the session page needs a sessionId" }

    private val _events = Channel<SessionPageEvent>(Channel.BUFFERED)

    /** [SessionPageEvent]s for the host, each delivered once. */
    val events: Flow<SessionPageEvent> = _events.receiveAsFlow()

    val state: StateFlow<SessionPageUiState> = journal.observeSessions()
        .map { list -> list.firstOrNull { it.session.id == sessionId }?.session }
        .distinctUntilChanged()
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(SessionPageUiState.NotFound)
            } else {
                teaOf(session.snapshot.teaId).map { SessionPageUiState.Ready(sessionPage(session, it)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionPageUiState.Loading)

    /**
     * *Löschen*, confirmed (JRN-10, D-102): the finished session goes with its stock deduction; then [events] says
     * [SessionPageEvent.Deleted]. A session still in progress is never deleted (the repository refuses it too).
     */
    fun delete() {
        val page = (state.value as? SessionPageUiState.Ready)?.page ?: return
        if (page.inProgress) return
        viewModelScope.launch {
            journal.deleteSession(sessionId)
            _events.send(SessionPageEvent.Deleted)
        }
    }

    /** *Wiederherstellen* of the session's archived tea (TEA-04, D-100): *So nochmal brühen* is offered again. */
    fun restore() {
        val teaId = (state.value as? SessionPageUiState.Ready)?.page?.teaId ?: return
        viewModelScope.launch { archive.restore(teaId) }
    }

    private fun teaOf(id: String?): Flow<SessionTea> = if (id == null) {
        flowOf(SessionTea.DELETED)
    } else {
        teas.observeTea(id).map { overview ->
            when {
                overview == null -> SessionTea.DELETED
                overview.tea.isArchived -> SessionTea.ARCHIVED
                else -> SessionTea.AVAILABLE
            }
        }.distinctUntilChanged()
    }

    companion object {
        /** The navigation argument (the route's property name). */
        const val SESSION_ID = "sessionId"
    }
}
