package com.umamit.feature.brew.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.umamit.core.common.stockGramsText
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
import com.umamit.core.model.StockLevel
import com.umamit.core.timer.engine.BrewTimerEngine
import com.umamit.core.timer.engine.StepStatus
import com.umamit.core.timer.engine.TimerEvent
import com.umamit.core.ui.RecipeCheckNote
import com.umamit.core.ui.StockSheet
import com.umamit.feature.brew.R
import com.umamit.feature.brew.picker.AlertGate
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
const val SETUP_STOCK_TAG = "setup_stock"

/**
 * The setup (SCR-04, BRW-02): tea, recipe chips, vessel, water and leaf with
 * the ratio lock (BRW-03), the kettle hint (BRW-10), „Rezept prüfen" with its
 * reason for a flagged tea (CAT-05), the steps, the effective
 * pre-alert, *Im Rezept speichern* (only after a change) and *Brühen starten*.
 * *Bearb.* and the *Vorwarnung* row open the Schritte-Sheet (SCR-31) for this
 * session.
 *
 * The top bar's ⋮ has *Tee ansehen* (D-100) and, for a tea with a catalog entry, *Im Katalog ansehen* (D-098).
 * The *Gefäß* list ends with *Neues Gefäß …* (WARE-02); a recipe's archived vessel reads „(archiviert)". The
 * stock line warns „Nur noch 3 g übrig" below this brew's leaf (BRW-08).
 *
 * **Alerts first (rule 8):** the menus and the sheets remember the alert they were opened in
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
        actions = {
            if (state is BrewSetupUiState.Ready) {
                val key = state.catalogKey
                SetupMenu(
                    alertKey = state.alertKey,
                    onOpenTea = { actions.openTea(state.teaId) },
                    onOpenCatalog = key?.let { { actions.openCatalog(it) } },
                )
            }
        },
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
    var stockOpen by rememberSaveable { mutableStateOf(false) }
    var stockOpenedIn by rememberSaveable { mutableStateOf<String?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        TeaHeader(
            state,
            onStock = {
                stockOpenedIn = state.alertKey
                stockOpen = true
            },
        )
        RecipeChips(state, actions.selectRecipe, actions.selectWithoutRecipe)
        VesselRow(state, actions.selectVessel, actions.newVessel)
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
    // „Wie viel ist noch da?" (BRW-02, INV-03): gone in the frame another alert starts, without saving (rule 8).
    AlertGate(stockOpen, stockOpenedIn, state.alertKey, onDismissRequest = { stockOpen = false }) {
        StockSheet(
            currentGrams = state.stockGrams ?: 0.0,
            sessionLeafGrams = state.sessionLeafGrams,
            onSave = { grams ->
                actions.correctStock(grams)
                stockOpen = false
            },
            onDismissRequest = { stockOpen = false },
        )
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeaHeader(state: BrewSetupUiState.Ready, onStock: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall)) {
        Text(
            text = state.teaName,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.secondaryName == null && state.stockGrams == null) return@Column
        // „Kabusecha · 38,0 g übrig ✎": the stock is a button that opens „Wie viel ist noch da?" (BRW-02).
        FlowRow {
            val separator = stringResource(R.string.brew_setup_meta_separator)
            state.secondaryName?.let { name ->
                Text(
                    text = if (state.stockGrams != null) name + separator else name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            state.stockGrams?.let {
                StockButton(
                    StockLine(it, state.stockLevel, state.stockShort),
                    onStock,
                    Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

/** What the stock line says: the [grams] left, their INV-05 [level], and whether they are [short] of the leaf. */
private class StockLine(val grams: Double, val level: StockLevel?, val short: Boolean)

/**
 * „38 g übrig", „14 g übrig · knapp" for a *Knapp* tea (BRW-08, D-097), „Nur noch 3 g übrig" (· knapp) below this
 * brew's leaf (BRW-08, D-100) or „Leer" at 0 g and below (INV-05), with a pencil: opens the stock sheet (BRW-02,
 * INV-03).
 */
@Composable
private fun StockButton(line: StockLine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val grams = stockGramsText(line.grams)
    val low = line.level == StockLevel.LOW
    val text = when {
        line.grams < StockLevel.EMPTY_BELOW_GRAMS -> stringResource(R.string.brew_setup_stock_empty)
        line.short && low -> stringResource(R.string.brew_setup_stock_short_low, grams)
        line.short -> stringResource(R.string.brew_setup_stock_short, grams)
        low -> stringResource(R.string.brew_setup_stock_low, grams)
        else -> stringResource(R.string.brew_setup_stock, grams)
    }
    Row(
        modifier = modifier
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.brew_setup_stock_correct),
                onClick = onClick,
            )
            .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
            .testTag(SETUP_STOCK_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(UmamiSpacing.XXSmall))
        Icon(
            painterResource(UmamiIcons.Edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(STOCK_ICON_SIZE),
        )
    }
}

private val STOCK_ICON_SIZE = 16.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeChips(state: BrewSetupUiState.Ready, onSelect: (String) -> Unit, onSelectWithout: () -> Unit) {
    if (state.recipes.isEmpty() && !state.withoutRecipe) return
    FlowRow(
        modifier = Modifier.padding(horizontal = UmamiSpacing.SideMargin),
        horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
    ) {
        // *So nochmal brühen* without the session's recipe (BRW-06, D-100): *Ohne Rezept* first, selected while no
        // recipe is.
        if (state.withoutRecipe) {
            val selected = state.selectedRecipeId == null
            FilterChip(
                selected = selected,
                onClick = onSelectWithout,
                label = { Text(stringResource(R.string.brew_setup_without_recipe)) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(UmamiIcons.Check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
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

/**
 * *Gefäß* with its list (WARE-02): the offered vessels, then *Neues Gefäß …* ([onNewVessel]). The selected vessel
 * may be a recipe's archived one, „Kyusu 250 ml (archiviert)", which the list doesn't offer (D-100). The list gives
 * way to an alert like the ⋮ (rule 8).
 */
@Composable
private fun VesselRow(state: BrewSetupUiState.Ready, onSelect: (String) -> Unit, onNewVessel: () -> Unit) {
    val archived = state.archivedVessel?.takeIf { it.id == state.selectedVesselId }
    val selected = state.vessels.firstOrNull { it.id == state.selectedVesselId } ?: archived
    val name = when {
        selected == null -> stringResource(R.string.brew_setup_vessel_none)
        archived != null -> stringResource(R.string.brew_setup_vessel_archived, archived.name.trim())
        else -> selected.name
    }
    val label = when {
        selected == null -> name
        archived != null -> stringResource(R.string.brew_setup_vessel_archived, vesselLabel(archived))
        else -> vesselLabel(selected)
    }
    val description = stringResource(R.string.brew_setup_vessel_description, name)
    AlertGatedMenu(
        alertKey = state.alertKey,
        anchor = { open ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.DropdownList, onClick = open)
                    .semantics(mergeDescendants = true) { contentDescription = description }
                    .defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget)
                    .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RowLabel(stringResource(R.string.brew_setup_vessel))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null)
            }
        },
    ) { close ->
        state.vessels.forEach { vessel ->
            DropdownMenuItem(
                text = { Text(vesselLabel(vessel)) },
                onClick = {
                    close()
                    onSelect(vessel.id)
                },
            )
        }
        if (state.vessels.isNotEmpty()) HorizontalDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.brew_setup_vessel_new)) },
            onClick = {
                close()
                onNewVessel()
            },
        )
    }
}

/**
 * A vessel as the setup names it (WARE-03): its name alone when the name already ends with its
 * volume (the seed vessels, „Kyusu 120 ml"), else „<name> · N ml" (a vessel the user renamed). The
 * volume never wraps away from its unit.
 */
@Composable
private fun vesselLabel(vessel: VesselOption): String = if (vessel.namesItsVolume) {
    vessel.name.trim().replace(VOLUME_SPACE, "$1\u00A0ml")
} else {
    stringResource(R.string.brew_setup_vessel_option, vessel.name, vessel.brewMl)
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

/** The space between a vessel name's volume and its „ml". */
private val VOLUME_SPACE = Regex("""(\d)\s+ml$""", RegexOption.IGNORE_CASE)

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
