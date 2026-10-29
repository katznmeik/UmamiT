package com.umamit.feature.brew.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.ui.RecipeCheckNote
import com.umamit.feature.brew.R
import com.umamit.feature.brew.steps.SaveToRecipeRequest
import com.umamit.feature.brew.steps.StepsSheet
import com.umamit.feature.brew.steps.StepsSheetInput
import com.umamit.feature.brew.steps.StepsSheetStep
import com.umamit.feature.brew.steps.rememberStepsSheetState
import com.umamit.feature.brew.timer.label
import java.util.Locale

const val SETUP_START_TAG = "setup_start"
const val SETUP_SAVED_TAG = "setup_saved_to_recipe"
const val SETUP_SAVE_TAG = "setup_save_to_recipe"
const val SETUP_STEPS_EDIT_TAG = "setup_steps_edit"
const val SETUP_PRE_ALERT_TAG = "setup_pre_alert"
const val SETUP_RECIPE_CHECK_TAG = "setup_recipe_check"

/**
 * The setup (SCR-04, BRW-02): tea, recipe chips, vessel, water and leaf with
 * the ratio lock (BRW-03), the kettle hint (BRW-10), „Rezept prüfen" with its
 * reason for a flagged tea (CAT-05), the steps, the effective
 * pre-alert, *Im Rezept speichern* (only after a change) and *Brühen starten*.
 * *Bearb.* and the *Vorwarnung* row open the Schritte-Sheet (SCR-31) for this
 * session.
 *
 * **Alerts first (rule 8):** the sheet remembers the alert it was opened in
 * and is removed in the same frame, without animation or question, when
 * another alert starts; the second-session question is closed by the view
 * model.
 */
@Composable
fun BrewSetupScreen(
    state: BrewSetupUiState,
    modifier: Modifier = Modifier,
    actions: BrewSetupActions = BrewSetupActions(),
) {
    ScreenScaffold(
        title = stringResource(R.string.brew_setup_title),
        modifier = modifier,
        navigationIcon = { BackAction(actions.back) },
        verticalArrangement = if (state is BrewSetupUiState.Ready) Arrangement.Top else Arrangement.Center,
    ) {
        when (state) {
            BrewSetupUiState.Loading -> Unit

            BrewSetupUiState.NotFound -> EmptyState(
                title = stringResource(R.string.brew_setup_not_found_title),
                body = stringResource(R.string.brew_setup_not_found_body),
            )

            is BrewSetupUiState.Ready -> SetupContent(state, actions)
        }
    }
}

@Composable
private fun SetupContent(state: BrewSetupUiState.Ready, actions: BrewSetupActions) {
    var stepsOpen by rememberSaveable { mutableStateOf(false) }
    var stepsOpenedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val stepsCovered = state.alertKey != null && state.alertKey != stepsOpenedIn
    val openSteps = {
        stepsOpenedIn = state.alertKey
        stepsOpen = true
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        TeaHeader(state)
        RecipeChips(state, actions.selectRecipe)
        VesselRow(state, actions.selectVessel)
        AmountRow(
            label = stringResource(R.string.brew_setup_water),
            value = stringResource(R.string.brew_setup_ml, state.waterMl),
            less = stringResource(R.string.brew_setup_water_less),
            more = stringResource(R.string.brew_setup_water_more),
            onStep = actions.stepWater,
        )
        AmountRow(
            label = stringResource(R.string.brew_setup_leaf),
            value = stringResource(R.string.brew_setup_grams, grams(state.leafGrams)),
            less = stringResource(R.string.brew_setup_leaf_less),
            more = stringResource(R.string.brew_setup_leaf_more),
            onStep = actions.stepLeaf,
        )
        RatioRow(state, actions.setRatioLocked)
        KettleHint(
            temperatureCelsius = state.kettle.tempC,
            water = state.kettle.water,
            modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.Medium),
        )
        // „Rezept prüfen" with its reason, above the steps it is about (CAT-05; 03 SCR-04).
        state.recipeCheckReason?.let { reason ->
            RecipeCheckNote(
                reason = reason,
                modifier = Modifier
                    .padding(horizontal = UmamiSpacing.SideMargin)
                    .padding(bottom = UmamiSpacing.Small)
                    .testTag(SETUP_RECIPE_CHECK_TAG),
            )
        }
        StepsSection(state, onEdit = openSteps)
        PreAlertRow(state, onClick = openSteps)
        SetupButtons(state, actions)
    }
    val input = StepsSheetInput(
        steps = state.steps.map {
            StepsSheetStep(it.kind, it.plannedSeconds, it.tempC, StepStatus.PENDING, it.preAlertSeconds)
        },
        hasRecipe = state.selectedRecipeId != null,
        baseTempC = state.baseTempC,
        // Each step's „Auto · 5 s" from the B this session will start with (TMR-23, D-093 Q17).
        preAlertBaseSeconds = state.preAlertBaseSeconds,
        longStepsOnly = BrewTimerEngine.longStepsOnly(state.method),
    )
    StepsSheet(
        visible = stepsOpen && !stepsCovered,
        state = rememberStepsSheetState(input),
        onDone = {
            actions.applySteps(it)
            stepsOpen = false
        },
        onSaveToRecipe = actions.saveStepsToRecipe,
        onDismissRequest = { stepsOpen = false },
        alerting = stepsCovered,
    )
    // A new alert closes the sheet for good; it was already removed in the frame the alert started.
    LaunchedEffect(stepsCovered) { if (stepsCovered) stepsOpen = false }
    // Alerts first (rule 8): never over an alert, by construction (the view model also closes it).
    val question = state.question?.takeIf { state.alertKey == null }
    if (question != null) {
        FinishActiveSessionDialog(
            teaName = question.teaName,
            onRestart = actions.confirmRestart,
            onCancel = actions.dismissQuestion,
        )
    }
}

@Composable
private fun TeaHeader(state: BrewSetupUiState.Ready) {
    Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)) {
        Text(
            text = state.teaName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val meta = listOfNotNull(
            state.secondaryName,
            state.stockGrams?.let { stringResource(R.string.brew_setup_stock, grams(it)) },
        ).joinToString(stringResource(R.string.brew_setup_meta_separator))
        if (meta.isNotEmpty()) {
            Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeChips(state: BrewSetupUiState.Ready, onSelect: (String) -> Unit) {
    if (state.recipes.isEmpty()) return
    FlowRow(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        state.recipes.forEach { recipe ->
            val selected = recipe.id == state.selectedRecipeId
            FilterChip(
                selected = selected,
                onClick = { onSelect(recipe.id) },
                label = { Text(recipe.name) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(UmamiIcons.Check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun VesselRow(state: BrewSetupUiState.Ready, onSelect: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = state.vessels.firstOrNull { it.id == state.selectedVesselId }
    val name = selected?.name ?: stringResource(R.string.brew_setup_vessel_none)
    val description = stringResource(R.string.brew_setup_vessel_description, name)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.DropdownList, enabled = state.vessels.isNotEmpty()) { open = true }
                .semantics(mergeDescendants = true) { contentDescription = description }
                .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel(stringResource(R.string.brew_setup_vessel))
            Text(
                text = selected?.let { stringResource(R.string.brew_setup_vessel_option, it.name, it.brewMl) } ?: name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.vessels.forEach { vessel ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.brew_setup_vessel_option, vessel.name, vessel.brewMl)) },
                    onClick = {
                        open = false
                        onSelect(vessel.id)
                    },
                )
            }
        }
    }
}

@Composable
internal fun RowLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(min = LABEL_MIN_WIDTH)
            .padding(end = UmamiSpacing.Small),
    )
}

private val LABEL_MIN_WIDTH = UmamiSpacing.XXLarge * 2

/** A value with − and + steppers (BRW-03). */
@Composable
private fun AmountRow(label: String, value: String, less: String, more: String, onStep: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label and value are one node, and a stepper's new value is announced (03 §6).
        Row(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowLabel(label)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = { onStep(false) }) {
            Icon(painterResource(UmamiIcons.Remove), contentDescription = less)
        }
        IconButton(onClick = { onStep(true) }) {
            Icon(painterResource(UmamiIcons.Add), contentDescription = more)
        }
    }
}

@Composable
private fun RatioRow(state: BrewSetupUiState.Ready, onLock: (Boolean) -> Unit) {
    val lock = stringResource(R.string.brew_setup_ratio_lock)
    val ratio = stringResource(R.string.brew_setup_ratio, grams(state.ratioPer100Ml))
    // One switch node: TalkBack reads the label and the ratio, then „ein" or „aus" (03 §6).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = state.ratioLocked, role = Role.Switch, onValueChange = onLock)
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .padding(horizontal = UmamiSpacing.SideMargin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(lock, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(ratio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.ratioLocked, onCheckedChange = null)
    }
}

/** „4,0" (German decimal comma, 05 §7). */
internal fun grams(value: Double): String = "%.1f".format(Locale.GERMAN, value)
