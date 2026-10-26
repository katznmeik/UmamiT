package com.umamit.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The add sheet (SCR-23). */
const val CATALOG_ADD_SHEET_TAG = "catalog_add_sheet"

/** The add sheet's price field. */
const val CATALOG_ADD_PRICE_TAG = "catalog_add_price"

/** The add sheet's custom amount field. */
const val CATALOG_ADD_GRAMS_TAG = "catalog_add_grams"

/** The add sheet's purchase date button. */
const val CATALOG_ADD_DATE_TAG = "catalog_add_date"

/** The add sheet's *Aufnehmen* / *Nachkauf erfassen*. */
const val CATALOG_ADD_SAVE_TAG = "catalog_add_save"

/** What the add sheet's controls do. */
data class AddSheetActions(
    val onSelectSize: (Int) -> Unit = {},
    val onPriceChange: (String) -> Unit = {},
    val onGramsChange: (String) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * *In Sammlung aufnehmen* / *Nachkauf erfassen* (SCR-23, CAT-12) as Material 3's modal bottom
 * sheet: the package sizes with their prices („250 g · ≈ 30,07 € · 3 % gespart"), a custom
 * amount, the price (the shop's, the exact one once fetched, an estimate or empty; editable),
 * the purchase date (today by default) and *Aufnehmen*. Saving never waits for a price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogAddSheet(state: AddSheetState, actions: AddSheetActions, onDismissRequest: () -> Unit) {
    // While it saves, neither a swipe nor back hides it, so the save's outcome shows (D-090).
    val saving by rememberUpdatedState(state.saving)
    val sheetState = rememberCatalogSheetState(UmamiTheme.reduceMotion) { it != SheetValue.Hidden || !saving }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !state.saving),
        modifier = Modifier.testTag(CATALOG_ADD_SHEET_TAG),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = UmamiSpacing.SideMargin, end = UmamiSpacing.SideMargin, bottom = UmamiSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small),
        ) {
            Text(
                text = stringResource(
                    if (state.repurchase) R.string.catalog_add_title_repurchase else R.string.catalog_add_title,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = state.teaName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Label(stringResource(R.string.catalog_add_package))
            Sizes(state, actions)
            Price(state, actions.onPriceChange)
            PurchaseDate(state, actions.onDateChange)
            if (state.failed) {
                Text(
                    text = stringResource(R.string.catalog_add_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(UmamiSpacing.XSmall))
            PillButton(
                text = stringResource(
                    if (state.repurchase) R.string.catalog_add_save_repurchase else R.string.catalog_add_save,
                ),
                onClick = actions.onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CATALOG_ADD_SAVE_TAG),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = UmamiSpacing.XSmall),
    )
}

/** The radio list (SCR-23): one row per size, smallest first, then *Andere Menge* with its field. */
@Composable
private fun ColumnScope.Sizes(state: AddSheetState, actions: AddSheetActions) {
    Column(modifier = Modifier.selectableGroup()) {
        state.sizes.forEachIndexed { index, size ->
            SizeRow(
                text = size.label(),
                selected = index == state.selected,
                onClick = { actions.onSelectSize(index) },
            )
        }
        SizeRow(
            text = stringResource(R.string.catalog_add_custom),
            selected = state.custom,
            onClick = { actions.onSelectSize(state.sizes.size) },
        )
    }
    if (state.custom) {
        val grams = state.customGrams
        OutlinedTextField(
            value = grams,
            onValueChange = actions.onGramsChange,
            label = { Text(stringResource(R.string.catalog_add_grams)) },
            suffix = { Text(stringResource(R.string.catalog_add_grams_unit)) },
            singleLine = true,
            isError = grams.isNotBlank() && state.grams == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CATALOG_ADD_GRAMS_TAG),
        )
    }
}

@Composable
private fun SizeRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row handles the click, so TalkBack reads one control.
        RadioButton(selected = selected, onClick = null)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = UmamiSpacing.Small,
                top = UmamiSpacing.XXSmall,
                bottom = UmamiSpacing.XXSmall,
            ),
        )
    }
}

/** „50 g · 8,95 €", „250 g · ≈ 30,07 € · 3 % gespart", „10 g · Preis beim Hinzufügen". */
@Composable
private fun AddSheetSize.label(): String {
    val cents = priceCents
    val price = when {
        cents == null -> stringResource(R.string.catalog_package_price_on_add)
        estimate -> stringResource(R.string.catalog_package_estimate, CatalogFormat.euros(cents))
        else -> stringResource(R.string.catalog_package_price, CatalogFormat.euros(cents))
    }
    return listOfNotNull(
        stringResource(R.string.catalog_package_grams, CatalogFormat.grams(grams)),
        price,
        savingsPercent?.takeIf { it > 0 }?.let { stringResource(R.string.catalog_package_savings, it) },
    ).joinToString(stringResource(R.string.catalog_meta_separator))
}

@Composable
private fun Price(state: AddSheetState, onPriceChange: (String) -> Unit) {
    val hint = when {
        !state.priceValid -> stringResource(R.string.catalog_add_price_invalid)

        else -> when (state.hint) {
            PriceHint.FETCHING -> stringResource(R.string.catalog_add_price_fetching)
            PriceHint.SHOP -> stringResource(R.string.catalog_add_price_shop)
            PriceHint.ESTIMATE -> stringResource(R.string.catalog_add_price_estimate)
            PriceHint.NONE -> stringResource(R.string.catalog_add_price_none)
            null -> null
        }
    }
    OutlinedTextField(
        value = state.price,
        onValueChange = onPriceChange,
        label = { Text(stringResource(R.string.catalog_add_price)) },
        suffix = { Text(stringResource(R.string.catalog_add_price_unit)) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        isError = !state.priceValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATALOG_ADD_PRICE_TAG),
    )
}

/** *Kaufdatum* with Material's date picker; never after today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.PurchaseDate(state: AddSheetState, onDateChange: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Label(stringResource(R.string.catalog_add_date))
    OutlinedButton(
        onClick = { picking = true },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATALOG_ADD_DATE_TAG),
    ) {
        Text(CatalogFormat.day(state.date))
    }
    if (picking) {
        val todayMillis = state.today.utcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.utcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis

                override fun isSelectableYear(year: Int): Boolean = year <= state.today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onDateChange(it.utcDate()) }
                        picking = false
                    },
                ) { Text(stringResource(R.string.catalog_add_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.catalog_add_date_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Material's date picker counts days as UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
