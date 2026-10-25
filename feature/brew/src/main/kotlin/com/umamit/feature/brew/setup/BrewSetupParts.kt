package com.umamit.feature.brew.setup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.R
import com.umamit.feature.brew.timer.label

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
        // Once saved, the slot keeps its height and shows „Im Rezept gespeichert", so *Brühen starten* never
        // moves under a finger that taps *Im Rezept speichern* twice (D-058, D-062). It grows from its top
        // edge only when a change makes it appear, far from the start button (03 §3.6).
        var saved by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(state.canSaveToRecipe) { if (state.canSaveToRecipe) saved = false }
        AnimatedVisibility(
            visible = state.canSaveToRecipe || saved,
            enter = fadeIn(motion.fade()) + expandVertically(motion.standard(), expandFrom = Alignment.Top),
            exit = fadeOut(motion.exitFade()) + shrinkVertically(motion.exit(), shrinkTowards = Alignment.Top),
        ) {
            Crossfade(
                targetState = state.canSaveToRecipe,
                animationSpec = motion.fade(),
                label = "saveToRecipe",
                modifier = Modifier.padding(bottom = UmamiSpacing.Small),
            ) { canSave ->
                if (canSave) {
                    OutlinedButton(
                        onClick = {
                            saved = true
                            actions.saveToRecipe()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(SETUP_SAVE_TAG),
                    ) {
                        Text(stringResource(R.string.brew_setup_save_to_recipe))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                            .testTag(SETUP_SAVED_TAG)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.brew_setup_saved_to_recipe),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
