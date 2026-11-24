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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.umamit.core.data.TeaPurchase
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.domain.input.GermanNumber
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.rememberExpandedSheetState
import com.umamit.feature.collection.R
import com.umamit.feature.collection.editor.DateField
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

const val PURCHASE_SHEET_GRAMS_TAG = "purchase_sheet_grams"
const val PURCHASE_SHEET_PRICE_TAG = "purchase_sheet_price"
const val PURCHASE_SHEET_DATE_TAG = "purchase_sheet_date"
const val PURCHASE_SHEET_SAVE_TAG = "purchase_sheet_save"
const val PURCHASE_SHEET_DELETE_TAG = "purchase_sheet_delete"

/**
 * *Nachkauf erfassen* (INV-03, SCR-09 VORRAT, D-104): the grams bought, an optional price and the day, today by
 * default and never later. The fields start empty; *Erfassen* stays off until the grams are a weight
 * ([GermanNumber.parseGrams]), a price typed is an amount ([GermanNumber.parseCents]) and the day isn't after
 * [today], and each field says why. *Erfassen* calls [onSave] at once, then the sheet slides out (not under
 * reduce motion) and [onDismissRequest] closes it; *Abbrechen* slides out the same way without saving, and back,
 * the scrim or a drag close it without saving. Like every sheet it never covers an alert: the host removes it in
 * the frame an alert starts (rule 8); its date picker gives way to an alert other than [alertKey]'s.
 *
 * With [edited] it is „Kauf bearbeiten" (INV-03, SCR-29, D-136 item 11): the fields start on that purchase's grams,
 * price and day, and *Speichern* (filled) and *Löschen* (text, error colour) replace *Abbrechen* / *Erfassen*.
 * *Löschen* slides the sheet out and calls [onDelete], whose host asks first.
 */
@Composable
internal fun PurchaseSheet(
    today: LocalDate,
    alertKey: String?,
    onSave: (TeaPurchase) -> Unit,
    onDismissRequest: () -> Unit,
    edited: StockEntry? = null,
    onDelete: () -> Unit = {},
) {
    StockSheetFrame(onDismissRequest) { leaving, leave ->
        PurchaseSheetContent(
            today = today,
            alertKey = alertKey,
            leaving = leaving,
            edited = edited,
            onSave = {
                onSave(it)
                leave()
            },
            onCancel = { if (!leaving) leave() },
            onDelete = {
                if (!leaving) {
                    leave()
                    onDelete()
                }
            },
        )
    }
}

/**
 * A stock sheet's frame (*Nachkauf erfassen*, „Kauf bearbeiten", „Korrektur bearbeiten"): [content] gets whether the
 * sheet is on its way out (so a second tap writes nothing twice) and `leave`, which slides it out (not under reduce
 * motion) and then calls [onDismissRequest]. Back, the scrim or a drag close it at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockSheetFrame(
    onDismissRequest: () -> Unit,
    content: @Composable (leaving: Boolean, leave: () -> Unit) -> Unit,
) {
    val reduceMotion = UmamiTheme.reduceMotion
    val sheetState = rememberExpandedSheetState(reduceMotion)
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    val leave = {
        leaving = true
        scope.launch { if (!reduceMotion) sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        content(leaving, leave)
    }
}

/**
 * The stock sheets' buttons: *Abbrechen* and [save] for a new entry; for an edit ([onDelete] set) *Löschen* (text,
 * error colour) at the start and [save] (filled) at the end.
 */
@Composable
internal fun StockSheetButtons(
    @StringRes save: Int,
    saveEnabled: Boolean,
    saveTag: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    deleteTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (onDelete == null) Arrangement.End else Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDelete == null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.collection_purchase_cancel)) }
            Spacer(Modifier.width(UmamiSpacing.XSmall))
        } else {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(deleteTag),
            ) {
                Text(stringResource(R.string.collection_stock_edit_delete))
            }
        }
        PillButton(
            text = stringResource(save),
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.testTag(saveTag),
        )
    }
}

@Suppress("LongParameterList") // the sheet's state and its three actions
@Composable
private fun PurchaseSheetContent(
    today: LocalDate,
    alertKey: String?,
    leaving: Boolean,
    edited: StockEntry?,
    onSave: (TeaPurchase) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    var gramsText by rememberSaveable { mutableStateOf(edited?.let { editableGrams(it.deltaGrams) }.orEmpty()) }
    var priceText by rememberSaveable { mutableStateOf(edited?.priceCents?.let(GermanFormat::editableEuros).orEmpty()) }
    var date by rememberSaveable { mutableStateOf(edited?.day ?: today) }
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
            text = stringResource(
                if (edited == null) R.string.collection_purchase_title else R.string.collection_purchase_edit_title,
            ),
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
        DateField(
            label = R.string.collection_purchase_date,
            date = date,
            latest = today,
            tag = PURCHASE_SHEET_DATE_TAG,
            alertKey = alertKey,
            confirm = R.string.collection_purchase_date_ok,
            cancel = R.string.collection_purchase_cancel,
            modifier = Modifier.padding(bottom = UmamiSpacing.XSmall),
            error = form.dateError(today),
            onDateChange = { date = it },
        )
        Spacer(Modifier.height(UmamiSpacing.Small))
        StockSheetButtons(
            save = if (edited == null) R.string.collection_purchase_save else R.string.collection_stock_edit_save,
            saveEnabled = purchase != null && !leaving,
            saveTag = PURCHASE_SHEET_SAVE_TAG,
            onSave = { purchase?.let(onSave) },
            onCancel = onCancel,
            onDelete = onDelete.takeIf { edited != null },
            deleteTag = PURCHASE_SHEET_DELETE_TAG,
        )
    }
}

/** Grams for an editable field: „50", „37,5" (no thousands separators, which [GermanNumber] reads as well). */
internal fun editableGrams(grams: Double): String =
    BigDecimal.valueOf(grams).stripTrailingZeros().toPlainString().replace('.', ',')

/**
 * What the sheet holds: the typed grams and price and the chosen day. A blank price is no price; blank grams are
 * missing but not yet an error.
 */
internal data class PurchaseForm(val grams: String, val price: String, val date: LocalDate) {
    private val parsedGrams: Double? = GermanNumber.parseGrams(grams)
    private val parsedCents: Long? = GermanNumber.parseCents(price)?.takeIf { it > 0 } // INV-08: no „0,00 €"

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

/** Long enough for „1.234,50 €" or „10.000 g"; [GermanNumber] refuses anything longer anyway. */
private const val FIELD_MAX = 12
