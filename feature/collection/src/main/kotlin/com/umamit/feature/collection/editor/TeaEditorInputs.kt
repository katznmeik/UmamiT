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
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.AlertGatedExposedDropdownMenu
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.collection.R
import com.umamit.feature.collection.detail.label
import com.umamit.feature.collection.label
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/* The inputs of Tee bearbeiten (SCR-10): text, number and choice fields and *Kaufdatum*. */

/** *Kaufdatum* with Material's date picker; never after today (as the catalog's add sheet, SCR-23). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseDate(date: LocalDate, today: LocalDate, alertKey: String?, onDateChange: (LocalDate) -> Unit) {
    val picking = rememberAlertGate(alertKey)
    Column(FieldModifier) {
        Text(
            text = stringResource(R.string.tea_editor_purchase_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = picking::open,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TeaEditorTags.PURCHASE_DATE),
        ) {
            Text(DAY.format(date))
        }
    }
    AlertGated(picking) {
        val todayMillis = today.utcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.utcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = picking::close,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateChange(it.utcDate()) }
                        picking.close()
                    },
                ) { Text(stringResource(R.string.tea_editor_purchase_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = picking::close) {
                    Text(stringResource(R.string.tea_editor_purchase_date_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Suppress("LongParameterList") // a text field's parts
@Composable
internal fun TextInput(
    value: String,
    @StringRes label: Int,
    tag: String,
    maxLength: Int,
    onValueChange: (String) -> Unit,
    @StringRes error: Int? = null,
    @StringRes hint: Int? = null,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(maxLength)) },
        modifier = FieldModifier.testTag(tag),
        label = { Text(stringResource(label)) },
        singleLine = minLines == 1,
        minLines = minLines,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

@Suppress("LongParameterList") // a number field's parts
@Composable
internal fun NumberInput(
    value: String,
    @StringRes label: Int,
    tag: String,
    maxDigits: Int,
    @StringRes error: Int?,
    onValueChange: (String) -> Unit,
    @StringRes suffix: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter(Char::isDigit).take(maxDigits)) },
        modifier = FieldModifier.testTag(tag),
        label = { Text(stringResource(label)) },
        suffix = suffix?.let { { Text(stringResource(it)) } },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * A read-only field that opens a menu of [options] (as the vessel editor's, SCR-14); with [singleLine] false a long
 * value wraps (a vessel „(archiviert)" at font scale 2.0). The menu gives way to another alert than the one it was
 * opened in ([alertKey], rule 8).
 */
@Suppress("LongParameterList") // a choice field's parts
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ChoiceField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    alertKey: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    val menu = rememberAlertGate(alertKey)
    ExposedDropdownMenuBox(expanded = menu.shown, onExpandedChange = menu::expand, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menu.shown) },
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        AlertGatedExposedDropdownMenu(menu) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        menu.close()
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy", Locale.GERMAN)

/** Material's date picker counts days as UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Digits with a decimal comma (or dot), at most [max] characters. */
internal fun String.decimalInput(max: Int): String = filter { it.isDigit() || it == ',' || it == '.' }.take(max)

/** A field of the editor: full width within the side margins. */
internal val FieldModifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = UmamiSpacing.SideMargin)
