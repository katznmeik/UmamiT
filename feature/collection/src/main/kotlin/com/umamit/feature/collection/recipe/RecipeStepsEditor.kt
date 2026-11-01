package com.umamit.feature.collection.recipe

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.StepKind
import com.umamit.core.ui.DurationPicker
import com.umamit.core.ui.RepeatingIconButton
import com.umamit.feature.collection.R

/**
 * The steps of Rezept bearbeiten (SCR-12, PRF-04): each with its drag handle (TalkBack: move up / down), its kind,
 * duration („Dauer wählen" and hold-to-repeat ± from the Schritte-Sheet), temperature, Vorwarnung and note;
 * *Aufguss hinzufügen* below, up to 30 steps.
 */
@Composable
internal fun RecipeSteps(state: RecipeEditorUiState.Ready, update: ((RecipeForm) -> RecipeForm) -> Unit) {
    val form = state.form
    val haptics = LocalHapticFeedback.current
    // The dragged step (by key), how far it is off its slot, and each step's height to know when it passes one.
    var dragged by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var pickerFor by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentSteps by rememberUpdatedState(form.steps)
    val drag = object : StepDrag {
        override fun start(key: Int) {
            dragged = key
            offset = 0f
        }

        override fun by(dy: Float) {
            offset += dy
            val steps = currentSteps
            val index = steps.indexOfFirst { it.key == dragged }
            if (index < 0) return
            val next = steps.getOrNull(index + 1)
            val previous = steps.getOrNull(index - 1)
            // Past half of the neighbour, the step takes its place and stays under the finger.
            if (next != null && offset > (heights[next.key] ?: 0) / 2f) {
                offset -= heights[next.key] ?: 0
                update { it.withStepMoved(index, index + 1) }
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            } else if (previous != null && offset < -(heights[previous.key] ?: 0) / 2f) {
                offset += heights[previous.key] ?: 0
                update { it.withStepMoved(index, index - 1) }
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
        }

        override fun end() {
            dragged = null
            offset = 0f
        }
    }
    Column {
        form.steps.forEachIndexed { index, step ->
            key(step.key) {
                val isDragged = dragged == step.key
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = if (isDragged) DraggedElevation else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) offset else 0f }
                        .onSizeChanged { heights[step.key] = it.height }
                        .testTag(RecipeStepTags.step(index + 1)),
                ) {
                    Column {
                        StepRow(state, index, update, drag, onDurationClick = { pickerFor = step.key })
                        HorizontalDivider(
                            thickness = UmamiSpacing.Hairline,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
        if (form.canAddStep) {
            TextButton(
                onClick = { update { it.withAddedStep() } },
                modifier = Modifier
                    .padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XXSmall)
                    .testTag(RecipeStepTags.ADD),
            ) {
                Icon(painterResource(UmamiIcons.Add), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(UmamiSpacing.XSmall))
                Text(stringResource(R.string.recipe_step_add), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    val pickerIndex = form.steps.indexOfFirst { it.key == pickerFor }
    if (pickerIndex >= 0) {
        key(pickerFor) {
            DurationPicker(
                initialSeconds = form.steps[pickerIndex].durationSeconds,
                onApply = { seconds ->
                    update { it.withStepDurationSet(pickerIndex, seconds) }
                    pickerFor = null
                },
                onDismiss = { pickerFor = null },
            )
        }
    }
}

/** A step's drag: started on its handle, moved by the finger, ended on release. */
private interface StepDrag {
    fun start(key: Int)

    fun by(dy: Float)

    fun end()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepRow(
    state: RecipeEditorUiState.Ready,
    index: Int,
    update: ((RecipeForm) -> RecipeForm) -> Unit,
    drag: StepDrag,
    onDurationClick: () -> Unit,
) {
    val form = state.form
    val step = form.steps[index]
    val number = index + 1
    Column(
        modifier = Modifier.padding(horizontal = UmamiSpacing.Small, vertical = UmamiSpacing.XSmall),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(index, form.steps.size, step.key, update, drag)
            KindSelector(form, index, update, modifier = Modifier.weight(1f))
            IconButton(onClick = { update { it.withoutStep(index) } }, enabled = form.canDelete(index)) {
                Icon(painterResource(UmamiIcons.Close), stringResource(R.string.recipe_step_delete, number))
            }
        }
        FlowRow(
            modifier = Modifier.padding(start = HandleIndent),
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            Stepper(
                value = clockText(step.durationSeconds),
                valueDescription = stringResource(
                    R.string.recipe_step_duration_description,
                    number,
                    spokenDuration(step.durationSeconds),
                ),
                decreaseDescription = stringResource(R.string.recipe_step_shorter, number),
                increaseDescription = stringResource(R.string.recipe_step_longer, number),
                canStep = { up -> form.withStepDuration(index, up) != form },
                onStep = { up -> update { it.withStepDuration(index, up) } },
                onValueClick = onDurationClick,
            )
            val tempC = form.shownTempC(index)
            Stepper(
                value = temperatureLabel(tempC),
                valueDescription = pluralStringResource(R.plurals.recipe_editor_temperature_description, tempC, tempC),
                decreaseDescription = stringResource(R.string.recipe_step_cooler, number),
                increaseDescription = stringResource(R.string.recipe_step_warmer, number),
                canStep = { up -> form.withStepTemp(index, up) != form },
                onStep = { up -> update { it.withStepTemp(index, up) } },
            )
        }
        StepPreAlertRow(state, index, update)
        OutlinedTextField(
            value = step.note,
            onValueChange = { value -> update { it.withStepNote(index, value) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = HandleIndent, end = UmamiSpacing.Small)
                .testTag(RecipeStepTags.note(number)),
            label = { Text(stringResource(R.string.recipe_step_note)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

/**
 * The handle: dragged, the step follows the finger and takes a neighbour's place past its middle. TalkBack gets
 * „Verschieben, Schritt 2" with the actions *Nach oben verschieben* / *Nach unten verschieben* (03 §6).
 */
@Composable
private fun DragHandle(
    index: Int,
    count: Int,
    key: Int,
    update: ((RecipeForm) -> RecipeForm) -> Unit,
    drag: StepDrag,
) {
    val up = stringResource(R.string.recipe_step_move_up)
    val down = stringResource(R.string.recipe_step_move_down)
    val description = stringResource(R.string.recipe_step_move, index + 1)
    val currentDrag by rememberUpdatedState(drag)
    Box(
        modifier = Modifier
            .size(UmamiSpacing.MinTouchTarget)
            .testTag(RecipeStepTags.handle(index + 1))
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { currentDrag.start(key) },
                    onDragEnd = { currentDrag.end() },
                    onDragCancel = { currentDrag.end() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentDrag.by(amount.y)
                    },
                )
            }
            .clearAndSetSemantics {
                contentDescription = description
                customActions = buildList {
                    if (index > 0) {
                        add(
                            CustomAccessibilityAction(up) {
                                update { it.withStepMoved(index, index - 1) }
                                true
                            },
                        )
                    }
                    if (index < count - 1) {
                        add(
                            CustomAccessibilityAction(down) {
                                update { it.withStepMoved(index, index + 1) }
                                true
                            },
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(UmamiIcons.DragHandle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The step's number and kind; a tap opens *Aufguss* / *Spülen* / *Aufschlagen* (the only brewing step: no rinse). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSelector(
    form: RecipeForm,
    index: Int,
    update: ((RecipeForm) -> RecipeForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val number = index + 1
    val kind = stringResource(form.steps[index].kind.label)
    Box(modifier = modifier) {
        // TalkBack reads the button's text, „1 · Aufguss".
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(RecipeStepTags.kind(number))) {
            Text(
                text = stringResource(R.string.recipe_step_label, number, kind),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StepKind.entries.sortedBy { KIND_ORDER.indexOf(it) }.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        open = false
                        update { it.withStepKind(index, option) }
                    },
                    enabled = option != StepKind.RINSE || form.canBeRinse(index),
                    leadingIcon = { CheckMark(option == form.steps[index].kind) },
                )
            }
        }
    }
}

/** The step's Vorwarnung (SCR-31's selector, D-100): *Automatisch* with its lead, *Aus* or 5–30 s. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepPreAlertRow(
    state: RecipeEditorUiState.Ready,
    index: Int,
    update: ((RecipeForm) -> RecipeForm) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val number = index + 1
    val selected = state.form.steps[index].preAlertSeconds
    val shown = stepPreAlertLabel(state.stepPreAlert(index))
    val description = stringResource(R.string.recipe_step_pre_alert_description, number, shown)
    // At large font scales the selector moves below its label rather than squeezing beside it.
    FlowRow(modifier = Modifier.padding(start = HandleIndent), itemVerticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.recipe_step_pre_alert),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = UmamiSpacing.XSmall),
        )
        Box {
            TextButton(
                onClick = { open = true },
                modifier = Modifier
                    .testTag(RecipeStepTags.preAlert(number))
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick {
                            open = true
                            true
                        }
                    },
            ) {
                Text(shown, style = MaterialTheme.typography.labelLarge)
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = open)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                PreAlertChoices.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(stepPreAlertLabel(state.stepPreAlert(index, choice), spelledOut = true)) },
                        onClick = {
                            open = false
                            update { it.withStepPreAlert(index, choice) }
                        },
                        leadingIcon = { CheckMark(choice == selected) },
                    )
                }
            }
        }
    }
}

/**
 * A value between − and + that repeat while held ([RepeatingIconButton], as the Schritte-Sheet's); with
 * [onValueClick] the value is a button too (the duration opens „Dauer wählen"). [canStep] says whether a step up or
 * down changes anything (at a bound the button is greyed out and a hold stops); [onStep] applies one.
 */
@Suppress("LongParameterList") // a stepper's parts
@Composable
internal fun Stepper(
    value: String,
    valueDescription: String,
    decreaseDescription: String,
    increaseDescription: String,
    canStep: (up: Boolean) -> Boolean,
    onStep: (up: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onValueClick: (() -> Unit)? = null,
) {
    val canDecrease = canStep(false)
    val canIncrease = canStep(true)
    Surface(
        modifier = modifier,
        shape = UmamiShape.Pill,
        border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingIconButton(
                icon = painterResource(UmamiIcons.Remove),
                contentDescription = decreaseDescription,
                enabled = canDecrease,
                onStep = { canDecrease.also { if (it) onStep(false) } },
            )
            Box(
                modifier = Modifier
                    .heightIn(min = UmamiSpacing.MinTouchTarget)
                    .widthIn(min = 56.dp)
                    .then(
                        if (onValueClick ==
                            null
                        ) {
                            Modifier
                        } else {
                            Modifier.clickable(role = Role.Button) { onValueClick() }
                        },
                    )
                    .semantics {
                        contentDescription = valueDescription
                        liveRegion = LiveRegionMode.Polite
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "lnum, tnum"),
                    textAlign = TextAlign.Center,
                )
            }
            RepeatingIconButton(
                icon = painterResource(UmamiIcons.Add),
                contentDescription = increaseDescription,
                enabled = canIncrease,
                onStep = { canIncrease.also { if (it) onStep(true) } },
            )
        }
    }
}

/** „70 °C", or „10 °C · kalt" at 10 °C and below (PRF-03). */
@Composable
internal fun temperatureLabel(tempC: Int): String = if (tempC <= RecipeForm.COLD_UP_TO_C) {
    stringResource(R.string.recipe_editor_temperature_cold, tempC)
} else {
    stringResource(R.string.recipe_editor_temperature_value, tempC)
}

@Composable
private fun CheckMark(checked: Boolean) {
    if (checked) {
        Icon(painterResource(UmamiIcons.Check), contentDescription = null)
    } else {
        Spacer(Modifier.size(24.dp))
    }
}

/** The kinds in the menu: *Aufguss*, *Spülen*, *Aufschlagen*. */
private val KIND_ORDER = listOf(StepKind.INFUSION, StepKind.RINSE, StepKind.WHISK)

/** The rows below a step's handle line up with its kind. */
private val HandleIndent = UmamiSpacing.MinTouchTarget

private val DraggedElevation = 6.dp
