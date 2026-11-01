package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.StepKind
import com.umamit.feature.journal.R

/** From this font scale on the step table gives way to one block per step, so no column breaks a word (03 §6). */
private const val STACKED_STEPS_FONT_SCALE = 1.5f

/**
 * The step table of the session page (SCR-16): *geplant* / *tatsächlich* / °C with each step's kind; from font scale
 * 1,5 one block per step. Each row is one TalkBack node with the durations in words.
 */
@Composable
internal fun SessionSteps(steps: List<SessionPageStep>) {
    if (LocalDensity.current.fontScale >= STACKED_STEPS_FONT_SCALE) {
        steps.forEachIndexed { index, step -> StackedStep(step, showDivider = index < steps.lastIndex) }
    } else {
        StepTableHeader()
        steps.forEachIndexed { index, step -> StepTableRow(step, showDivider = index < steps.lastIndex) }
    }
}

@Composable
private fun StepTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
    ) {
        val style = MaterialTheme.typography.labelMedium
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(stringResource(R.string.session_column_step), Modifier.weight(STEP_WEIGHT), color, style = style)
        Text(
            stringResource(R.string.session_column_planned),
            Modifier.weight(TIME_WEIGHT),
            color,
            style = style,
            textAlign = TextAlign.End,
        )
        Text(
            stringResource(R.string.session_column_actual),
            Modifier.weight(ACTUAL_WEIGHT),
            color,
            style = style,
            textAlign = TextAlign.End,
        )
        Text(
            stringResource(R.string.session_column_temperature),
            Modifier.weight(TEMP_WEIGHT),
            color,
            style = style,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun StepTableRow(step: SessionPageStep, showDivider: Boolean) {
    val description = stepDescription(step)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description }
                .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val style = MaterialTheme.typography.bodyLarge.tabular()
            val color = stepColor(step)
            Text(stepLabel(step), Modifier.weight(STEP_WEIGHT), color, style = style)
            Text(
                clockText(step.plannedSeconds),
                Modifier.weight(TIME_WEIGHT),
                color,
                style = style,
                textAlign = TextAlign.End,
            )
            Text(
                text = actualText(step),
                modifier = Modifier.weight(ACTUAL_WEIGHT),
                color = color,
                style = if (step.brewed && !step.skipped) style else style.copy(fontStyle = FontStyle.Italic),
                textAlign = TextAlign.End,
            )
            Text(
                text = step.tempC?.toString() ?: unknown(),
                modifier = Modifier.weight(TEMP_WEIGHT),
                color = color,
                style = style,
                textAlign = TextAlign.End,
            )
        }
        if (showDivider) StepDivider()
    }
}

/** A step under a large font: its kind and temperature, then its times, wrapping as needed. */
@Composable
private fun StackedStep(step: SessionPageStep, showDivider: Boolean) {
    val description = stepDescription(step)
    val color = stepColor(step)
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description }
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        ) {
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stepLabel(step), color = color, style = MaterialTheme.typography.titleMedium)
                step.tempC?.let {
                    Text(
                        stringResource(R.string.session_temperature, it),
                        color = color,
                        style = MaterialTheme.typography.bodyLarge.tabular(),
                    )
                }
            }
            Text(
                text = listOf(
                    stringResource(R.string.session_planned_value, clockText(step.plannedSeconds)),
                    if (step.brewed && !step.skipped) {
                        stringResource(R.string.session_actual_value, actualText(step))
                    } else {
                        actualText(step)
                    },
                ).joinToString(" · "),
                color = color,
                style = MaterialTheme.typography.bodyMedium.tabular(),
            )
        }
        if (showDivider) StepDivider()
    }
}

@Composable
private fun StepDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        thickness = UmamiSpacing.Hairline,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
@ReadOnlyComposable
private fun stepColor(step: SessionPageStep) = if (step.brewed) {
    MaterialTheme.colorScheme.onSurface
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

/** „Aufguss 2", „Spülen", „Aufschlagen". */
@Composable
@ReadOnlyComposable
private fun stepLabel(step: SessionPageStep): String = when (step.kind) {
    StepKind.INFUSION -> stringResource(R.string.session_step_infusion, step.infusionNumber ?: 1)
    StepKind.RINSE -> stringResource(R.string.session_step_rinse)
    StepKind.WHISK -> stringResource(R.string.session_step_whisk)
}

/** The measured time „1:35", „übersprungen" or „nicht gebrüht" (Q-19). */
@Composable
@ReadOnlyComposable
private fun actualText(step: SessionPageStep): String = when {
    step.actualSeconds == null -> stringResource(R.string.session_not_brewed)
    step.skipped -> stringResource(R.string.session_skipped)
    else -> clockText(step.actualSeconds)
}

/** TalkBack: „Aufguss 2: geplant 1 Minute 15 Sekunden, tatsächlich 1 Minute 35 Sekunden, 65 °C". */
@Composable
@ReadOnlyComposable
private fun stepDescription(step: SessionPageStep): String {
    val actual = step.actualSeconds
    val parts = listOfNotNull(
        stringResource(R.string.session_planned_value, spokenDuration(step.plannedSeconds)),
        if (actual != null && !step.skipped) {
            stringResource(R.string.session_actual_value, spokenDuration(actual))
        } else {
            actualText(step)
        },
        step.tempC?.let { stringResource(R.string.session_temperature, it) },
    )
    return stringResource(R.string.session_step_description, stepLabel(step), parts.joinToString(", "))
}

/** Tabular lining figures, so the times line up (03 §3.2). */
internal fun TextStyle.tabular() = copy(fontFeatureSettings = "lnum, tnum")

private const val STEP_WEIGHT = 1.3f
private const val TIME_WEIGHT = 1f
private const val ACTUAL_WEIGHT = 1.3f
private const val TEMP_WEIGHT = 0.6f
