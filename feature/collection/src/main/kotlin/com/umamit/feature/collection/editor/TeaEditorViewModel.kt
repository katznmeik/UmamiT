package com.umamit.feature.collection.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.common.WallClock
import com.umamit.core.data.AddWithoutCatalog
import com.umamit.core.data.AddWithoutCatalogResult
import com.umamit.core.data.TeaRepository
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** SCR-10's state. */
sealed interface TeaEditorUiState {
    data object Loading : TeaEditorUiState

    /** The tea to edit is gone. */
    data object NotFound : TeaEditorUiState

    /**
     * @property isNew a tea added *Ohne Katalog*: *Kauf* offers the package (INV-01).
     * @property today the latest purchase date and harvest year.
     * @property errors the refused fields, once *Speichern* was tapped (then kept up to date as you type).
     * @property saveFailed the database refused the last *Speichern*; the form stays.
     */
    data class Ready(
        val form: TeaEditorForm,
        val isNew: Boolean,
        val today: LocalDate,
        val hasChanges: Boolean,
        val errors: Set<TeaFieldError>,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : TeaEditorUiState
}

/** What the editor asks its host to do. */
sealed interface TeaEditorEvent {
    /** *Speichern* stored the tea [teaId]: close; after an add ([isNew]) its detail opens. */
    data class Saved(val teaId: String, val isNew: Boolean) : TeaEditorEvent
}

/**
 * Tee bearbeiten (SCR-10; TEA-01–TEA-03, PRF-02, INV-01, D-100): the tea [TEA_ID], or with none a new tea of the
 * Sorte [STYLE_KEY] chosen in *Sorte wählen* (SCR-11). Nothing is stored before *Speichern*. A new tea is added with
 * its Sorte's recipes and, with a package, its INITIAL stock event ([AddWithoutCatalog]); an edit writes only the
 * tea's row, so a Sorte change keeps the recipes (TEA-03) and a running session keeps its snapshot (rule 8). The
 * form lives in the [SavedStateHandle], so it survives a process death.
 */
@HiltViewModel
class TeaEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val teas: TeaRepository,
    private val adder: AddWithoutCatalog,
    private val sorteNames: SorteNames,
    private val clock: WallClock,
) : ViewModel() {
    private val teaId: String? = savedState[TEA_ID]
    private val today: LocalDate = Instant.ofEpochMilli(clock.nowMs()).atZone(clock.zone()).toLocalDate()

    /** The stored tea, or a new one; null while loading. */
    private val base = MutableStateFlow<Base?>(null)
    private val form = MutableStateFlow(savedState.get<TeaEditorForm>(FORM))
    private val showErrors = MutableStateFlow(savedState[SHOW_ERRORS] ?: false)
    private val saving = MutableStateFlow(false)
    private val saveFailed = MutableStateFlow(false)
    private val navigation = Channel<TeaEditorEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<TeaEditorEvent> = navigation.receiveAsFlow()

    val state: StateFlow<TeaEditorUiState> = combine(
        base,
        form,
        showErrors,
        saving,
        saveFailed,
    ) { b, f, show, isSaving, failed ->
        when {
            b == null -> TeaEditorUiState.Loading

            b is Base.Gone -> TeaEditorUiState.NotFound

            f == null -> TeaEditorUiState.Loading

            else -> TeaEditorUiState.Ready(
                form = f,
                isNew = b is Base.New,
                today = today,
                hasChanges = f != b.initialForm(),
                errors = if (show) f.errors(today) else emptySet(),
                saving = isSaving,
                saveFailed = failed,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TeaEditorUiState.Loading)

    init {
        viewModelScope.launch {
            val loaded = if (teaId == null) {
                Base.New(TeaEditorForm.new(savedState[STYLE_KEY], today))
            } else {
                teas.getTea(teaId)?.let(Base::Stored) ?: Base.Gone
            }
            if (form.value == null) {
                when (loaded) {
                    is Base.New -> setForm(loaded.form)
                    is Base.Stored -> setForm(TeaEditorForm.of(loaded.tea))
                    Base.Gone -> Unit
                }
            }
            base.value = loaded
        }
    }

    fun update(transform: (TeaEditorForm) -> TeaEditorForm) {
        form.value?.let { setForm(transform(it)) }
        saveFailed.value = false
    }

    /** *Speichern*: stores the tea when every field is valid, else shows the errors at the fields. */
    fun save() {
        val f = form.value
        val b = base.value
        when {
            f == null || b == null || b is Base.Gone || saving.value -> Unit

            f.errors(today).isNotEmpty() -> {
                showErrors.value = true
                savedState[SHOW_ERRORS] = true
            }

            else -> store(b, f)
        }
    }

    private fun store(b: Base, f: TeaEditorForm) {
        saving.value = true
        viewModelScope.launch {
            val saved = when (b) {
                is Base.New -> add(f)
                is Base.Stored -> edit(b.tea, f)
                Base.Gone -> null
            }
            saving.value = false
            if (saved == null) saveFailed.value = true else navigation.send(saved)
        }
    }

    private suspend fun add(f: TeaEditorForm): TeaEditorEvent.Saved? {
        val draft = Tea(id = "", name = "", category = TeaCategory.OTHER, form = TeaForm.LOOSE_LEAF)
        val tea = f.applyTo(draft, sorteNames::name, isNew = true)
        return when (val result = adder.add(tea, f.purchase())) {
            is AddWithoutCatalogResult.Added -> TeaEditorEvent.Saved(result.teaId, isNew = true)
            AddWithoutCatalogResult.Failed -> null
        }
    }

    private suspend fun edit(stored: Tea, f: TeaEditorForm): TeaEditorEvent.Saved {
        // The newest row, so a favourite or an archive set meanwhile is kept.
        val current = teas.getTea(stored.id) ?: stored
        val tea = f.applyTo(current, sorteNames::name, isNew = false)
        teas.saveTea(tea)
        base.value = Base.Stored(tea)
        return TeaEditorEvent.Saved(tea.id, isNew = false)
    }

    private fun setForm(value: TeaEditorForm) {
        form.value = value
        savedState[FORM] = value
    }

    private sealed interface Base {
        fun initialForm(): TeaEditorForm? = null

        data class New(val form: TeaEditorForm) : Base {
            override fun initialForm() = form
        }

        data object Gone : Base

        data class Stored(val tea: Tea) : Base {
            override fun initialForm() = TeaEditorForm.of(tea)
        }
    }

    companion object {
        /** The route argument: the tea to edit; absent for a new tea. */
        const val TEA_ID = "teaId"

        /** The route argument of a new tea: its Sorte from *Sorte wählen* (`other`: *Andere / unbekannt*). */
        const val STYLE_KEY = "styleKey"

        private const val FORM = "teaEditor.form"
        private const val SHOW_ERRORS = "teaEditor.showErrors"
    }
}
