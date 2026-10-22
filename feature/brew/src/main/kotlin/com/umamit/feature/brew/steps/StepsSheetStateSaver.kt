package com.umamit.feature.brew.steps

import androidx.compose.runtime.saveable.Saver
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus

/**
 * Keeps the Schritte-Sheet's edits, and the plan they are compared with (GEN-08),
 * across configuration changes and process death. A pending confirmation is not
 * kept, so a configuration change never shows it again.
 *
 * Encoding: `[hasRecipe, baseTempC?, preAlert, appliedPreAlert, stepCount,
 * (kind, seconds, tempC?, status)*, (applied kind, seconds, tempC?)*]`.
 */
internal val StepsSheetStateSaver: Saver<StepsSheetState, Any> = Saver(
    save = { state -> encode(state) },
    restore = { value -> decode(value) },
)

private const val INHERIT_CODE = -1
private const val PRE_ALERT_INDEX = 2
private const val APPLIED_PRE_ALERT_INDEX = 3
private const val STEP_COUNT_INDEX = 4
private const val HEADER_SIZE = 5
private const val STEP_SIZE = 4
private const val APPLIED_STEP_SIZE = 3

private fun PreAlertChoice.code(): Int = when (this) {
    PreAlertChoice.Inherit -> INHERIT_CODE
    PreAlertChoice.Off -> 0
    is PreAlertChoice.Seconds -> seconds
}

private fun preAlertOf(code: Int): PreAlertChoice = when (code) {
    INHERIT_CODE -> PreAlertChoice.Inherit
    0 -> PreAlertChoice.Off
    else -> PreAlertChoice.Seconds(code)
}

private fun encode(state: StepsSheetState): List<Int?> = buildList {
    add(if (state.hasRecipe) 1 else 0)
    add(state.baseTempC)
    add(state.preAlert.code())
    add(state.appliedPreAlert.code())
    add(state.steps.size)
    state.steps.forEach { step ->
        add(step.kind.ordinal)
        add(step.plannedSeconds)
        add(step.tempC)
        add(step.status.ordinal)
    }
    state.appliedSteps.forEach { step ->
        add(step.kind.ordinal)
        add(step.plannedSeconds)
        add(step.tempC)
    }
}

private fun decode(value: Any): StepsSheetState {
    @Suppress("UNCHECKED_CAST")
    val codes = value as List<Int?>
    val stepsEnd = HEADER_SIZE + requireNotNull(codes[STEP_COUNT_INDEX]) * STEP_SIZE
    val steps = codes.subList(HEADER_SIZE, stepsEnd).chunked(STEP_SIZE).map { step ->
        StepsSheetStep(
            kind = StepKind.entries[requireNotNull(step[0])],
            plannedSeconds = requireNotNull(step[1]),
            tempC = step[2],
            status = StepStatus.entries[requireNotNull(step[3])],
        )
    }
    val applied = codes.subList(stepsEnd, codes.size).chunked(APPLIED_STEP_SIZE).map { step ->
        PlannedStep(StepKind.entries[requireNotNull(step[0])], requireNotNull(step[1]), step[2])
    }
    val input = StepsSheetInput(
        steps = steps,
        preAlert = preAlertOf(requireNotNull(codes[PRE_ALERT_INDEX])),
        hasRecipe = codes[0] == 1,
        baseTempC = codes[1],
    )
    return StepsSheetState(input, applied, preAlertOf(requireNotNull(codes[APPLIED_PRE_ALERT_INDEX])))
}
