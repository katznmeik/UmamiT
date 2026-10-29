package com.umamit.feature.brew.steps

import androidx.compose.runtime.saveable.Saver
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PlannedStep
import com.umamit.core.timer.engine.StepStatus

/**
 * Keeps the Schritte-Sheet's edits, each step's pre-alert included, and the plan they are compared with (GEN-08),
 * across configuration changes and process death. A pending confirmation is not kept, so a configuration change
 * never shows it again.
 *
 * Encoding: `[hasRecipe, baseTempC?, preAlertBaseSeconds, longStepsOnly, stepCount,
 * (kind, seconds, tempC?, status, preAlertSeconds?)*, (applied kind, seconds, tempC?, preAlertSeconds?)*]`.
 */
internal val StepsSheetStateSaver: Saver<StepsSheetState, Any> = Saver(
    save = { state -> encode(state) },
    restore = { value -> decode(value) },
)

private const val PRE_ALERT_BASE_INDEX = 2
private const val LONG_STEPS_ONLY_INDEX = 3
private const val STEP_COUNT_INDEX = 4
private const val HEADER_SIZE = 5
private const val STEP_SIZE = 5
private const val APPLIED_STEP_SIZE = 4

private fun encode(state: StepsSheetState): List<Int?> = buildList {
    add(if (state.hasRecipe) 1 else 0)
    add(state.baseTempC)
    add(state.preAlertBaseSeconds)
    add(if (state.longStepsOnly) 1 else 0)
    add(state.steps.size)
    state.steps.forEach { step ->
        add(step.kind.ordinal)
        add(step.plannedSeconds)
        add(step.tempC)
        add(step.status.ordinal)
        add(step.preAlertSeconds)
    }
    state.appliedSteps.forEach { step ->
        add(step.kind.ordinal)
        add(step.plannedSeconds)
        add(step.tempC)
        add(step.preAlertSeconds)
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
            preAlertSeconds = step[4],
        )
    }
    val applied = codes.subList(stepsEnd, codes.size).chunked(APPLIED_STEP_SIZE).map { step ->
        PlannedStep(StepKind.entries[requireNotNull(step[0])], requireNotNull(step[1]), step[2], step[3])
    }
    val input = StepsSheetInput(
        steps = steps,
        hasRecipe = codes[0] == 1,
        baseTempC = codes[1],
        preAlertBaseSeconds = requireNotNull(codes[PRE_ALERT_BASE_INDEX]),
        longStepsOnly = codes[LONG_STEPS_ONLY_INDEX] == 1,
    )
    return StepsSheetState(input, applied)
}
