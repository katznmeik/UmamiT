package com.umamit.feature.brew.steps

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiShape
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.StepKind
import com.umamit.core.timer.engine.PreAlertChoice
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R
import kotlinx.coroutines.launch

/** The pre-alert options of the sheet (SCR-31): *Wie Einstellung*, *Aus*, 5–30 s in 5 s steps. */
val PreAlertOptions: List<PreAlertChoice> =
    listOf(PreAlertChoice.Inherit, PreAlertChoice.Off) + (5..30 step 5).map { PreAlertChoice.Seconds(it) }

/**
 * The content of the Schritte-Sheet: the plan, *Aufguss hinzufügen*, the
 * pre-alert selector, *Im Rezept speichern* (only with a recipe) and *Fertig*.
 * Stateless apart from the snackbar: the edits live in [state].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepsSheetContent(
    state: StepsSheetState,
    onDone: (TimerEvent.EditSteps) -> Unit,
    onSaveToRecipe: (SaveToRecipeRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.brew_steps_saved_to_recipe)
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.confirmationPending) {
        // A one-shot event: consumed as it shows, so a reopened sheet never replays it.
        if (state.confirmationPending) {
            state.confirmationShown()
            scope.launch { snackbarHostState.showSnackbar(savedMessage) }
        }
    }
    val scrollState = rememberScrollState()
    val reduceMotion = UmamiTheme.reduceMotion
    val motion = UmamiTheme.motion
    Column(modifier = modifier.fillMaxWidth().padding(bottom = UmamiSpacing.Medium)) {
        Text(
            text = stringResource(R.string.brew_steps_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)
                .semantics { heading() },
        )
        // The snackbar overlays the end of the list, never the buttons below it.
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Column(
                modifier = Modifier
                    .verticalScroll(scrollState)
                    .padding(horizontal = UmamiSpacing.SideMargin),
            ) {
                state.steps.forEachIndexed { index, _ ->
                    if (state.isEditable(index)) EditableStepRow(state, index) else ReadOnlyStepRow(state, index)
                    HorizontalDivider(
                        thickness = UmamiSpacing.Hairline,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                TextButton(
                    onClick = {
                        state.addStep()
                        // Show the new step, which may land below the fold.
                        scope.launch {
                            scrollState.scrollToEnd(reduceMotion, motion.standard())
                        }
                    },
                    modifier = Modifier.padding(vertical = UmamiSpacing.XXSmall),
                ) {
                    Icon(painterResource(UmamiIcons.Add), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(UmamiSpacing.XSmall))
                    Text(stringResource(R.string.brew_steps_add), style = MaterialTheme.typography.labelLarge)
                }
                PreAlertRow(selected = state.preAlert, onSelect = { state.preAlert = it })
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Column(
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin).padding(top = UmamiSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            if (state.hasRecipe) {
                OutlinedButton(
                    onClick = { onSaveToRecipe(state.saveRequest()) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget),
                    shape = UmamiShape.Pill,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outline),
                ) {
                    Text(stringResource(R.string.brew_steps_save_to_recipe), textAlign = TextAlign.Center)
                }
            }
            PillButton(
                text = stringResource(R.string.brew_steps_done),
                onClick = { onDone(state.editRequest()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Scrolls to the end once the new content is measured; instant under reduce motion. */
private suspend fun ScrollState.scrollToEnd(reduceMotion: Boolean, spec: AnimationSpec<Float>) {
    // The tap changes the state; the new row is composed and measured in the next frame.
    repeat(2) { withFrameNanos {} }
    if (reduceMotion) scrollTo(maxValue) else animateScrollTo(maxValue, spec)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableStepRow(state: StepsSheetState, index: Int) {
    val step = state.steps[index]
    val number = index + 1
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = UmamiSpacing.XSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.brew_steps_step_label, number, kindLabel(step.kind)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { state.delete(index) }, enabled = state.canDelete(index)) {
                Icon(
                    painter = painterResource(UmamiIcons.Close),
                    contentDescription = stringResource(R.string.brew_steps_delete, number),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            Stepper(
                value = clockText(step.plannedSeconds),
                valueDescription = stringResource(
                    R.string.brew_steps_duration_value_description,
                    spokenDuration(step.plannedSeconds),
                ),
                decreaseDescription = stringResource(R.string.brew_steps_shorter, number),
                increaseDescription = stringResource(R.string.brew_steps_longer, number),
                canDecrease = state.canEdit(index, StepEdit.SHORTEN),
                canIncrease = state.canEdit(index, StepEdit.LENGTHEN),
                onDecrease = { state.edit(index, StepEdit.SHORTEN) },
                onIncrease = { state.edit(index, StepEdit.LENGTHEN) },
            )
            // A step without any temperature (quick timer) has no temperature stepper.
            val tempC = state.shownTempC(index) ?: return@FlowRow
            Stepper(
                value = stringResource(R.string.brew_steps_temperature, tempC),
                valueDescription = stringResource(
                    R.string.brew_steps_temperature_value_description,
                    pluralStringResource(R.plurals.brew_steps_temperature_description, tempC, tempC),
                ),
                decreaseDescription = stringResource(R.string.brew_steps_cooler, number),
                increaseDescription = stringResource(R.string.brew_steps_warmer, number),
                canDecrease = state.canEdit(index, StepEdit.COOL),
                canIncrease = state.canEdit(index, StepEdit.WARM),
                onDecrease = { state.edit(index, StepEdit.COOL) },
                onIncrease = { state.edit(index, StepEdit.WARM) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadOnlyStepRow(state: StepsSheetState, index: Int) {
    val step = state.steps[index]
    val number = index + 1
    val kind = kindLabel(step.kind)
    val status = when (step.status) {
        StepStatus.DONE -> stringResource(R.string.brew_steps_status_done)
        StepStatus.STARTED -> stringResource(R.string.brew_steps_status_running)
        StepStatus.SKIPPED -> stringResource(R.string.brew_steps_status_skipped)
        StepStatus.PENDING -> null
    }
    val tempC = state.shownTempC(index)
    val description = if (tempC == null) {
        stringResource(
            R.string.brew_steps_step_description_no_temperature,
            number,
            kind,
            status.orEmpty(),
            spokenDuration(step.plannedSeconds),
        )
    } else {
        stringResource(
            R.string.brew_steps_step_description,
            number,
            kind,
            status.orEmpty(),
            spokenDuration(step.plannedSeconds),
            pluralStringResource(R.plurals.brew_steps_temperature_description, tempC, tempC),
        )
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = UmamiSpacing.Small)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (step.status == StepStatus.DONE) {
            Icon(
                painter = painterResource(UmamiIcons.Check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
        Text(
            text = stringResource(R.string.brew_steps_step_label, number, kind),
            style = MaterialTheme.typography.titleMedium,
            color = muted,
        )
        if (status != null && step.status != StepStatus.DONE) {
            Text(text = status, style = MaterialTheme.typography.labelMedium, color = muted)
        }
        Text(text = clockText(step.plannedSeconds), style = MaterialTheme.typography.bodyLarge.tabular(), color = muted)
        if (tempC != null) {
            Text(
                text = stringResource(R.string.brew_steps_temperature, tempC),
                style = MaterialTheme.typography.bodyLarge.tabular(),
                color = muted,
            )
        }
    }
}

/** A value between − and + buttons, each 48 dp, with German TalkBack labels (03 §6). */
@Composable
private fun Stepper(
    value: String,
    valueDescription: String,
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Surface(
        shape = UmamiShape.Pill,
        border = BorderStroke(UmamiSpacing.Hairline, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease, enabled = canDecrease) {
                Icon(painterResource(UmamiIcons.Remove), contentDescription = decreaseDescription)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.tabular(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .semantics {
                        contentDescription = valueDescription
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            IconButton(onClick = onIncrease, enabled = canIncrease) {
                Icon(painterResource(UmamiIcons.Add), contentDescription = increaseDescription)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreAlertRow(selected: PreAlertChoice, onSelect: (PreAlertChoice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = preAlertLabel(selected)
    val description = stringResource(R.string.brew_steps_pre_alert_description, preAlertSpoken(selected))
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = UmamiSpacing.XSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.brew_steps_pre_alert),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = UmamiSpacing.XSmall),
        )
        Box {
            TextButton(
                onClick = { expanded = true },
                // One TalkBack element with the spoken value, a button role and a click action.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick {
                        expanded = true
                        true
                    }
                },
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(UmamiSpacing.XXSmall))
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                PreAlertOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(preAlertLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                        leadingIcon = {
                            if (option == selected) {
                                Icon(painterResource(UmamiIcons.Check), contentDescription = null)
                            } else {
                                Spacer(Modifier.size(24.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: StepKind): String = stringResource(
    when (kind) {
        StepKind.INFUSION -> R.string.brew_steps_kind_infusion
        StepKind.RINSE -> R.string.brew_steps_kind_rinse
        StepKind.WHISK -> R.string.brew_steps_kind_whisk
    },
)

@Composable
private fun preAlertLabel(choice: PreAlertChoice): String = when (choice) {
    PreAlertChoice.Inherit -> stringResource(R.string.brew_steps_pre_alert_inherit)
    PreAlertChoice.Off -> stringResource(R.string.brew_steps_pre_alert_off)
    is PreAlertChoice.Seconds -> stringResource(R.string.brew_steps_pre_alert_seconds, choice.seconds)
}

@Composable
private fun preAlertSpoken(choice: PreAlertChoice): String = when (choice) {
    is PreAlertChoice.Seconds ->
        pluralStringResource(R.plurals.brew_steps_pre_alert_seconds_description, choice.seconds, choice.seconds)

    else -> preAlertLabel(choice)
}

/** Tabular lining figures, so the stepper values never jitter (03 §3.2). */
private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "lnum, tnum")
