package com.umamit.feature.collection.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.BrewMethod
import com.umamit.core.model.Vessel
import com.umamit.feature.collection.R
import com.umamit.feature.collection.editor.ChoiceField
import com.umamit.feature.collection.editor.FieldModifier
import com.umamit.feature.collection.editor.Section
import com.umamit.feature.collection.editor.TextInput
import com.umamit.feature.collection.editor.decimalInput

/** SCR-12's sections: *Rezept*, *Schritte* and *Notizen*. */
@Composable
internal fun RecipeFields(
    state: RecipeEditorUiState.Ready,
    actions: RecipeEditorActions,
    modifier: Modifier = Modifier,
) {
    val update = actions.update
    Column(modifier = modifier.padding(vertical = UmamiSpacing.Small)) {
        Section(R.string.recipe_editor_section_recipe) { General(state, actions) }
        Section(R.string.recipe_editor_section_steps) {
            if (RecipeFieldError.OnlyRinse in state.errors) {
                Text(
                    text = stringResource(R.string.recipe_editor_error_only_rinse),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = FieldModifier,
                )
            }
            Column(Modifier.testTag(RecipeEditorTags.STEPS)) { RecipeSteps(state, update) }
        }
        Section(R.string.recipe_editor_section_notes) {
            TextInput(
                value = state.form.notes,
                label = R.string.recipe_editor_notes,
                tag = RecipeEditorTags.NOTES,
                maxLength = RecipeForm.MAX_NOTES,
                minLines = NOTES_MIN_LINES,
                onValueChange = { value -> update { it.copy(notes = value) } },
            )
        }
    }
}

@Composable
private fun General(state: RecipeEditorUiState.Ready, actions: RecipeEditorActions) {
    val form = state.form
    val errors = state.errors
    val update = actions.update
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
        TextInput(
            value = form.name,
            label = R.string.recipe_editor_name,
            tag = RecipeEditorTags.NAME,
            // A little room past the limit, so a pasted name shows its error rather than being cut.
            maxLength = RecipeForm.MAX_NAME + 1,
            error = when {
                RecipeFieldError.NameEmpty in errors -> R.string.recipe_editor_error_name_empty
                RecipeFieldError.NameTooLong in errors -> R.string.recipe_editor_error_name_long
                else -> null
            },
            hint = R.string.recipe_editor_name_hint,
            onValueChange = { value -> update { it.copy(name = value) } },
        )
        ChoiceField(
            label = stringResource(R.string.recipe_editor_method),
            selected = form.method,
            options = BrewMethod.entries,
            optionLabel = { stringResource(it.label) },
            onSelect = { method -> update { it.copy(method = method) } },
            modifier = FieldModifier.testTag(RecipeEditorTags.METHOD),
        )
        // The list never offers an archived vessel; the recipe's own archived one shows as chosen (WARE-02, D-100).
        ChoiceField(
            label = stringResource(R.string.recipe_editor_vessel),
            selected = state.selectedVessel,
            options = listOf<Vessel?>(null) + state.vessels,
            optionLabel = { it?.let { v -> vesselLabel(v) } ?: stringResource(R.string.recipe_editor_vessel_none) },
            onSelect = { vessel -> actions.selectVessel(vessel?.id) },
            modifier = FieldModifier.testTag(RecipeEditorTags.VESSEL),
            singleLine = false,
        )
        AmountField(
            value = form.leaf,
            label = R.string.recipe_editor_leaf,
            unit = R.string.recipe_editor_unit_g,
            error = (RecipeFieldError.Leaf in errors).then(R.string.recipe_editor_error_leaf),
            decimal = true,
            onValueChange = { value -> update { it.copy(leaf = value) } },
            modifier = FieldModifier.testTag(RecipeEditorTags.LEAF),
        )
        AmountField(
            value = form.water,
            label = R.string.recipe_editor_water,
            unit = R.string.recipe_editor_unit_ml,
            error = (RecipeFieldError.Water in errors).then(R.string.recipe_editor_error_water),
            decimal = false,
            onValueChange = { value -> update { it.copy(water = value) } },
            modifier = FieldModifier.testTag(RecipeEditorTags.WATER),
        )
        // PRF-03 AC1: the ratio follows the typing.
        val ratio = form.ratioPer100Ml
        Text(
            text = if (ratio == null) {
                stringResource(R.string.recipe_editor_ratio_unknown)
            } else {
                stringResource(R.string.recipe_editor_ratio, RecipeForm.leafText(ratio))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = FieldModifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(RecipeEditorTags.RATIO),
        )
        Row(
            modifier = FieldModifier.defaultMinSize(minHeight = UmamiSpacing.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recipe_editor_temperature),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = UmamiSpacing.Small),
            )
            Stepper(
                value = temperatureLabel(form.baseTempC),
                valueDescription = pluralStringResource(
                    R.plurals.recipe_editor_temperature_description,
                    form.baseTempC,
                    form.baseTempC,
                ),
                decreaseDescription = stringResource(R.string.recipe_editor_cooler),
                increaseDescription = stringResource(R.string.recipe_editor_warmer),
                canStep = { up -> form.withBaseStep(up) != form },
                onStep = { up -> update { it.withBaseStep(up) } },
                modifier = Modifier.testTag(RecipeEditorTags.TEMPERATURE),
            )
        }
        ChoiceField(
            label = stringResource(R.string.recipe_editor_pre_alert),
            selected = form.preAlertSeconds,
            options = PreAlertChoices,
            optionLabel = { recipePreAlertLabel(it, state.globalPreAlertSeconds) },
            onSelect = { seconds -> update { it.copy(preAlertSeconds = seconds) } },
            modifier = FieldModifier.testTag(RecipeEditorTags.PRE_ALERT),
        )
        Text(
            text = stringResource(R.string.recipe_editor_pre_alert_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = FieldModifier,
        )
        DefaultSwitch(state, update)
    }
}

/** *Als Standard* (PRF-01): the stored default stays on, so the tea always has exactly one. */
@Composable
private fun DefaultSwitch(state: RecipeEditorUiState.Ready, update: ((RecipeForm) -> RecipeForm) -> Unit) {
    val locked = state.isStoredDefault
    val checked = locked || state.form.isDefault
    ListItem(
        headlineContent = { Text(stringResource(R.string.recipe_editor_default)) },
        supportingContent = {
            val detail = if (locked) R.string.recipe_editor_default_locked else R.string.recipe_editor_default_detail
            Text(stringResource(detail))
        },
        // The row is the switch: one node, one target.
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = !locked) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecipeEditorTags.DEFAULT)
            .toggleable(
                value = checked,
                enabled = !locked,
                role = Role.Switch,
                onValueChange = { on -> update { it.copy(isDefault = on) } },
            )
            .padding(horizontal = UmamiSpacing.XXSmall),
    )
}

@Suppress("LongParameterList") // an amount field's parts
@Composable
private fun AmountField(
    value: String,
    label: Int,
    unit: Int,
    error: Int?,
    decimal: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            val cleaned = if (decimal) {
                typed.decimalInput(
                    LEAF_CHARS,
                )
            } else {
                typed.filter(Char::isDigit).take(WATER_DIGITS)
            }
            onValueChange(cleaned)
        },
        modifier = modifier,
        label = { Text(stringResource(label)) },
        suffix = { Text(stringResource(unit)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
    )
}

private fun Boolean.then(value: Int): Int? = if (this) value else null

private const val NOTES_MIN_LINES = 3

/** „100,0" has five characters; „3000" four digits. */
private const val LEAF_CHARS = 5
private const val WATER_DIGITS = 4
