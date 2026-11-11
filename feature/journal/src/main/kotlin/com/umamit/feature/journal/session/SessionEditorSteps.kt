package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.StepKind
import com.umamit.core.ui.RepeatingIconButton
import com.umamit.feature.journal.R

// SCR-17's SCHRITTE rows (JRN-11): the brewed steps' *Zeit* and *°C*, the others greyed.

/** „1 · Aufguss" with *Zeit* and *°C* when brewed; greyed with „übersprungen" or „nicht gebrüht" otherwise. */
@Composable
internal fun StepRow(row: SessionEditorStepRow, actions: SessionEditorActions) {
    val step = row.form
    Column(modifier = EditorFieldModifier.padding(vertical = UmamiSpacing.XXSmall)) {
        if (step == null) {
            val status = stringResource(if (row.skipped) R.string.session_skipped else R.string.session_not_brewed)
            Text(
                text = stringResource(R.string.session_editor_step_greyed, stepLabel(row), status),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GREYED_ALPHA),
            )
            return@Column
        }
        Text(
            text = stepLabel(row),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            OutlinedTextField(
                value = step.time,
                onValueChange = { text ->
                    val cleaned = text.filter { it.isDigit() || it == ':' }.take(TIME_CHARS)
                    actions.update { form -> form.withStep(row.position) { it.copy(time = cleaned) } }
                },
                label = { Text(stringResource(R.string.session_editor_step_time)) },
                singleLine = true,
                isError = row.timeInvalid,
                supportingText = if (row.timeInvalid) {
                    { Text(stringResource(R.string.session_editor_error_step_time)) }
                } else {
                    null
                },
                // Ascii, not Number: number pads (Samsung's, Gboard's) have no „:" key for m:ss.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = TIME_FIELD_MIN_WIDTH)
                    .testTag(SessionEditorTags.STEP_TIME + row.position),
            )
            TempStepper(row.position, step, actions)
        }
    }
}

/** *°C* in 5 °C steps within the Schritte-Sheet's 5–100 °C (GEN-12). */
@Composable
private fun TempStepper(position: Int, step: SessionEditorStepForm, actions: SessionEditorActions) {
    val number = position + 1
    val canCool = step.withTempStep(up = false) != step
    val canWarm = step.withTempStep(up = true) != step
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(SessionEditorTags.STEP_TEMP + position),
    ) {
        RepeatingIconButton(
            icon = painterResource(UmamiIcons.Remove),
            contentDescription = stringResource(R.string.session_editor_step_cooler, number),
            enabled = canCool,
            onStep = {
                canCool.also {
                    if (it) actions.update { f -> f.withStep(position) { s -> s.withTempStep(up = false) } }
                }
            },
        )
        val label = stringResource(R.string.session_temperature, step.tempC)
        Box(
            modifier = Modifier
                .heightIn(min = UmamiSpacing.MinTouchTarget)
                .widthIn(min = TEMP_MIN_WIDTH)
                .clearAndSetSemantics {
                    contentDescription = label
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge.tabular())
        }
        RepeatingIconButton(
            icon = painterResource(UmamiIcons.Add),
            contentDescription = stringResource(R.string.session_editor_step_warmer, number),
            enabled = canWarm,
            onStep = {
                canWarm.also { if (it) actions.update { f -> f.withStep(position) { s -> s.withTempStep(up = true) } } }
            },
        )
    }
}

/** „1 · Aufguss", „2 · Spülen", „3 · Aufschlagen": the step's place in the session and its kind. */
@Composable
@ReadOnlyComposable
private fun stepLabel(row: SessionEditorStepRow): String = stringResource(
    R.string.session_editor_step,
    row.position + 1,
    stringResource(
        when (row.kind) {
            StepKind.INFUSION -> R.string.session_editor_kind_infusion
            StepKind.RINSE -> R.string.session_step_rinse
            StepKind.WHISK -> R.string.session_step_whisk
        },
    ),
)

private const val GREYED_ALPHA = 0.7f
private const val TIME_CHARS = 8
private val TIME_FIELD_MIN_WIDTH = 96.dp
private val TEMP_MIN_WIDTH = 64.dp
