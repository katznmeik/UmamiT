package com.umamit.feature.brew.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.FinishActiveSessionDialog
import com.umamit.core.designsystem.component.KettleHint
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.feature.brew.R
import com.umamit.feature.brew.steps.SaveToRecipeRequest
import com.umamit.feature.brew.steps.StepsSheet
import com.umamit.feature.brew.steps.StepsSheetInput
import com.umamit.feature.brew.steps.StepsSheetStep
import com.umamit.feature.brew.steps.rememberStepsSheetState
import com.umamit.feature.brew.timer.label
import java.util.Locale

// The lower half of the setup (SCR-04): steps, pre-alert and the buttons.

@Composable
internal fun StepsSection(state: BrewSetupUiState.Ready, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionHeader(stringResource(R.string.brew_setup_steps), Modifier.weight(1f))
        val description = stringResource(R.string.brew_setup_steps_edit_description)
        TextButton(
            onClick = onEdit,
            modifier = Modifier
                .padding(end = UmamiSpacing.Small)
                .testTag(SETUP_STEPS_EDIT_TAG)
                .semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.brew_setup_steps_edit))
        }
    }
    state.steps.forEachIndexed { i, step ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XXSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${i + 1}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(UmamiSpacing.Large),
            )
            Text(
                text = stringResource(step.kind.label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = clockText(step.plannedSeconds),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(UmamiSpacing.Medium))
            Text(
                text = step.tempC?.let { stringResource(R.string.brew_steps_temperature, it) }.orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = UmamiSpacing.XXLarge),
            )
        }
    }
}

@Composable
internal fun PreAlertRow(state: BrewSetupUiState.Ready, onClick: () -> Unit) {
    val seconds = state.effectivePreAlertSeconds
    val value = if (seconds == 0) {
        stringResource(R.string.brew_setup_pre_alert_off)
    } else {
        stringResource(R.string.brew_setup_pre_alert_seconds, seconds)
    }
    val shown = if (state.preAlertIsDefault) stringResource(R.string.brew_setup_pre_alert_default, value) else value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(SETUP_PRE_ALERT_TAG)
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(stringResource(R.string.brew_setup_pre_alert))
        Text(shown, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun SetupButtons(state: BrewSetupUiState.Ready, actions: BrewSetupActions) {
    val motion = UmamiTheme.motion
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium),
    ) {
        // Grows from its top edge, so the start button below glides down instead of jumping (03 §3.6).
        AnimatedVisibility(
            visible = state.canSaveToRecipe,
            enter = fadeIn(motion.fade()) + expandVertically(motion.standard(), expandFrom = Alignment.Top),
            exit = fadeOut(motion.exitFade()) + shrinkVertically(motion.exit(), shrinkTowards = Alignment.Top),
        ) {
            OutlinedButton(
                onClick = actions.saveToRecipe,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = UmamiSpacing.Small)
                    .testTag(SETUP_SAVE_TAG),
            ) {
                Text(stringResource(R.string.brew_setup_save_to_recipe))
            }
        }
        PillButton(
            text = stringResource(R.string.brew_setup_start),
            onClick = actions.start,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SETUP_START_TAG),
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
    }
}
