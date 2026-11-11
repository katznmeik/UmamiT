package com.umamit.feature.journal.session

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.data.EditableSession
import com.umamit.core.data.SessionEditResult
import com.umamit.core.data.SessionEditor
import com.umamit.core.data.VesselRepository
import com.umamit.core.model.BrewSession
import com.umamit.core.model.StepKind
import com.umamit.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * One row of the editor's SCHRITTE (SCR-17): „1 · Aufguss" with its fields when it was brewed ([form]); a step never
 * started ([notBrewed]) or skipped shows greyed, without fields.
 */
data class SessionEditorStepRow(
    val position: Int,
    val kind: StepKind,
    val form: SessionEditorStepForm?,
    val skipped: Boolean,
    val timeInvalid: Boolean = false,
    val timeOutOfRange: Boolean = false,
) {
    val notBrewed: Boolean get() = form == null && !skipped
}

/** SCR-17's state. */
sealed interface SessionEditorUiState {
    data object Loading : SessionEditorUiState

    /** The session is gone or no longer a finished one: nothing to edit. */
    data object NotFound : SessionEditorUiState

    /**
     * @property zone the session's own time zone: *Datum* and *Uhrzeit* are its wall time.
     * @property vessels *Gefäß*'s choices after *Kein Gefäß*: the vessels that are not archived and the session's own.
     * @property vesselName the chosen vessel's label: the snapshot name for the session's own vessel when it is gone.
     * @property stockChangeGrams „Dein Vorrat wird um 2 g angepasst.": shown while the leaf differs and the session
     *   has a stock deduction of its tea.
     * @property canSave *Speichern* is enabled: something changed, every field is valid, and no write is running.
     * @property saveFailed the last *Speichern* failed: „Die Änderungen konnten nicht gespeichert werden.".
     */
    data class Ready(
        val form: SessionEditorForm,
        val zone: ZoneId,
        val today: LocalDate,
        val vessels: List<Vessel>,
        val vesselName: String?,
        val steps: List<SessionEditorStepRow>,
        val errors: Set<SessionEditorError>,
        val stockChangeGrams: Double?,
        val hasChanges: Boolean,
        val canSave: Boolean,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : SessionEditorUiState
}

/** What the editor tells its host once. */
sealed interface SessionEditorEvent {
    /** *Speichern* wrote the edit: the host closes the editor; the page below shows the new values. */
    data object Saved : SessionEditorEvent
}

/**
 * The session editor (SCR-17; JRN-11, D-112) of the finished session `sessionId`: date and time, vessel, leaf,
 * water and each brewed step's time and °C. Nothing is written before *Speichern*, which runs `EditSession` in one
 * transaction ([SessionEditor]) and always completes once started (rule 8). The form lives in the
 * [SavedStateHandle], so an alert or a process death keeps the unsaved values.
 */
@HiltViewModel
class SessionEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val editor: SessionEditor,
    vesselRepository: VesselRepository,
    private val time: WallClock,
) : ViewModel() {
    private val sessionId: String = checkNotNull(savedState[SESSION_ID]) { "the session editor needs a sessionId" }

    private val base = MutableStateFlow<Base?>(null)
    private val form = MutableStateFlow(savedState.get<SessionEditorForm>(FORM))
    private val status = MutableStateFlow(Status())
    private val navigation = Channel<SessionEditorEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<SessionEditorEvent> = navigation.receiveAsFlow()

    val state: StateFlow<SessionEditorUiState> = combine(
        base,
        form,
        status,
        vesselRepository.observeVessels(),
    ) { b, f, st, vessels ->
        when (b) {
            null -> SessionEditorUiState.Loading
            Base.Gone -> SessionEditorUiState.NotFound
            is Base.Loaded -> if (f == null) SessionEditorUiState.Loading else ready(b.editable, f, st, vessels)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionEditorUiState.Loading)

    init {
        viewModelScope.launch {
            val editable = editor.editable(sessionId)
            if (editable == null) {
                base.value = Base.Gone
            } else {
                if (form.value == null) setForm(SessionEditorForm.of(editable.session))
                base.value = Base.Loaded(editable)
            }
        }
    }

    private fun ready(
        editable: EditableSession,
        f: SessionEditorForm,
        st: Status,
        vessels: List<Vessel>,
    ): SessionEditorUiState.Ready {
        val session = editable.session
        val zone = sessionZone(session.zoneId)
        val now = time.nowMs()
        val check = SessionEditorCheck(session, f, now)
        val withTea = editable.hasStockDeduction && session.snapshot.teaId != null
        val stored = session.snapshot.leafGrams
        val choices = vessels.filter { !it.isArchived || it.id == session.snapshot.vesselId }
        val errors = check.errors + if (st.refusedFuture) setOf(SessionEditorError.InFuture) else emptySet()
        return SessionEditorUiState.Ready(
            form = f,
            zone = zone,
            today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            vessels = choices,
            vesselName = vesselName(f, session, choices),
            steps = stepRows(session, f, check),
            errors = errors,
            stockChangeGrams = check.leafChange?.takeIf { withTea && stored != null }
                ?.let { gramsDifference(it, checkNotNull(stored)) },
            hasChanges = check.hasChanges,
            canSave = check.hasChanges && check.valid && !st.refusedFuture && !st.saving,
            saving = st.saving,
            saveFailed = st.saveFailed,
        )
    }

    /** The chosen vessel's label; a session whose vessel was deleted keeps showing its snapshot name. */
    private fun vesselName(f: SessionEditorForm, session: BrewSession, choices: List<Vessel>): String? =
        when (val id = f.vesselId) {
            null -> session.snapshot.vesselName?.takeIf { f.keepsVesselName }
            session.snapshot.vesselId -> choices.firstOrNull { it.id == id }?.name ?: session.snapshot.vesselName
            else -> choices.firstOrNull { it.id == id }?.name
        }

    private fun stepRows(
        session: BrewSession,
        f: SessionEditorForm,
        check: SessionEditorCheck,
    ): List<SessionEditorStepRow> {
        val fields = f.steps.associateBy { it.position }
        return session.steps.sortedBy { it.position }.map { row ->
            SessionEditorStepRow(
                position = row.position,
                kind = row.kind,
                form = fields[row.position],
                skipped = row.skipped,
                timeInvalid = row.position in check.invalidSteps,
                timeOutOfRange = row.position in check.outOfRangeSteps,
            )
        }
    }

    fun update(transform: (SessionEditorForm) -> SessionEditorForm) {
        val current = form.value ?: return
        setForm(transform(current))
        status.value = status.value.copy(refusedFuture = false, saveFailed = false)
    }

    /** *Datum* picked [date]: the start moves to that day at the same time (in the session's zone). */
    fun setDate(date: LocalDate) {
        val zone = zone() ?: return
        update { it.withDate(date, zone) }
    }

    /** *Uhrzeit* picked [hour]:[minute] on the same day. */
    fun setTime(hour: Int, minute: Int) {
        val zone = zone() ?: return
        update { it.withTime(hour, minute, zone) }
    }

    /** *Speichern*: writes the edit in one transaction; a write once started completes even if the editor is left. */
    fun save() {
        val ready = state.value as? SessionEditorUiState.Ready
        val editable = (base.value as? Base.Loaded)?.editable
        val edit = if (ready?.canSave == true && editable != null) {
            SessionEditorCheck(editable.session, ready.form, time.nowMs()).edit()
        } else {
            null
        }
        if (edit == null) return
        status.value = status.value.copy(saving = true, saveFailed = false)
        viewModelScope.launch(start = CoroutineStart.ATOMIC) {
            val result = withContext(NonCancellable) {
                try {
                    editor.editSession(sessionId, edit)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // A failed write keeps the editor open with its values (SCR-17).
                    Log.w(TAG, "session edit failed", e)
                    null
                }
            }
            status.value = when (result) {
                SessionEditResult.SAVED -> status.value

                // stays saving: the editor closes
                SessionEditResult.IN_FUTURE -> Status(refusedFuture = true)

                else -> Status(saveFailed = true)
            }
            if (result == SessionEditResult.SAVED) navigation.send(SessionEditorEvent.Saved)
        }
    }

    private fun zone(): ZoneId? = (base.value as? Base.Loaded)?.editable?.session?.zoneId?.let(::sessionZone)

    private fun setForm(value: SessionEditorForm) {
        form.value = value
        savedState[FORM] = value
    }

    private data class Status(
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val refusedFuture: Boolean = false,
    )

    private sealed interface Base {
        data object Gone : Base

        data class Loaded(val editable: EditableSession) : Base
    }

    companion object {
        /** The navigation argument (the route's property name), as the session page's. */
        const val SESSION_ID = "sessionId"

        private const val FORM = "sessionEditor.form"
        private const val TAG = "SessionEditor"
    }
}
