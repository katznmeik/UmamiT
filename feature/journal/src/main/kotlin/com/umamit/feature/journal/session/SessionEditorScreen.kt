package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.umamit.core.designsystem.component.EmptyState
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.Vessel
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedExposedDropdownMenu
import com.umamit.core.ui.EditorScaffold
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.journal.R
import java.time.LocalDate
import java.time.ZonedDateTime

/** Test tags of SCR-17. */
object SessionEditorTags {
    const val SAVE = "session_editor_save"
    const val DATE = "session_editor_date"
    const val TIME = "session_editor_time"
    const val VESSEL = "session_editor_vessel"
    const val LEAF = "session_editor_leaf"
    const val WATER = "session_editor_water"
    const val STOCK_LINE = "session_editor_stock_line"
    const val FUTURE = "session_editor_future"
    const val SAVE_FAILED = "session_editor_save_failed"

    /** The *Zeit* field of the step at a position: [STEP_TIME] + position. */
    const val STEP_TIME = "session_editor_step_time_"

    /** The *°C* stepper of the step at a position: [STEP_TEMP] + position. */
    const val STEP_TEMP = "session_editor_step_temp_"
}

/** What the editor's inputs do. */
class SessionEditorActions(
    val close: () -> Unit = {},
    val save: () -> Unit = {},
    val update: ((SessionEditorForm) -> SessionEditorForm) -> Unit = {},
    val setDate: (LocalDate) -> Unit = {},
    val setTime: (hour: Int, minute: Int) -> Unit = { _, _ -> },
)

/**
 * Sitzung bearbeiten (SCR-17; JRN-11, D-112): ✕ · „Sitzung bearbeiten" · *Speichern* (enabled once something changed
 * and every field is valid); *Datum*, *Uhrzeit*, *Gefäß*, *Blattmenge* and *Wasser* with the setup's limits; SCHRITTE
 * with *Zeit* and *°C* for each brewed step, never started and skipped ones greyed. Leaving with changes asks
 * „Änderungen verwerfen?". The pickers, the menu and the question give way to another alert than the one they were
 * opened in ([alertKey], rule 8); the values stay in the view model.
 */
@Composable
fun SessionEditorScreen(
    state: SessionEditorUiState,
    modifier: Modifier = Modifier,
    actions: SessionEditorActions = SessionEditorActions(),
    alertKey: String? = null,
) {
    val ready = state as? SessionEditorUiState.Ready
    EditorScaffold(
        title = stringResource(R.string.session_editor_title),
        closeDescription = stringResource(R.string.session_editor_close),
        saveLabel = stringResource(R.string.session_editor_save),
        saveTag = SessionEditorTags.SAVE,
        showActions = ready?.canSave == true,
        hasChanges = ready?.hasChanges == true,
        saving = ready?.saving == true,
        onSave = actions.save,
        onClose = actions.close,
        modifier = modifier,
        alertKey = alertKey,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SessionEditorUiState.Loading -> Unit

                SessionEditorUiState.NotFound -> EmptyState(
                    title = stringResource(R.string.session_not_found_title),
                    body = stringResource(R.string.session_not_found_body),
                    modifier = Modifier.align(Alignment.Center),
                )

                is SessionEditorUiState.Ready -> SessionEditorFields(state, actions, alertKey)
            }
        }
    }
}

@Composable
private fun SessionEditorFields(state: SessionEditorUiState.Ready, actions: SessionEditorActions, alertKey: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = UmamiSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
    ) {
        if (state.saveFailed) {
            ErrorText(R.string.session_editor_save_failed, Modifier.testTag(SessionEditorTags.SAVE_FAILED))
        }
        StartFields(state, actions, alertKey)
        VesselField(state, actions, alertKey)
        LeafField(state, actions)
        AmountField(
            value = state.form.water,
            label = R.string.session_editor_water,
            unit = R.string.session_editor_unit_ml,
            error = R.string.session_editor_error_water.takeIf { SessionEditorError.Water in state.errors },
            keyboard = KeyboardType.Number,
            onValueChange = { text ->
                actions.update { it.copy(water = text.filter(Char::isDigit).take(WATER_DIGITS)) }
            },
            modifier = EditorFieldModifier.testTag(SessionEditorTags.WATER),
        )
        SectionHeader(
            title = stringResource(R.string.session_editor_steps),
            modifier = Modifier.padding(top = UmamiSpacing.Small),
        )
        state.steps.forEach { row -> StepRow(row, actions) }
    }
}

/** *Datum* and *Uhrzeit* of the start, in the session's zone; the future is refused at the fields. */
@Composable
private fun StartFields(state: SessionEditorUiState.Ready, actions: SessionEditorActions, alertKey: String?) {
    val start = state.form.startIn(state.zone)
    val datePicker = rememberAlertGate(alertKey)
    val timePicker = rememberAlertGate(alertKey)
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
        StartButtons(start, onDate = datePicker::open, onTime = timePicker::open)
        if (SessionEditorError.InFuture in state.errors) {
            ErrorText(R.string.session_editor_error_future, Modifier.testTag(SessionEditorTags.FUTURE))
        }
    }
    AlertGated(datePicker) {
        SessionDateDialog(start.toLocalDate(), state.today, onPick = actions.setDate, onDismiss = datePicker::close)
    }
    AlertGated(timePicker) {
        SessionTimeDialog(start.hour, start.minute, onPick = actions.setTime, onDismiss = timePicker::close)
    }
}

/** The two buttons that open the system date and time pickers. */
@Composable
private fun StartButtons(start: ZonedDateTime, onDate: () -> Unit, onTime: () -> Unit) {
    Row(modifier = EditorFieldModifier, horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
        LabelledButton(
            label = R.string.session_editor_date,
            value = GermanFormat.day(start),
            onClick = onDate,
            modifier = Modifier.weight(1f).testTag(SessionEditorTags.DATE),
        )
        LabelledButton(
            label = R.string.session_editor_time,
            value = GermanFormat.time(start),
            onClick = onTime,
            modifier = Modifier.weight(1f).testTag(SessionEditorTags.TIME),
        )
    }
}

@Composable
private fun LabelledButton(label: Int, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = stringResource(label)
    val spoken = stringResource(R.string.session_editor_start_field, name, value)
    Column(modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
        ) {
            Text(value, style = MaterialTheme.typography.bodyLarge.tabular())
        }
    }
}

/** *Gefäß*: *Kein Gefäß*, the vessels that are not archived and the session's own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VesselField(state: SessionEditorUiState.Ready, actions: SessionEditorActions, alertKey: String?) {
    val menu = rememberAlertGate(alertKey)
    val none = stringResource(R.string.session_vessel_none)
    ExposedDropdownMenuBox(
        expanded = menu.shown,
        onExpandedChange = menu::expand,
        modifier = EditorFieldModifier.testTag(SessionEditorTags.VESSEL),
    ) {
        OutlinedTextField(
            value = state.vesselName ?: none,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.session_editor_vessel)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu.shown) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        AlertGatedExposedDropdownMenu(menu) {
            (listOf<Vessel?>(null) + state.vessels).forEach { vessel ->
                DropdownMenuItem(
                    text = { Text(vessel?.name ?: none) },
                    onClick = {
                        menu.close()
                        actions.update { it.copy(vesselId = vessel?.id) }
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** *Blattmenge* and, while it differs, what the change does to the stock. */
@Composable
private fun LeafField(state: SessionEditorUiState.Ready, actions: SessionEditorActions) {
    Column {
        AmountField(
            value = state.form.leaf,
            label = R.string.session_editor_leaf,
            unit = R.string.session_editor_unit_g,
            error = R.string.session_editor_error_leaf.takeIf { SessionEditorError.Leaf in state.errors },
            keyboard = KeyboardType.Decimal,
            onValueChange = { text ->
                actions.update {
                    it.copy(leaf = text.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(LEAF_CHARS))
                }
            },
            modifier = EditorFieldModifier.testTag(SessionEditorTags.LEAF),
        )
        state.stockChangeGrams?.let { grams ->
            Text(
                text = stringResource(R.string.session_editor_stock_line, GermanFormat.gramsTrimmed(grams)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = EditorFieldModifier
                    .padding(top = UmamiSpacing.XXSmall)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag(SessionEditorTags.STOCK_LINE),
            )
        }
    }
}

@Suppress("LongParameterList") // an amount field's parts
@Composable
private fun AmountField(
    value: String,
    label: Int,
    unit: Int,
    error: Int?,
    keyboard: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(stringResource(label)) },
        suffix = { Text(stringResource(unit)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

@Composable
private fun ErrorText(text: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = EditorFieldModifier.semantics { liveRegion = LiveRegionMode.Polite }.then(modifier),
    )
}

/** A field of the editor: full width within the side margins. */
internal val EditorFieldModifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = UmamiSpacing.SideMargin)

private const val LEAF_CHARS = 5
private const val WATER_DIGITS = 4
