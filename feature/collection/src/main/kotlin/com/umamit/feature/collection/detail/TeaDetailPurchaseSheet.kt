package com.umamit.feature.collection.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umamit.core.data.TeaPurchase
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.input.GermanNumber
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.collection.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

const val PURCHASE_SHEET_GRAMS_TAG = "purchase_sheet_grams"
const val PURCHASE_SHEET_PRICE_TAG = "purchase_sheet_price"
const val PURCHASE_SHEET_DATE_TAG = "purchase_sheet_date"
const val PURCHASE_SHEET_SAVE_TAG = "purchase_sheet_save"

/**
 * *Nachkauf erfassen* (INV-03, SCR-09 VORRAT, D-104): the grams bought, an optional price and the day, today by
 * default and never later. The fields start empty; *Erfassen* stays off until the grams are a weight
 * ([GermanNumber.parseGrams]), a price typed is an amount ([GermanNumber.parseCents]) and the day isn't after
 * [today], and each field says why. *Erfassen* calls [onSave] and the host closes the sheet in that frame;
 * *Abbrechen*, back, the scrim or a drag close it without saving. Like every sheet it never covers an alert: the
 * host removes it in the frame an alert starts (rule 8); its date picker gives way to an alert other than
 * [alertKey]'s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PurchaseSheet(
    today: LocalDate,
    alertKey: String?,
    onSave: (TeaPurchase) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberPurchaseSheetState(UmamiTheme.reduceMotion),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        PurchaseSheetContent(today, alertKey, onSave, onDismissRequest)
    }
}

@Composable
private fun PurchaseSheetContent(
    today: LocalDate,
    alertKey: String?,
    onSave: (TeaPurchase) -> Unit,
    onCancel: () -> Unit,
) {
    var gramsText by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(today) }
    val form = PurchaseForm(gramsText, priceText, date)
    val purchase = form.purchase(today)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UmamiSpacing.SideMargin)
            .padding(bottom = UmamiSpacing.Medium)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.collection_purchase_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        AmountField(
            value = gramsText,
            onValueChange = { gramsText = it },
            label = R.string.collection_purchase_grams,
            suffix = R.string.collection_purchase_grams_unit,
            error = form.gramsError,
            tag = PURCHASE_SHEET_GRAMS_TAG,
        )
        AmountField(
            value = priceText,
            onValueChange = { priceText = it },
            label = R.string.collection_purchase_price,
            suffix = R.string.collection_purchase_price_unit,
            error = form.priceError,
            tag = PURCHASE_SHEET_PRICE_TAG,
            hint = R.string.collection_purchase_price_hint,
        )
        PurchaseDay(date, today, form.dateError(today), alertKey) { date = it }
        Spacer(Modifier.height(UmamiSpacing.Small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.collection_purchase_cancel)) }
            Spacer(Modifier.width(UmamiSpacing.XSmall))
            PillButton(
                text = stringResource(R.string.collection_purchase_save),
                onClick = { purchase?.let(onSave) },
                enabled = purchase != null,
                modifier = Modifier.testTag(PURCHASE_SHEET_SAVE_TAG),
            )
        }
    }
}

/**
 * What the sheet holds: the typed grams and price and the chosen day. A blank price is no price; blank grams are
 * missing but not yet an error.
 */
internal data class PurchaseForm(val grams: String, val price: String, val date: LocalDate) {
    private val parsedGrams: Double? = GermanNumber.parseGrams(grams)
    private val parsedCents: Long? = GermanNumber.parseCents(price)

    @get:StringRes
    val gramsError: Int?
        get() = R.string.collection_purchase_grams_invalid.takeIf { grams.isNotBlank() && parsedGrams == null }

    @get:StringRes
    val priceError: Int?
        get() = R.string.collection_purchase_price_invalid.takeIf { price.isNotBlank() && parsedCents == null }

    @StringRes
    fun dateError(today: LocalDate): Int? = R.string.collection_purchase_date_future.takeIf { date.isAfter(today) }

    /** The purchase to record, or null while a field is missing or wrong. */
    fun purchase(today: LocalDate): TeaPurchase? {
        val grams = parsedGrams?.takeIf { priceError == null && dateError(today) == null }
        return grams?.let { TeaPurchase(it, parsedCents, date) }
    }
}

@Suppress("LongParameterList") // a field's parts
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes suffix: Int,
    @StringRes error: Int?,
    tag: String,
    @StringRes hint: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(FIELD_MAX)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = UmamiSpacing.XSmall)
            .testTag(tag),
        label = { Text(stringResource(label)) },
        suffix = { Text(stringResource(suffix)) },
        supportingText = (error ?: hint)?.let { { Text(stringResource(it)) } },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** *Kaufdatum* with Material's date picker, which offers no day after [today] (as SCR-23). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDay(
    date: LocalDate,
    today: LocalDate,
    @StringRes error: Int?,
    alertKey: String?,
    onDateChange: (LocalDate) -> Unit,
) {
    val picking = rememberAlertGate(alertKey)
    Column(Modifier.fillMaxWidth().padding(bottom = UmamiSpacing.XSmall)) {
        Text(
            text = stringResource(R.string.collection_purchase_date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = picking::open,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PURCHASE_SHEET_DATE_TAG),
        ) {
            Text(GermanFormat.day(date))
        }
        error?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    AlertGated(picking) {
        val todayMillis = today.utcMillis()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.utcMillis().coerceAtMost(todayMillis),
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
                ) { Text(stringResource(R.string.collection_purchase_date_ok)) }
            },
            dismissButton = {
                TextButton(onClick = picking::close) { Text(stringResource(R.string.collection_purchase_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** The picker counts days in UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Always fully expanded; under reduce motion it starts expanded, so it appears without the slide (03 §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberPurchaseSheetState(reduceMotion: Boolean): SheetState {
    val density = LocalDensity.current
    return remember(reduceMotion) {
        SheetState(
            skipPartiallyExpanded = true,
            positionalThreshold = { with(density) { PositionalThreshold.toPx() } },
            velocityThreshold = { with(density) { VelocityThreshold.toPx() } },
            initialValue = if (reduceMotion) SheetValue.Expanded else SheetValue.Hidden,
        )
    }
}

// Material's own defaults for modal sheets (BottomSheetDefaults is internal for these).
private val PositionalThreshold = 56.dp
private val VelocityThreshold = 125.dp

/** Long enough for „1.234,50 €" or „10.000 g"; [GermanNumber] refuses anything longer anyway. */
private const val FIELD_MAX = 12
