package com.umamit.feature.journal.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.SessionTasting
import com.umamit.core.data.TastingWrite
import com.umamit.core.data.TeaArchive
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.TasteAxis
import com.umamit.core.model.TastingLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
 *
 * The rating, the taste and the notes are saved at once through [SessionTasting] (JRN-02, JRN-03, JRN-05,
 * D-112): the stars on each tap, the notes 1 s after the last key and when the field or the page is left
 * ([flushNotes]). A write started always completes, also when the page is left or an alert takes the screen (rule 8).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SessionPageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val journal: JournalRepository,
    private val teas: TeaRepository,
    private val archive: TeaArchive,
    private val tasting: SessionTasting,
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

    private var pendingNotes: String? = null
    private var notesJob: Job? = null
    private val notesLock = Mutex()

    /** Rating writes run one after another in tap order, so the last tap is the one stored (JRN-02). */
    private val ratingLock = Mutex()

    /** A tap on the stars (JRN-02): [rating] (null for none) is saved at once, after any earlier rating write. */
    fun setRating(rating: Double?) {
        launchWrite { ratingLock.withLock { tasting.setRating(sessionId, rating) } }
    }

    /** Taste writes run one after another in release order, so the last one is the one stored (JRN-03). */
    private val tasteLock = Mutex()

    /** A slider released on [value] (JRN-03): saved at once, after any earlier taste write. */
    fun setTaste(axis: TasteAxis, value: Int) {
        launchWrite { tasteLock.withLock { tasting.setTaste(sessionId, axis, value) } }
    }

    /** *Zurücksetzen* (JRN-03): all six taste values are unset, in order with the slider writes. */
    fun clearTaste() {
        launchWrite { tasteLock.withLock { tasting.clearTaste(sessionId) } }
    }

    /** Descriptor writes run one after another in tap order, so the last tap is the one stored (JRN-04). */
    private val descriptorLock = Mutex()

    /**
     * A chip ticked or unticked in the descriptor sheet (JRN-04): saved at once, after any earlier tick. When the
     * repository does not store it (the 21st key, a session no longer completed), [onRefused] is called so the page
     * can drop what it showed in advance.
     */
    fun setDescriptor(key: String, ticked: Boolean, onRefused: () -> Unit = {}) {
        launchWrite {
            val result = descriptorLock.withLock { tasting.setDescriptor(sessionId, key, ticked) }
            if (result != TastingWrite.SAVED) onRefused()
        }
    }

    /** The notes field changed to [text] (JRN-05): saved 1 s after the last key, or sooner by [flushNotes]. */
    fun onNotesChanged(text: String) {
        pendingNotes = text.take(TastingLimits.MAX_NOTES_LENGTH)
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            delay(NOTES_DEBOUNCE_MS)
            flushNotes()
        }
    }

    /** The field or the page is left: a pending note is saved now (JRN-05). Nothing pending, nothing written. */
    fun flushNotes() {
        val text = pendingNotes ?: return
        pendingNotes = null
        notesJob?.cancel()
        notesJob = null
        launchWrite { notesLock.withLock { tasting.setNotes(sessionId, text) } }
    }

    override fun onCleared() {
        flushNotes()
    }

    /** A write that completes even when the page is left meanwhile (rule 8); a failed one changes nothing else. */
    private fun launchWrite(write: suspend () -> Unit) {
        viewModelScope.launch(start = CoroutineStart.ATOMIC) { withContext(NonCancellable) { write() } }
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

        /** The notes are saved this long after the last key (SCR-16). */
        const val NOTES_DEBOUNCE_MS = 1_000L
    }
}
