package com.umamit.feature.collection.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.backup.recipe.RecipeFile
import com.umamit.core.backup.recipe.RecipeFileResult
import com.umamit.core.backup.recipe.RecipeRefusal
import com.umamit.core.backup.recipe.SharedRecipe
import com.umamit.core.data.PhotoFiles
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.catalog.CatalogRepository
import com.umamit.core.data.catalog.TeaLinkLoader
import com.umamit.core.data.catalog.TeaLinkOutcome
import com.umamit.core.model.Recipe
import com.umamit.core.model.Tea
import com.umamit.core.model.TeaCategory
import com.umamit.core.model.TeaForm
import com.umamit.core.model.TeaOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * A collection tea as SCR-32 offers it: its row (cover, name, Sorte or Kategorie) and whether it is [full] (10
 * recipes: greyed out, „Höchstens 10 Rezepte").
 */
data class TeaOption(
    val id: String,
    val name: String,
    val category: TeaCategory,
    val sorte: String? = null,
    val secondaryName: String? = null,
    val isArchived: Boolean = false,
    val photoPath: String? = null,
    val recipeCount: Int = 0,
    val catalogKey: String? = null,
) {
    val full: Boolean get() = recipeCount >= Recipe.MAX_PER_TEA
}

/** The tea SCR-32's *Rezept übernehmen* writes to. */
sealed interface TeaChoice {
    data class Existing(val teaId: String) : TeaChoice

    data object NewTea : TeaChoice
}

/** What SCR-32 „Rezept übernehmen" shows (03 SCR-32). */
sealed interface ReceiveRecipeUiState {
    /** The file is read. */
    data object Loading : ReceiveRecipeUiState

    /** The reader refused the file: a centred message with *Schließen*, nothing else. */
    data class Refused(val refusal: RecipeRefusal) : ReceiveRecipeUiState

    /**
     * A valid file.
     *
     * @property match the preselected tea's row and how it was found (null: „Diesen Tee hast du noch nicht.").
     * @property picked a tea chosen with *Anderen Tee wählen …*, shown as a row of its own.
     * @property choice the tea *Rezept übernehmen* writes to; null until one is chosen.
     * @property teas every collection tea, archived ones included, for *Anderen Tee wählen …*.
     * @property catalogKey the local catalog tea *Neuen Tee anlegen* copies („aus dem Katalog"); null: „ohne Katalog".
     * @property offersShopLink *Tee über Shop-Link laden*: the file has a checked shop link whose tea is not in the
     *   local catalog (D-130 Q4).
     * @property linkLoading its one fetch runs.
     * @property linkFailure why the fetch led to no catalog tea (CAT-17's messages); null for none.
     * @property saving *Rezept übernehmen* runs.
     * @property failure why the last *Rezept übernehmen* failed; null for none.
     */
    data class Ready(
        val recipe: SharedRecipe,
        val match: TeaOption? = null,
        val matchKind: MatchKind? = null,
        val picked: TeaOption? = null,
        val choice: TeaChoice? = null,
        val teas: List<TeaOption> = emptyList(),
        val catalogKey: String? = null,
        val offersShopLink: Boolean = false,
        val linkLoading: Boolean = false,
        val linkFailure: TeaLinkOutcome? = null,
        val saving: Boolean = false,
        val failure: TakeOverResult? = null,
    ) : ReceiveRecipeUiState {
        val canTakeOver: Boolean get() = choice != null && !saving
    }
}

/** One-shot outcomes of SCR-32. */
sealed interface ReceiveRecipeEvent {
    /** The recipe [recipeName] was added to [teaId]: SCR-09 with „Rezept „…" übernommen". */
    data class TakenOver(val teaId: String, val recipeName: String) : ReceiveRecipeEvent
}

/**
 * SCR-32 „Rezept übernehmen" (PRF-08 AC5–AC9, D-130 Q4, Q13): the received file through the one reader
 * ([RecipeFile.read]), the same tea preselected ([RecipeMatch]), *Anderen Tee wählen …*, *Neuen Tee anlegen* and, only
 * on its tap, *Tee über Shop-Link laden*'s one CAT-17 fetch ([TeaLinkLoader]). Opening a file writes nothing and
 * sends no request; *Rezept übernehmen* ([RecipeTakeOver]) is the only write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReceiveRecipeViewModel internal constructor(
    teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val catalog: CatalogRepository,
    private val links: TeaLinkLoader,
    private val takeOver: RecipeTakeOver,
    private val photos: PhotoFiles,
    private val readDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @Inject
    constructor(
        teas: TeaRepository,
        recipes: RecipeRepository,
        catalog: CatalogRepository,
        links: TeaLinkLoader,
        takeOver: RecipeTakeOver,
        photos: PhotoFiles,
    ) : this(teas, recipes, catalog, links, takeOver, photos, Dispatchers.Default)

    private val file = MutableStateFlow<FileState>(FileState.Unread)

    /** The catalog key a successful *Tee über Shop-Link laden* stored (it may be a merged tea's, D-081). */
    private val loadedKey = MutableStateFlow<String?>(null)
    private val form = MutableStateFlow(Form())
    private val events = Channel<ReceiveRecipeEvent>(Channel.BUFFERED)
    private var opened: Long? = null

    val navigation: Flow<ReceiveRecipeEvent> = events.receiveAsFlow()

    private val options: Flow<List<TeaOption>> = teas.observeTeas().flatMapLatest { overviews ->
        if (overviews.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(overviews.map { overview -> recipes.observeRecipes(overview.tea.id).map { overview.toOption(it) } }) {
                it.toList()
            }
        }
    }

    private val catalogKey: Flow<String?> = combine(file, loadedKey) { state, loaded ->
        loaded ?: (state as? FileState.Read)?.recipe?.tea?.catalogKey
    }.flatMapLatest { key ->
        key?.let { catalog.observeTea(it).map { detail -> detail?.key } } ?: flowOf(null)
    }

    val state: StateFlow<ReceiveRecipeUiState> = combine(file, options, catalogKey, form) { file, teas, key, form ->
        when (file) {
            FileState.Unread -> ReceiveRecipeUiState.Loading
            is FileState.Refused -> ReceiveRecipeUiState.Refused(file.refusal)
            is FileState.Read -> ready(file.recipe, teas, key, form)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReceiveRecipeUiState.Loading)

    /**
     * Reads the received [bytes] once per [token] (a recreated screen keeps what was read). Writes nothing and never
     * sends a request.
     */
    fun open(token: Long, bytes: ByteArray) {
        if (opened == token) return
        opened = token
        file.value = FileState.Unread
        loadedKey.value = null
        form.value = Form()
        viewModelScope.launch {
            file.value = when (val result = withContext(readDispatcher) { RecipeFile.read(bytes) }) {
                is RecipeFileResult.Valid -> FileState.Read(result.recipe)
                is RecipeFileResult.Refused -> FileState.Refused(result.refusal)
            }
        }
    }

    /** A tea's row, or *Neuen Tee anlegen*; a tea with 10 recipes can't be chosen. */
    fun choose(choice: TeaChoice) {
        val ready = state.value as? ReceiveRecipeUiState.Ready ?: return
        if (choice is TeaChoice.Existing && ready.teas.firstOrNull { it.id == choice.teaId }?.full != false) return
        form.update { it.copy(choice = choice, failure = null) }
    }

    /** *Anderen Tee wählen …*'s pick: shown as a row and chosen. */
    fun pick(teaId: String) {
        val ready = state.value as? ReceiveRecipeUiState.Ready ?: return
        val tea = ready.teas.firstOrNull { it.id == teaId }?.takeIf { !it.full } ?: return
        form.update { it.copy(picked = tea.id, choice = TeaChoice.Existing(tea.id), failure = null) }
    }

    /** *Tee über Shop-Link laden* (D-130 Q4, GEN-01 (d)): the one CAT-17 fetch of the file's shop link. */
    fun loadShopLink() {
        val ready = state.value as? ReceiveRecipeUiState.Ready ?: return
        val url = ready.recipe.tea.shopUrl ?: return
        if (!ready.offersShopLink || ready.linkLoading) return
        form.update { it.copy(linkLoading = true, linkFailure = null) }
        viewModelScope.launch {
            val outcome = links.load(url)
            if (outcome is TeaLinkOutcome.Opened) loadedKey.value = outcome.key
            form.update {
                it.copy(linkLoading = false, linkFailure = outcome.takeUnless { o -> o is TeaLinkOutcome.Opened })
            }
        }
    }

    /** *Rezept übernehmen*: the only write (PRF-08 AC7). */
    fun takeOver() {
        val ready = state.value as? ReceiveRecipeUiState.Ready ?: return
        val choice = ready.choice ?: return
        if (ready.saving) return
        form.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val target = when (choice) {
                is TeaChoice.Existing -> TakeOverTarget.Existing(choice.teaId)
                TeaChoice.NewTea -> TakeOverTarget.NewTea(ready.catalogKey)
            }
            when (val result = takeOver.takeOver(ready.recipe, target)) {
                is TakeOverResult.TakenOver -> {
                    form.update { it.copy(saving = false) }
                    events.send(ReceiveRecipeEvent.TakenOver(result.teaId, result.recipeName))
                }

                else -> form.update { it.copy(saving = false, failure = result) }
            }
        }
    }

    private fun ready(recipe: SharedRecipe, teas: List<TeaOption>, key: String?, form: Form): ReceiveRecipeUiState {
        val match = RecipeMatch.find(recipe.tea, teas.map { it.asTea() })
        val matched = match?.let { m -> teas.firstOrNull { it.id == m.teaId } }
        val picked = form.picked?.let { id -> teas.firstOrNull { it.id == id } }?.takeIf { it.id != matched?.id }
        // The match is preselected unless it is full; a choice made stays while its tea can take a recipe.
        val choice = when (val chosen = form.choice) {
            null -> matched?.takeIf { !it.full }?.let { TeaChoice.Existing(it.id) }
            is TeaChoice.Existing -> chosen.takeIf { teas.any { it.id == chosen.teaId && !it.full } }
            TeaChoice.NewTea -> chosen
        }
        return ReceiveRecipeUiState.Ready(
            recipe = recipe,
            match = matched,
            matchKind = match?.kind,
            picked = picked,
            choice = choice,
            teas = teas.sortedWith(compareBy({ it.isArchived }, { it.name.lowercase() })),
            catalogKey = key,
            offersShopLink = recipe.tea.shopUrl != null && key == null,
            linkLoading = form.linkLoading,
            linkFailure = form.linkFailure,
            saving = form.saving,
            failure = form.failure,
        )
    }

    private fun TeaOverview.toOption(recipes: List<Recipe>) = TeaOption(
        id = tea.id,
        name = tea.name,
        category = tea.category,
        sorte = tea.teaType,
        secondaryName = tea.secondaryName,
        isArchived = tea.isArchived,
        photoPath = coverPhoto?.let { photos.file(it.id) }?.takeIf { it.isFile }?.path,
        recipeCount = recipes.size,
        catalogKey = tea.catalogKey,
    )

    private fun TeaOption.asTea() = Tea(
        id = id,
        name = name,
        category = category,
        form = TeaForm.LOOSE_LEAF,
        catalogKey = catalogKey,
        isArchived = isArchived,
    )

    private sealed interface FileState {
        data object Unread : FileState

        data class Refused(val refusal: RecipeRefusal) : FileState

        data class Read(val recipe: SharedRecipe) : FileState
    }

    private data class Form(
        val choice: TeaChoice? = null,
        val picked: String? = null,
        val linkLoading: Boolean = false,
        val linkFailure: TeaLinkOutcome? = null,
        val saving: Boolean = false,
        val failure: TakeOverResult? = null,
    )
}
