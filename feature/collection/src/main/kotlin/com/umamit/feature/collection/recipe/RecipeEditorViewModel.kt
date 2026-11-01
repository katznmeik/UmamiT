package com.umamit.feature.collection.recipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.DeleteRecipeResult
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.RoomAddWithoutCatalog
import com.umamit.core.data.SaveRecipeResult
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.domain.style.BuiltInStyles
import com.umamit.core.model.Recipe
import com.umamit.core.model.Vessel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** SCR-12's state. */
sealed interface RecipeEditorUiState {
    data object Loading : RecipeEditorUiState

    /** The recipe to edit, or the tea of a new one, is gone. */
    data object NotFound : RecipeEditorUiState

    /**
     * @property isNew *Rezept hinzufügen*: nothing is stored before *Speichern* (D-100).
     * @property isStoredDefault the stored recipe is the tea's default: *Als Standard* stays on (PRF-01).
     * @property canDelete a stored recipe that is not its tea's last one (PRF-01).
     * @property vessels the *Gefäß* list: favourites first, then A–Z, never an archived vessel (WARE-02).
     * @property selectedVessel the form's vessel, archived or not („Kyusu 250 ml (archiviert)", D-100).
     * @property globalPreAlertSeconds the settings' Vorwarnung (SET-10): B while the recipe has none of its own.
     * @property errors the refused fields, once *Speichern* was tapped (then kept up to date as you type).
     */
    data class Ready(
        val form: RecipeForm,
        val isNew: Boolean,
        val isStoredDefault: Boolean,
        val canDelete: Boolean,
        val vessels: List<Vessel>,
        val selectedVessel: Vessel?,
        val globalPreAlertSeconds: Int,
        val hasChanges: Boolean,
        val errors: Set<RecipeFieldError>,
        val saving: Boolean = false,
        val message: RecipeEditorMessage? = null,
    ) : RecipeEditorUiState {
        /** B of the steps' *Automatisch* (TMR-23): the recipe's Vorwarnung, else the settings'. */
        val preAlertBaseSeconds: Int get() = form.preAlertSeconds ?: globalPreAlertSeconds
    }
}

/** A notice of the last action, shown once in a snackbar. */
enum class RecipeEditorMessage {
    /** A new recipe of a tea that has 10 already (PRF-01): nothing was stored. */
    LimitReached,

    /** The tea's last recipe stays (PRF-01). */
    LastRecipe,
}

/** What the editor asks its host to do. */
sealed interface RecipeEditorEvent {
    /** *Speichern* stored the recipe [recipeId]: close. */
    data class Saved(val recipeId: String) : RecipeEditorEvent

    /** The recipe was deleted: close. */
    data object Deleted : RecipeEditorEvent
}

/**
 * Rezept bearbeiten (SCR-12; PRF-01, PRF-03, PRF-04, WARE-02–WARE-04, TEA-17, D-100): the recipe [RECIPE_ID] of the
 * tea [TEA_ID], or with none *Rezept hinzufügen*, a copy of the tea's default with an empty name. Nothing is stored
 * before *Speichern*, which goes through [RecipeRepository.saveRecipe]: one default, at most 10 recipes, and „Rezept
 * prüfen" cleared (TEA-17). A running session keeps its snapshot (rule 8). The form lives in the
 * [SavedStateHandle], so it survives a process death.
 */
@HiltViewModel
class RecipeEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val recipes: RecipeRepository,
    private val teas: TeaRepository,
    vessels: VesselRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val teaId: String = checkNotNull(savedState[TEA_ID]) { "the recipe editor needs its tea" }
    private val recipeId: String? = savedState[RECIPE_ID]

    /** The stored recipe, or the new one's start; null while loading. */
    private val base = MutableStateFlow<Base?>(null)
    private val form = MutableStateFlow(savedState.get<RecipeForm>(FORM))
    private val showErrors = MutableStateFlow(savedState[SHOW_ERRORS] ?: false)
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<RecipeEditorMessage?>(null)
    private val navigation = Channel<RecipeEditorEvent>(Channel.BUFFERED)

    /** One-shot requests to the host. */
    val events: Flow<RecipeEditorEvent> = navigation.receiveAsFlow()

    private val surroundings: Flow<Surroundings> = combine(
        recipes.observeRecipes(teaId).map { it.size }.distinctUntilChanged(),
        vessels.observeVessels(),
        settings.settings.map { it.preAlertSeconds }.distinctUntilChanged(),
    ) { count, all, preAlert -> Surroundings(count, all, preAlert) }

    private val editing: Flow<Editing> = combine(form, showErrors, saving, message) { f, show, isSaving, m ->
        Editing(f, show, isSaving, m)
    }

    val state: StateFlow<RecipeEditorUiState> = combine(base, editing, surroundings) { b, e, s ->
        val f = e.form
        when {
            b == null -> RecipeEditorUiState.Loading

            b is Base.Gone -> RecipeEditorUiState.NotFound

            f == null -> RecipeEditorUiState.Loading

            else -> {
                val stored = (b as? Base.Stored)?.recipe
                RecipeEditorUiState.Ready(
                    form = f,
                    isNew = stored == null,
                    isStoredDefault = stored?.isDefault == true,
                    canDelete = stored != null && s.recipeCount > 1,
                    vessels = s.vessels.filter { !it.isArchived }
                        .sortedWith(compareBy({ !it.isFavorite }, { it.name.lowercase() }, { it.id })),
                    selectedVessel = s.vessels.firstOrNull { it.id == f.vesselId },
                    globalPreAlertSeconds = s.globalPreAlertSeconds,
                    hasChanges = f.content() != b.initialForm()?.content(),
                    errors = if (e.showErrors) f.errors() else emptySet(),
                    saving = e.saving,
                    message = e.message,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecipeEditorUiState.Loading)

    init {
        viewModelScope.launch {
            val loaded = load()
            if (form.value == null) loaded.initialForm()?.let(::setForm)
            base.value = loaded
        }
    }

    fun update(transform: (RecipeForm) -> RecipeForm) {
        form.value?.let { setForm(transform(it)) }
    }

    /** A vessel of the list, or *Kein Gefäß* (null): sets the water and scales the leaf (WARE-03). */
    fun selectVessel(vesselId: String?) {
        val vessel = (state.value as? RecipeEditorUiState.Ready)?.vessels?.firstOrNull { it.id == vesselId }
        update { it.withVessel(vessel) }
    }

    /** The snackbar showed [message]. */
    fun messageShown() {
        message.value = null
    }

    /** *Speichern*: stores the recipe when every field is valid, else shows the errors at the fields. */
    fun save() {
        val f = form.value
        val b = base.value
        when {
            f == null || b == null || b is Base.Gone || saving.value -> Unit

            f.errors().isNotEmpty() -> {
                showErrors.value = true
                savedState[SHOW_ERRORS] = true
            }

            else -> store(b, f)
        }
    }

    /** ⋮ *Löschen* of a stored recipe, never its tea's last one; a deleted default passes on (PRF-01). */
    fun delete() {
        val stored = (base.value as? Base.Stored)?.recipe ?: return
        viewModelScope.launch {
            when (recipes.deleteRecipe(stored.id)) {
                DeleteRecipeResult.Deleted, DeleteRecipeResult.NotFound -> navigation.send(RecipeEditorEvent.Deleted)
                DeleteRecipeResult.LastRecipe -> message.value = RecipeEditorMessage.LastRecipe
            }
        }
    }

    private fun store(b: Base, f: RecipeForm) {
        val recipe = when (b) {
            is Base.Stored -> b.recipe
            is Base.New -> b.template
            Base.Gone -> return
        }
        saving.value = true
        viewModelScope.launch {
            // The newest row, so a default set meanwhile (*Als Standard* elsewhere) is kept.
            val current = (b as? Base.Stored)?.let { recipes.getRecipe(it.recipe.id) } ?: recipe
            val saved = f.applyTo(current)
            val result = recipes.saveRecipe(saved)
            saving.value = false
            when (result) {
                SaveRecipeResult.Saved -> {
                    base.value = Base.Stored(saved)
                    navigation.send(RecipeEditorEvent.Saved(saved.id))
                }

                SaveRecipeResult.LimitReached -> message.value = RecipeEditorMessage.LimitReached
            }
        }
    }

    private suspend fun load(): Base = if (recipeId != null) {
        recipes.getRecipe(recipeId)?.takeIf { it.teaId == teaId }?.let(Base::Stored) ?: Base.Gone
    } else {
        teas.getTea(teaId)?.let { newRecipe(it.styleKey) } ?: Base.Gone
    }

    /**
     * *Rezept hinzufügen* (D-100): the default's values with an empty name, after the other recipes; a tea without
     * any recipe starts from its style's default, else PRF-02's *Westlich*.
     */
    private suspend fun newRecipe(styleKey: String?): Base {
        val own = recipes.observeRecipes(teaId).first()
        val start = own.firstOrNull { it.isDefault }
            ?: own.firstOrNull()
            ?: styleKey?.let(BuiltInStyles::byKey)?.recipes
                ?.let { list -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }
                ?.toRecipe("", teaId, 0)
            ?: RoomAddWithoutCatalog.WESTERN_DEFAULT.toRecipe("", teaId, 0)
        val template = start.copy(
            id = UUID.randomUUID().toString(),
            name = "",
            isDefault = false,
            position = (own.maxOfOrNull { it.position } ?: -1) + 1,
        )
        return Base.New(template)
    }

    private fun setForm(value: RecipeForm) {
        form.value = value
        savedState[FORM] = value
    }

    private data class Surroundings(val recipeCount: Int, val vessels: List<Vessel>, val globalPreAlertSeconds: Int)

    private data class Editing(
        val form: RecipeForm?,
        val showErrors: Boolean,
        val saving: Boolean,
        val message: RecipeEditorMessage?,
    )

    private sealed interface Base {
        fun initialForm(): RecipeForm? = null

        /** *Rezept hinzufügen*: [template] is stored on *Speichern* with the form's values. */
        data class New(val template: Recipe) : Base {
            override fun initialForm() = RecipeForm.of(template)
        }

        data object Gone : Base

        data class Stored(val recipe: Recipe) : Base {
            override fun initialForm() = RecipeForm.of(recipe)
        }
    }

    companion object {
        /** The route arguments: the tea, and the recipe to edit (absent: *Rezept hinzufügen*). */
        const val TEA_ID = "teaId"
        const val RECIPE_ID = "recipeId"

        private const val FORM = "recipeEditor.form"
        private const val SHOW_ERRORS = "recipeEditor.showErrors"
    }
}
