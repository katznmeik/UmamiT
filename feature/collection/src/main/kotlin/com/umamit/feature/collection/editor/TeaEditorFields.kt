package com.umamit.feature.collection.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.designsystem.component.DiscardChangesDialog
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.HarvestSeason
import com.umamit.core.model.UmamiLevel
import com.umamit.feature.collection.R
import com.umamit.feature.collection.detail.label
import com.umamit.feature.collection.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The fields of Tee bearbeiten (SCR-10, TEA-02, D-100): *Allgemein*, *Herkunft*, *Kauf* and *Notizen*.
 */

@Composable
internal fun EditorFields(
    state: TeaEditorUiState.Ready,
    update: ((TeaEditorForm) -> TeaEditorForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = UmamiSpacing.Small)) {
        General(state, update)
        Origin(state, update)
        Purchase(state, update)
        Section(R.string.tea_editor_section_notes) {
            TextInput(
                value = state.form.notes,
                label = R.string.tea_editor_notes,
                tag = TeaEditorTags.NOTES,
                maxLength = TeaEditorForm.MAX_NOTES,
                minLines = NOTES_MIN_LINES,
                onValueChange = { value -> update { it.copy(notes = value) } },
            )
        }
    }
}

@Composable
private fun General(state: TeaEditorUiState.Ready, update: ((TeaEditorForm) -> TeaEditorForm) -> Unit) {
    val form = state.form
    val errors = state.errors
    Section(R.string.tea_editor_section_general) {
        TextInput(
            value = form.name,
            label = R.string.tea_editor_name,
            tag = TeaEditorTags.NAME,
            // A little room past the limit, so a pasted name shows its error rather than being cut.
            maxLength = TeaEditorForm.MAX_NAME + 1,
            error = when {
                TeaFieldError.NameEmpty in errors -> R.string.tea_editor_error_name_empty
                TeaFieldError.NameTooLong in errors -> R.string.tea_editor_error_name_long
                else -> null
            },
            onValueChange = { value -> update { it.copy(name = value) } },
        )
        TextInput(
            value = form.secondaryName,
            label = R.string.tea_editor_secondary_name,
            tag = TeaEditorTags.SECONDARY_NAME,
            maxLength = TeaEditorForm.MAX_NAME,
            hint = R.string.tea_editor_secondary_name_hint,
            onValueChange = { value -> update { it.copy(secondaryName = value) } },
        )
        SorteField(
            selected = form.styleKey,
            onSelect = { key -> update { it.withStyle(key) } },
            modifier = FieldModifier.testTag(TeaEditorTags.SORTE),
        )
        ChoiceField(
            label = stringResource(R.string.tea_editor_umami),
            selected = form.umami,
            options = listOf<UmamiLevel?>(null) + UmamiLevel.entries,
            optionLabel = { stringResource(it?.editorLabel ?: R.string.tea_editor_umami_unset) },
            onSelect = { level -> update { it.copy(umami = level) } },
            modifier = FieldModifier.testTag(TeaEditorTags.UMAMI),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.tea_editor_flavored)) },
            supportingContent = { Text(stringResource(R.string.tea_editor_flavored_detail)) },
            // The row is the switch: one node, one target.
            trailingContent = { Switch(checked = form.isFlavored, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TeaEditorTags.FLAVORED)
                .toggleable(
                    value = form.isFlavored,
                    role = Role.Switch,
                    onValueChange = { on -> update { it.copy(isFlavored = on) } },
                )
                .padding(horizontal = UmamiSpacing.XXSmall),
        )
    }
}

@Composable
private fun Origin(state: TeaEditorUiState.Ready, update: ((TeaEditorForm) -> TeaEditorForm) -> Unit) {
    val form = state.form
    val errors = state.errors
    Section(R.string.tea_editor_section_origin) {
        val countries = (TEA_COUNTRIES + listOfNotNull(form.originCountry)).distinct()
        ChoiceField(
            label = stringResource(R.string.tea_editor_country),
            selected = form.originCountry,
            options = listOf<String?>(null) + countries.sortedBy(::countryName),
            optionLabel = { it?.let(::countryName) ?: stringResource(R.string.tea_editor_country_unset) },
            onSelect = { country -> update { it.copy(originCountry = country) } },
            modifier = FieldModifier.testTag(TeaEditorTags.COUNTRY),
        )
        TextInput(
            value = form.region,
            label = R.string.tea_editor_region,
            tag = TeaEditorTags.REGION,
            maxLength = TeaEditorForm.MAX_NAME,
            onValueChange = { value -> update { it.copy(region = value) } },
        )
        TextInput(
            value = form.cultivar,
            label = R.string.tea_editor_cultivar,
            tag = TeaEditorTags.CULTIVAR,
            maxLength = TeaEditorForm.MAX_NAME,
            onValueChange = { value -> update { it.copy(cultivar = value) } },
        )
        NumberInput(
            value = form.harvestYear,
            label = R.string.tea_editor_harvest_year,
            tag = TeaEditorTags.HARVEST_YEAR,
            maxDigits = YEAR_DIGITS,
            error = when {
                TeaFieldError.HarvestYearFuture in errors -> R.string.tea_editor_error_harvest_year_future
                TeaFieldError.HarvestYear in errors -> R.string.tea_editor_error_harvest_year
                else -> null
            },
            onValueChange = { value -> update { it.copy(harvestYear = value) } },
        )
        ChoiceField(
            label = stringResource(R.string.tea_editor_harvest_season),
            selected = form.harvestSeason,
            options = listOf<HarvestSeason?>(null) + HarvestSeason.entries.filter { it.label != null },
            optionLabel = { season ->
                stringResource(season?.label ?: R.string.tea_editor_harvest_season_unset)
            },
            onSelect = { season -> update { it.copy(harvestSeason = season) } },
            modifier = FieldModifier.testTag(TeaEditorTags.HARVEST_SEASON),
        )
        NumberInput(
            value = form.shadingDays,
            label = R.string.tea_editor_shading,
            tag = TeaEditorTags.SHADING,
            maxDigits = SHADING_DIGITS,
            suffix = R.string.tea_editor_shading_unit,
            error = (TeaFieldError.ShadingDays in errors).then(R.string.tea_editor_error_shading),
            onValueChange = { value -> update { it.copy(shadingDays = value) } },
        )
        TextInput(
            value = form.processing,
            label = R.string.tea_editor_processing,
            tag = TeaEditorTags.PROCESSING,
            maxLength = TeaEditorForm.MAX_PROCESSING,
            hint = R.string.tea_editor_processing_hint,
            onValueChange = { value -> update { it.copy(processing = value) } },
        )
    }
}

@Composable
private fun Purchase(state: TeaEditorUiState.Ready, update: ((TeaEditorForm) -> TeaEditorForm) -> Unit) {
    val form = state.form
    val errors = state.errors
    Section(R.string.tea_editor_section_purchase) {
        TextInput(
            value = form.vendor,
            label = R.string.tea_editor_vendor,
            tag = TeaEditorTags.VENDOR,
            maxLength = TeaEditorForm.MAX_NAME,
            onValueChange = { value -> update { it.copy(vendor = value) } },
        )
        val date = form.purchaseDate
        if (state.isNew && date != null) {
            PurchaseDate(date, state.today) { day -> update { it.copy(purchaseDate = day) } }
            OutlinedTextField(
                value = form.packageGrams,
                onValueChange = { typed ->
                    update { it.copy(packageGrams = typed.decimalInput(MAX_GRAMS_CHARS)) }
                },
                modifier = FieldModifier.testTag(TeaEditorTags.PACKAGE),
                label = { Text(stringResource(R.string.tea_editor_package)) },
                suffix = { Text(stringResource(R.string.tea_editor_package_unit)) },
                singleLine = true,
                isError = TeaFieldError.PackageGrams in errors,
                supportingText = {
                    val text = if (TeaFieldError.PackageGrams in errors) {
                        R.string.tea_editor_error_package
                    } else {
                        R.string.tea_editor_package_hint
                    }
                    Text(stringResource(text))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            val priceError = when {
                TeaFieldError.Price in errors -> R.string.tea_editor_error_price
                TeaFieldError.PriceWithoutPackage in errors -> R.string.tea_editor_error_price_without_package
                else -> null
            }
            OutlinedTextField(
                value = form.price,
                onValueChange = { typed ->
                    update { it.copy(price = typed.decimalInput(MAX_PRICE_CHARS)) }
                },
                modifier = FieldModifier.testTag(TeaEditorTags.PRICE),
                label = { Text(stringResource(R.string.tea_editor_price)) },
                suffix = { Text(stringResource(R.string.tea_editor_price_unit)) },
                singleLine = true,
                isError = priceError != null,
                supportingText = priceError?.let { { Text(stringResource(it)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@Composable
internal fun Section(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
        SectionHeader(
            title = stringResource(title),
            modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Small),
        )
        content()
    }
}

/** The Sorte: every built-in style by Kategorie (as *Sorte wählen*), *Andere / unbekannt* last. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SorteField(selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val groups = SortePicker.groups("", { it }, { "" })
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(sorteNameRes(selected ?: OTHER_STYLE_KEY) ?: R.string.sorte_other),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.tea_editor_sorte)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                group.category?.let { category ->
                    Text(
                        text = stringResource(category.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = UmamiSpacing.Medium,
                            vertical = UmamiSpacing.XSmall,
                        ),
                    )
                }
                group.styleKeys.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(stringResource(sorteNameRes(key) ?: R.string.sorte_other)) },
                        onClick = {
                            expanded = false
                            onSelect(key)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}

@get:StringRes
private val UmamiLevel.editorLabel: Int
    get() = when (this) {
        UmamiLevel.NONE -> R.string.tea_editor_umami_none
        UmamiLevel.LOW -> R.string.tea_editor_umami_low
        UmamiLevel.MEDIUM -> R.string.tea_editor_umami_medium
        UmamiLevel.HIGH -> R.string.tea_editor_umami_high
        UmamiLevel.VERY_HIGH -> R.string.tea_editor_umami_very_high
    }

/** The countries tea usually comes from (ISO 3166-1 alpha-2, 05 §3.1); a stored other one is offered too. */
private val TEA_COUNTRIES = listOf("JP", "CN", "TW", "KR", "IN", "LK", "NP", "VN", "TH", "ID", "KE", "ZA", "DE")

/** The German name of the country [code], e.g. „Japan" for `JP`. */
private fun countryName(code: String): String =
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.GERMAN).ifEmpty { code }

private fun Boolean.then(value: Int): Int? = if (this) value else null

private const val NOTES_MIN_LINES = 3

/** „10000,5" and „123456,78": the longest valid package and price. */
private const val MAX_GRAMS_CHARS = 7
private const val MAX_PRICE_CHARS = 9

private const val YEAR_DIGITS = 4
private const val SHADING_DIGITS = 2
