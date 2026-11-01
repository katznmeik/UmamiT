package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/**
 * The session page's view model (SCR-16 read-only, BRW-06, D-100) of the session `sessionId`. The session comes from
 * the journal's read and its tea's state from the collection, both as flows, so *Wiederherstellen* enables *So
 * nochmal brühen* at once and a deleted tea drops the link. Nothing here starts a brew: *So nochmal brühen* is the
 * host's way to the setup (BRW-01).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionPageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    journal: JournalRepository,
    private val teas: TeaRepository,
    private val archive: TeaArchive,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedState[SESSION_ID]) { "the session page needs a sessionId" }

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
