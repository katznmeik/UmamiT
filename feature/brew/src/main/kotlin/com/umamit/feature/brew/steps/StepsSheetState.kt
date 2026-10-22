package com.umamit.feature.brew.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.umamit.core.model.RecipeStep
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.timer.engine.TimerSession

/**
 * One step of the session plan as the Schritte-Sheet shows it (SCR-31): its
 * planned values and its [status]. Only PENDING steps are editable.
 */
data class StepsSheetStep(val kind: StepKind, val plannedSeconds: Int, val tempC: Int?, val status: StepStatus) {
    /** The planned values, as the engine takes them. */
    val planned: PlannedStep get() = PlannedStep(kind, plannedSeconds, tempC)
}

/**
 * What the Schritte-Sheet opens with: the session's [steps] with their status,
 * the session's [preAlert] option, whether the session [hasRecipe] (a quick
 * timer has none, so *Im Rezept speichern* is hidden), and the recipe's
 * [baseTempC], shown for steps without their own temperature.
 */
data class StepsSheetInput(
    val steps: List<StepsSheetStep>,
    val preAlert: PreAlertChoice,
    val hasRecipe: Boolean,
    val baseTempC: Int?,
) {
    companion object {
        /** The input for the active [session] (SCR-04, SCR-05 hosts). */
        fun of(session: TimerSession, hasRecipe: Boolean, baseTempC: Int?) = StepsSheetInput(
            steps = session.steps.map { StepsSheetStep(it.kind, it.plannedSeconds, it.tempC, it.status) },
            preAlert = PreAlertChoice.of(session.preAlert),
            hasRecipe = hasRecipe,
            baseTempC = baseTempC,
        )
    }
}

/**
 * *Im Rezept speichern* (BRW-02, SCR-31): the whole plan as the sheet shows it
 * and the pre-alert option, for the recipe the session was started from.
 */
data class SaveToRecipeRequest(val steps: List<PlannedStep>, val preAlert: PreAlertChoice) {

    /** The steps as recipe steps (notes per step arrive in M4). */
    val recipeSteps: List<RecipeStep> get() = steps.map { RecipeStep(it.kind, it.plannedSeconds, it.tempC) }

    /**
     * The recipe's pre-alert value (05 §3.2: null = global, 0 = off, else
     * seconds). *Wie Einstellung* keeps the recipe's [current] value, because
     * the session inherited it from there.
     */
    fun recipePreAlertSeconds(current: Int?): Int? = when (preAlert) {
        PreAlertChoice.Inherit -> current
        PreAlertChoice.Off -> 0
        is PreAlertChoice.Seconds -> preAlert.seconds
    }
}

/** One stepper tap on a pending step (SCR-31). */
enum class StepEdit {
    /** Duration + 5 s below 10 min, + 1 min from 10 min on; at most 24 h. */
    LENGTHEN,

    /** Duration − 1 min above 10 min, − 5 s from 10 min down; at least 5 s. */
    SHORTEN,

    /** Temperature + 5 °C, at most 100 °C. */
    WARM,

    /** Temperature − 5 °C, at least 5 °C. */
    COOL,
}

/**
 * The state holder of the Schritte-Sheet (SCR-31). It applies the editing
 * rules (PRF-04, GEN-12, TMR-10) and builds the requests; it calls neither the
 * engine nor the database, so SCR-04 and SCR-05 can host it unchanged.
 */
@Stable
class StepsSheetState(input: StepsSheetInput) {

    /** Whether *Im Rezept speichern* is offered. */
    val hasRecipe: Boolean = input.hasRecipe

    /** The recipe's base temperature, for steps without their own. */
    val baseTempC: Int? = input.baseTempC

    /** The plan as edited so far. */
    var steps: List<StepsSheetStep> by mutableStateOf(input.steps)
        private set

    /** The pre-alert option as edited so far. */
    var preAlert: PreAlertChoice by mutableStateOf(input.preAlert)
        private set

    /** Counts *Im Rezept speichern* taps; each one shows the confirmation snackbar. */
    var saveConfirmations: Int by mutableIntStateOf(0)
        private set

    /** Done, running and skipped steps are read-only (SCR-31). */
    fun isEditable(index: Int): Boolean = steps[index].status == StepStatus.PENDING

    /** The temperature shown for step [index]: its own, else the recipe's base; null when neither is set. */
    fun shownTempC(index: Int): Int? = steps[index].tempC ?: baseTempC

    /** Whether [edit] would change step [index]: only pending steps, within the bounds (PRF-04, GEN-12). */
    fun canEdit(index: Int, edit: StepEdit): Boolean =
        isEditable(index) && steps[index].edited(edit, shownTempC(index)) != steps[index]

    /** A pending step can go unless it is the last non-rinse step of the plan (PRF-04, D-048). */
    fun canDelete(index: Int): Boolean =
        isEditable(index) && (steps[index].kind == StepKind.RINSE || steps.count { it.kind != StepKind.RINSE } > 1)

    /** Applies one stepper tap to step [index]; ignored when [canEdit] is false. */
    fun edit(index: Int, edit: StepEdit) {
        if (canEdit(index, edit)) {
            val step = steps[index].edited(edit, shownTempC(index))
            steps = steps.mapIndexed { i, old -> if (i == index) step else old }
        }
    }

    fun delete(index: Int) {
        if (canDelete(index)) steps = steps.filterIndexed { i, _ -> i != index }
    }

    /** *Aufguss hinzufügen*: appends the engine's *Weiterer Aufguss* default (TMR-10, D-048). */
    fun addStep() {
        val added = BrewTimerEngine.defaultAddedStep(steps.map { it.planned })
        steps = steps + StepsSheetStep(added.kind, added.plannedSeconds, added.tempC, StepStatus.PENDING)
    }

    fun selectPreAlert(choice: PreAlertChoice) {
        preAlert = choice
    }

    /** *Fertig*: the whole plan and the pre-alert option for the engine's `editSteps` (04 §3.2). */
    fun editRequest(): TimerEvent.EditSteps = TimerEvent.EditSteps(steps.map { it.planned }, preAlert)

    /** *Im Rezept speichern*: the request, and the confirmation snackbar. */
    fun saveRequest(): SaveToRecipeRequest {
        saveConfirmations++
        return SaveToRecipeRequest(steps.map { it.planned }, preAlert)
    }

    companion object {
        /** Keeps the edits across configuration changes and process death. */
        val Saver: Saver<StepsSheetState, Any> = Saver(
            save = { state -> encode(state) },
            restore = { value -> decode(value) },
        )
    }
}

/** A [StepsSheetState] for [input] that survives configuration changes; a new input starts over. */
@Composable
fun rememberStepsSheetState(input: StepsSheetInput): StepsSheetState =
    rememberSaveable(input, saver = StepsSheetState.Saver) { StepsSheetState(input) }

/** This step after one stepper tap; unchanged at a bound or without any temperature. */
private fun StepsSheetStep.edited(edit: StepEdit, shownTempC: Int?): StepsSheetStep = when (edit) {
    StepEdit.LENGTHEN -> copy(plannedSeconds = longer(plannedSeconds))

    StepEdit.SHORTEN -> copy(plannedSeconds = shorter(plannedSeconds))

    StepEdit.WARM -> if (shownTempC == null ||
        warmer(shownTempC) == shownTempC
    ) {
        this
    } else {
        copy(tempC = warmer(shownTempC))
    }

    StepEdit.COOL -> if (shownTempC == null ||
        cooler(shownTempC) == shownTempC
    ) {
        this
    } else {
        copy(tempC = cooler(shownTempC))
    }
}

// Editing rules (PRF-04, GEN-12): durations 5 s – 24 h in 5 s steps below 10 min and
// 1 min steps from 10 min on; temperatures 5–100 °C in 5 °C steps (05 §3.2).
private const val MIN_SECONDS = 5
private const val MAX_SECONDS = 86_400
private const val LONG_STEP_SECONDS = 600
private const val SHORT_STEP_UNIT = 5
private const val LONG_STEP_UNIT = 60
private const val TEMP_UNIT = 5
private const val MIN_TEMP_C = 5
private const val MAX_TEMP_C = 100

/** The next longer duration: the next multiple of 5 s below 10 min, of 1 min from 10 min on. */
internal fun longer(seconds: Int): Int {
    val unit = if (seconds < LONG_STEP_SECONDS) SHORT_STEP_UNIT else LONG_STEP_UNIT
    return nextMultiple(seconds, unit).coerceIn(MIN_SECONDS, MAX_SECONDS).coerceAtLeast(seconds)
}

/** The next shorter duration: the previous multiple of 1 min above 10 min, of 5 s from 10 min down. */
internal fun shorter(seconds: Int): Int {
    val unit = if (seconds > LONG_STEP_SECONDS) LONG_STEP_UNIT else SHORT_STEP_UNIT
    return previousMultiple(seconds, unit).coerceIn(MIN_SECONDS, MAX_SECONDS).coerceAtMost(seconds)
}

internal fun warmer(tempC: Int): Int =
    nextMultiple(tempC, TEMP_UNIT).coerceIn(MIN_TEMP_C, MAX_TEMP_C).coerceAtLeast(tempC)

internal fun cooler(tempC: Int): Int =
    previousMultiple(tempC, TEMP_UNIT).coerceIn(MIN_TEMP_C, MAX_TEMP_C).coerceAtMost(tempC)

private fun nextMultiple(value: Int, unit: Int): Int = Math.floorDiv(value, unit) * unit + unit

private fun previousMultiple(value: Int, unit: Int): Int = Math.floorDiv(value - 1, unit) * unit

// Saver encoding: [hasRecipe, baseTempC?, preAlert, (kind, seconds, tempC?, status)*]. The snackbar
// counter is not kept, so a configuration change never shows the confirmation again.
private const val INHERIT_CODE = -1
private const val HEADER_SIZE = 3
private const val STEP_SIZE = 4

private fun encode(state: StepsSheetState): List<Int?> = buildList {
    add(if (state.hasRecipe) 1 else 0)
    add(state.baseTempC)
    add(
        when (val choice = state.preAlert) {
            PreAlertChoice.Inherit -> INHERIT_CODE
            PreAlertChoice.Off -> 0
            is PreAlertChoice.Seconds -> choice.seconds
        },
    )
    state.steps.forEach { step ->
        add(step.kind.ordinal)
        add(step.plannedSeconds)
        add(step.tempC)
        add(step.status.ordinal)
    }
}

private fun decode(value: Any): StepsSheetState {
    @Suppress("UNCHECKED_CAST")
    val codes = value as List<Int?>
    val preAlert = when (val code = requireNotNull(codes[2])) {
        INHERIT_CODE -> PreAlertChoice.Inherit
        0 -> PreAlertChoice.Off
        else -> PreAlertChoice.Seconds(code)
    }
    val steps = codes.drop(HEADER_SIZE).chunked(STEP_SIZE).map { step ->
        StepsSheetStep(
            kind = StepKind.entries[requireNotNull(step[0])],
            plannedSeconds = requireNotNull(step[1]),
            tempC = step[2],
            status = StepStatus.entries[requireNotNull(step[3])],
        )
    }
    return StepsSheetState(StepsSheetInput(steps, preAlert, hasRecipe = codes[0] == 1, baseTempC = codes[1]))
}
