package com.umamit.feature.brew.setup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.data.RecipeRepository
import com.umamit.core.data.SessionRepository
import com.umamit.core.data.SettingsRepository
import com.umamit.core.data.StockRepository
import com.umamit.core.data.TeaRepository
import com.umamit.core.data.VesselRepository
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.BrewSession
import com.umamit.core.model.KettleWater
import com.umamit.core.model.Recipe
import com.umamit.core.model.SessionSnapshot
import com.umamit.core.model.TeaOverview
import com.umamit.core.model.TimerPhase
import com.umamit.core.model.Vessel
import com.umamit.core.timer.controller.BrewStart
import com.umamit.core.timer.controller.BrewStarter
import com.umamit.core.timer.controller.TimerUiState
import com.umamit.core.timer.controller.alertKey
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertSettings
import com.umamit.core.timer.engine.RejectionReason
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TransitionOutcome
import com.umamit.feature.brew.steps.SaveToRecipeRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * The setup's view model (SCR-04; BRW-02, BRW-03, BRW-10, TMR-01, TMR-21). It
 * opens on the tea `teaId` with its default recipe, or, for *Brühen anpassen*
 * (BRW-11), on the waiting session `sessionId` with that session's values.
 * Every change applies to this session only, until *Im Rezept speichern*.
 * *Brühen starten* goes through the [BrewStarter]; nothing else here touches
 * the timer.
 */
// One public function per user action of SCR-04, plus the few helpers they share.
@Suppress("TooManyFunctions")
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrewSetupViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val teas: TeaRepository,
    private val recipes: RecipeRepository,
    private val vessels: VesselRepository,
    private val stock: StockRepository,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val starter: BrewStarter,
) : ViewModel() {

    /** The session *Brühen anpassen* came from; it is replaced without asking while it still waits. */
    private val adjustedSessionId: String? = savedState[SESSION_ID]

    private val edits = MutableStateFlow(SetupEdits.decode(savedState[EDITS]))
    private val pending = MutableStateFlow<PendingStart?>(null)
    private val navigation = Channel<BrewSetupEvent>(Channel.BUFFERED)

    /** One-shot requests to the host: open the timer. */
    val events: Flow<BrewSetupEvent> = navigation.receiveAsFlow()

    private val source: Flow<Source?> = flow {
        val session = adjustedSessionId?.let { sessions.getSession(it) }
        val teaId = savedState.get<String>(TEA_ID) ?: session?.snapshot?.teaId
        if (teaId == null) {
            emit(null)
        } else {
            emitAll(sourceOf(teaId, session))
        }
    }

    val state: StateFlow<BrewSetupUiState> = combine(source, edits, pending, starter.state) { s, e, p, timer ->
        when {
            s == null -> BrewSetupUiState.NotFound
            e == null -> BrewSetupUiState.Loading
            else -> uiState(s, e, p, timer)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BrewSetupUiState.Loading)

    init {
        viewModelScope.launch {
            source.first { it != null }?.let { s -> if (edits.value == null) setEdits(s.initialEdits()) }
        }
        // Alerts first (rule 8): a new alert closes the question.
        viewModelScope.launch {
            starter.state.collect { timer ->
                val alert = timer.alertKey()
                if (alert != null) pending.update { it?.takeIf { p -> p.openedIn == alert } }
            }
        }
    }

    fun selectRecipe(id: String) = launchWithSource { s ->
        s.recipes.firstOrNull { it.id == id }?.let { setEdits(SetupEdits.of(it)) }
    }

    fun selectVessel(id: String) = launchWithSource { s ->
        s.vessels.firstOrNull { it.id == id }?.let { vessel -> change { it.withVessel(vessel) } }
    }

    fun stepWater(up: Boolean) = change { it.withWater(BrewScaling.stepWater(it.waterMl, up)) }

    fun stepLeaf(up: Boolean) = change { it.withLeaf(BrewScaling.stepLeaf(it.leafGrams, up)) }

    fun setRatioLocked(locked: Boolean) = change { it.withLock(locked) }

    /** *Fertig* in the Schritte-Sheet: the steps of this session, each with its pre-alert (SCR-31, D-093). */
    fun applySteps(request: TimerEvent.EditSteps) = change { it.withSteps(request.steps) }

    /** *Im Rezept speichern* in the Schritte-Sheet: only the steps, each with its pre-alert (SCR-31, D-093). */
    fun saveStepsToRecipe(request: SaveToRecipeRequest) = launchWithSource { s ->
        val recipe = s.recipe(edits.value?.recipeId) ?: return@launchWithSource
        recipes.replaceSteps(
            recipe.id,
            request.recipeSteps.map { it.copy(tempC = it.tempC.takeIf { t -> t != recipe.baseTempC }) },
        )
    }

    /** *Im Rezept speichern* on the setup: every value of this session into the recipe (BRW-02). */
    fun saveToRecipe() = launchWithSource { s ->
        val current = edits.value ?: return@launchWithSource
        val recipe = s.recipe(current.recipeId) ?: return@launchWithSource
        recipes.saveRecipe(current.appliedTo(recipe))
    }

    /**
     * *Speichern* in „Wie viel ist noch da?" (BRW-02, INV-03, D-093 point 7): the tea's stock becomes
     * [newTotalGrams] through one correction event; the header follows the stock it observes.
     */
    fun correctStock(newTotalGrams: Double) = launchWithSource { s ->
        stock.correctStock(s.tea.tea.id, newTotalGrams)
    }

    /** *Brühen starten* (TMR-01): the session waits in READY and the timer opens. */
    fun startBrew() = launchWithSource { s ->
        val current = edits.value ?: return@launchWithSource
        val request = current.startRequest(s)
        val timer = starter.state.value as? TimerUiState.Active
        val outcome = if (timer != null && timer.isAdjustable()) {
            starter.finishActiveAndStart(request)
        } else {
            starter.startSession(request)
        }
        handle(outcome, request)
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
            TransitionOutcome.Accepted -> navigation.send(BrewSetupEvent.OpenTimer)

            is TransitionOutcome.Rejected -> if (outcome.reason is RejectionReason.ActiveTimerExists) {
                val timer = starter.state.value
                if (timer.alertKey() != null) {
                    // No question over an alert (rule 8): the alerting timer is where to go now.
                    navigation.send(BrewSetupEvent.OpenTimer)
                } else {
                    val active = timer as? TimerUiState.Active
                    pending.value = PendingStart(request, ActiveSessionQuestion(active?.teaName), null)
                }
            }

            TransitionOutcome.Ignored -> Unit
        }
    }

    /** The adjusted session still waits for its first step, so starting over loses nothing (BRW-11). */
    private fun TimerUiState.Active.isAdjustable(): Boolean = adjustedSessionId != null &&
        sessionId == adjustedSessionId &&
        phase == TimerPhase.READY &&
        steps.none { it.status != StepStatus.PENDING }

    private fun change(transform: (SetupEdits) -> SetupEdits) {
        edits.value?.let { setEdits(transform(it)) }
    }

    private fun setEdits(value: SetupEdits) {
        edits.value = value
        savedState[EDITS] = SetupEdits.encode(value)
    }

    private fun launchWithSource(block: suspend (Source) -> Unit) {
        viewModelScope.launch { source.first()?.let { block(it) } }
    }

    private fun sourceOf(teaId: String, session: BrewSession?): Flow<Source?> = teas.observeTea(teaId)
        .transformLatest { overview ->
            if (overview == null) {
                emit(null)
            } else {
                emitAll(
                    combine(
                        recipes.observeRecipes(teaId),
                        vessels.observeVessels(),
                        stock.observeStock(teaId).map { it.remainingGrams },
                        settings.settings.map { it.preAlertSeconds },
                    ) { r, v, grams, global -> Source(overview, r, v, grams, global, session) },
                )
            }
        }

    private fun uiState(s: Source, e: SetupEdits, p: PendingStart?, timer: TimerUiState): BrewSetupUiState.Ready {
        val recipe = s.recipe(e.recipeId)
        val firstTemp = e.steps.firstOrNull()?.tempC ?: e.baseTempC
        return BrewSetupUiState.Ready(
            teaName = s.tea.tea.name,
            secondaryName = s.tea.tea.secondaryName,
            stockGrams = s.stockGrams,
            recipeCheckReason = s.tea.tea.recipeCheckReason,
            recipes = s.recipes.map { RecipeChip(it.id, it.name) },
            selectedRecipeId = e.recipeId,
            vessels = s.vessels.filter { !it.isArchived || it.id == e.vesselId }
                .sortedWith(compareBy({ !it.isFavorite }, { it.name.lowercase() }))
                .map { VesselOption(it.id, it.name, it.brewMl()) },
            selectedVesselId = e.vesselId,
            method = e.method,
            waterMl = e.waterMl,
            leafGrams = e.leafGrams,
            ratioLocked = e.locked,
            ratioPer100Ml = per100Ml(e.leafGrams, e.waterMl),
            baseTempC = e.baseTempC,
            kettle = Kettle(firstTemp, KettleWater.of(firstTemp, iceBrew = e.method == BrewMethod.ICE_BREW)),
            steps = e.steps,
            // The B that startRequest hands the controller with the recipe's value (04 §5, TMR-23).
            preAlertBaseSeconds = PreAlertSettings.inherited(recipe?.preAlertSeconds, s.globalPreAlertSeconds)
                .inheritedSeconds,
            canSaveToRecipe = recipe != null && e.differsFrom(recipe),
            question = p?.question,
            alertKey = timer.alertKey(),
        )
    }

    private fun SetupEdits.startRequest(s: Source): BrewStart {
        val recipe = s.recipe(recipeId)
        val vessel = s.vessels.firstOrNull { it.id == vesselId }
        val tea = s.tea.tea
        return BrewStart(
            snapshot = SessionSnapshot(
                teaId = tea.id,
                teaName = tea.name,
                teaSecondaryName = tea.secondaryName,
                teaType = tea.teaType,
                styleKey = tea.styleKey,
                category = tea.category,
                profileId = recipe?.id,
                profileName = recipe?.name,
                method = method,
                vesselId = vessel?.id,
                vesselName = vessel?.name,
                leafGrams = leafGrams,
                waterMl = waterMl,
                baseTempC = baseTempC,
            ),
            steps = steps,
            recipePreAlertSeconds = recipe?.preAlertSeconds,
        )
    }

    /** What the setup is built from: the tea, its recipes, the vessels, the stock and the global pre-alert. */
    private class Source(
        val tea: TeaOverview,
        val recipes: List<Recipe>,
        val vessels: List<Vessel>,
        val stockGrams: Double,
        val globalPreAlertSeconds: Int,
        val session: BrewSession?,
    ) {
        fun recipe(id: String?): Recipe? = recipes.firstOrNull { it.id == id }

        private fun startingRecipe(): Recipe? =
            tea.defaultRecipe?.let { d -> recipes.firstOrNull { it.id == d.id } } ?: recipes.firstOrNull()

        /** The default recipe, or the adjusted session's values on top of its recipe (BRW-11). */
        fun initialEdits(): SetupEdits {
            val session = session ?: return startingRecipe()?.let(SetupEdits::of) ?: SetupEdits.default()
            val snapshot = session.snapshot
            val recipe = recipe(snapshot.profileId) ?: startingRecipe()
            val base = recipe?.let(SetupEdits::of) ?: SetupEdits.default()
            val water = snapshot.waterMl ?: base.waterMl
            val leaf = snapshot.leafGrams ?: base.leafGrams
            return base.copy(
                vesselId = snapshot.vesselId,
                waterMl = water,
                leafGrams = leaf,
                ratio = Ratio(leaf, water),
                method = snapshot.method,
                baseTempC = snapshot.baseTempC ?: base.baseTempC,
                // Each step with its own pre-alert, as the session holds it (D-093).
                steps = session.steps.sortedBy { it.position }.map {
                    val tempC = it.tempC ?: snapshot.baseTempC ?: base.baseTempC
                    PlannedStep(it.kind, it.plannedSeconds, tempC, it.preAlertSeconds)
                },
            )
        }
    }

    private data class PendingStart(val request: BrewStart, val question: ActiveSessionQuestion, val openedIn: String?)

    companion object {
        /** Navigation arguments (the route's property names). */
        const val TEA_ID = "teaId"
        const val SESSION_ID = "sessionId"
        private const val EDITS = "setupEdits"

        /** Leaf per 100 ml, to 0,1 g (PRF-03 AC1). */
        internal fun per100Ml(leafGrams: Double, waterMl: Int): Double = (leafGrams.toBigDecimal() * HUNDRED)
            .divide(waterMl.toBigDecimal(), 1, RoundingMode.HALF_UP)
            .toDouble()

        private val HUNDRED = BigDecimal(100)
    }
}
