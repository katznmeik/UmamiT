package com.umamit.feature.brew.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
 * planned values, its [status] and its own pre-alert [preAlertSeconds] (null
 * *Automatisch*, 0 *Aus*; TMR-23, D-093). Only PENDING steps are editable.
 */
data class StepsSheetStep(
    val kind: StepKind,
    val plannedSeconds: Int,
    val tempC: Int?,
    val status: StepStatus,
    val preAlertSeconds: Int? = null,
) {
    /** The planned values, as the engine takes them, the step's own pre-alert included. */
    val planned: PlannedStep get() = PlannedStep(kind, plannedSeconds, tempC, preAlertSeconds)

    /** The step's own pre-alert choice: *Automatisch*, *Aus* or a lead. */
    val preAlert: PreAlertChoice get() = PreAlertChoice.of(preAlertSeconds)
}

/**
 * What the Schritte-Sheet opens with: the session's [steps] with their status
 * and pre-alerts, whether the session [hasRecipe] (a quick timer has none, so
 * *Im Rezept speichern* is hidden), the recipe's [baseTempC], shown for steps
 * without their own temperature, and what each step's *Automatisch* stands for
 * (TMR-23, D-093 Q17): the session's pre-alert base [preAlertBaseSeconds] (B:
 * the recipe's value ?: the global setting, frozen at the start) and
 * [longStepsOnly] for a cold or ice brew, whose steps never have one (TMR-18).
 */
data class StepsSheetInput(
    val steps: List<StepsSheetStep>,
    val hasRecipe: Boolean,
    val baseTempC: Int?,
    val preAlertBaseSeconds: Int,
    val longStepsOnly: Boolean = false,
) {
    companion object {
        /** The input for the active [session] (SCR-04, SCR-05 hosts). */
        fun of(session: TimerSession, hasRecipe: Boolean, baseTempC: Int?): StepsSheetInput = StepsSheetInput(
            steps = session.steps.map {
                StepsSheetStep(it.kind, it.plannedSeconds, it.tempC, it.status, it.preAlertSeconds)
            },
            hasRecipe = hasRecipe,
            baseTempC = baseTempC,
            preAlertBaseSeconds = session.preAlert.inheritedSeconds,
            longStepsOnly = session.longStepsOnly,
        )
    }
}

/**
 * *Im Rezept speichern* (BRW-02, SCR-31): the whole plan as the sheet shows it,
 * each step with its pre-alert, for the recipe the session was started from.
 */
data class SaveToRecipeRequest(val steps: List<PlannedStep>) {

    /** The steps as recipe steps, each with its pre-alert (TMR-23, D-093; notes per step arrive in M4). */
    val recipeSteps: List<RecipeStep>
        get() = steps.map { RecipeStep(it.kind, it.plannedSeconds, it.tempC, preAlertSeconds = it.preAlertSeconds) }
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
 * rules (PRF-04, GEN-12, TMR-10, each step's pre-alert of TMR-23) and builds the
 * requests; it calls neither the engine's reducer nor the database, so SCR-04 and
 * SCR-05 can host it unchanged.
 */
@Stable
@Suppress("TooManyFunctions") // one function per editing rule of SCR-31 and per request
class StepsSheetState(input: StepsSheetInput) {

    /** What the session runs now: the plan, each step's pre-alert included, the edits are compared with (GEN-08). */
    internal var appliedSteps: List<PlannedStep> = input.steps.map { it.planned }
        private set

    /** A restored state: [input] as edited, compared with the [appliedSteps] it had. */
    internal constructor(input: StepsSheetInput, appliedSteps: List<PlannedStep>) : this(input) {
        this.appliedSteps = appliedSteps
    }

    /** Whether *Im Rezept speichern* is offered. */
    val hasRecipe: Boolean = input.hasRecipe

    /** The recipe's base temperature, for steps without their own. */
    val baseTempC: Int? = input.baseTempC

    /** The session's pre-alert base B, from which *Automatisch* takes its lead (TMR-23). */
    val preAlertBaseSeconds: Int = input.preAlertBaseSeconds

    /** A cold or ice brew: every step is a long step without a pre-alert (TMR-18). */
    val longStepsOnly: Boolean = input.longStepsOnly

    /** The plan as edited so far, each step with its own pre-alert. */
    var steps: List<StepsSheetStep> by mutableStateOf(input.steps)
        private set

    /** *Im Rezept speichern* was tapped and its confirmation has not been shown yet (a one-shot event). */
    var confirmationPending: Boolean by mutableStateOf(false)
        private set

    /**
     * The plan, a step's pre-alert included, differs from what the session runs (GEN-08):
     * closing the sheet other than with *Fertig* then asks „Änderungen verwerfen?".
     * Edits undone by hand count as no edits.
     */
    val isDirty: Boolean
        get() = steps.map { it.planned } != appliedSteps

    /** *Verwerfen*: back to what the session runs, so a reopened sheet shows no stale edits. */
    fun discard() {
        steps = steps.withPlanned(appliedSteps)
    }

    /** Done, running and skipped steps are read-only (SCR-31); the running step's pre-alert is not ([hasPreAlert]). */
    fun isEditable(index: Int): Boolean = steps[index].status == StepStatus.PENDING

    /**
     * Step [index] has a *Vorwarnung* selector: every pending step and the running one, whose pre-alert is its
     * only editable field (SCR-31, D-093). Ended steps keep theirs.
     */
    fun hasPreAlert(index: Int): Boolean =
        steps[index].status == StepStatus.PENDING || steps[index].status == StepStatus.STARTED

    /** Sets step [index]'s pre-alert to [choice] for this session; ignored without a selector ([hasPreAlert]). */
    fun setPreAlert(index: Int, choice: PreAlertChoice) {
        if (hasPreAlert(index)) {
            steps = steps.mapIndexed { i, old ->
                if (i == index) old.copy(preAlertSeconds = choice.explicitSeconds) else old
            }
        }
    }

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
        steps = steps +
            StepsSheetStep(added.kind, added.plannedSeconds, added.tempC, StepStatus.PENDING, added.preAlertSeconds)
    }

    /** The confirmation of the last save is on screen; a reopened sheet does not show it again. */
    fun confirmationShown() {
        confirmationPending = false
    }

    /**
     * *Fertig*: the whole plan for the engine's `editSteps` (04 §3.2), each step with its own pre-alert. The host
     * applies it, so the sheet counts it as applied: it is no longer [isDirty].
     */
    fun editRequest(): TimerEvent.EditSteps {
        val plan = steps.map { it.planned }
        appliedSteps = plan
        return TimerEvent.EditSteps(plan)
    }

    /** *Im Rezept speichern*: the request, each step with its own pre-alert as in [editRequest], and the snackbar. */
    fun saveRequest(): SaveToRecipeRequest {
        confirmationPending = true
        return SaveToRecipeRequest(steps.map { it.planned })
    }

    companion object {
        /** Keeps the edits across configuration changes and process death. */
        val Saver: Saver<StepsSheetState, Any> = StepsSheetStateSaver
    }
}

/** These steps with the planned values of [planned], pre-alerts included, keeping each step's status by position. */
private fun List<StepsSheetStep>.withPlanned(planned: List<PlannedStep>): List<StepsSheetStep> =
    planned.mapIndexed { i, step ->
        val status = getOrNull(i)?.status ?: StepStatus.PENDING
        StepsSheetStep(step.kind, step.plannedSeconds, step.tempC, status, step.preAlertSeconds)
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
