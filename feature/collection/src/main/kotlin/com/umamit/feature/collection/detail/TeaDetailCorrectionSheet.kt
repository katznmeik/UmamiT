package com.umamit.feature.collection.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.umamit.core.common.AppLanguage
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.domain.input.GermanNumber
import com.umamit.feature.collection.R
import com.umamit.feature.collection.editor.DateField
import java.time.LocalDate
import kotlin.math.abs

const val CORRECTION_SHEET_DELTA_TAG = "correction_sheet_delta"
const val CORRECTION_SHEET_DATE_TAG = "correction_sheet_date"
const val CORRECTION_SHEET_SAVE_TAG = "correction_sheet_save"
const val CORRECTION_SHEET_DELETE_TAG = "correction_sheet_delete"

/**
 * „Korrektur bearbeiten" (INV-03, SCR-29, D-136 item 11): the correction [edited] as its signed difference in g
 * („+5", „−3"; a plus sign may be left out, never 0) and its day (never after [today]). *Speichern* (filled) stays off
 * while a field is wrong, and the field says why; it calls [onSave] at once and slides the sheet out. *Löschen* (text,
 * error colour) slides it out and calls [onDelete], whose host asks first. Back, the scrim or a drag close it without
 * saving. Like every sheet it never covers an alert: the host removes it in the frame an alert starts (rule 8); its
 * date picker gives way to an alert other than [alertKey]'s.
 */
@Suppress("LongParameterList") // the edited entry, the day, the alert and the three actions
@Composable
internal fun CorrectionSheet(
    edited: StockEntry,
    today: LocalDate,
    alertKey: String?,
    onSave: (signedDeltaGrams: Double, day: LocalDate) -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    StockSheetFrame(onDismissRequest) { leaving, leave ->
        var deltaText by rememberSaveable { mutableStateOf(CorrectionForm.format(edited.deltaGrams)) }
        var day by rememberSaveable { mutableStateOf(edited.day) }
        val form = CorrectionForm(deltaText, day)
        val delta = form.delta(today)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UmamiSpacing.SideMargin)
                .padding(bottom = UmamiSpacing.Medium)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.collection_correction_edit_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(UmamiSpacing.Medium))
            val error = form.deltaError
            OutlinedTextField(
                value = deltaText,
                onValueChange = { deltaText = it.take(FIELD_MAX) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = UmamiSpacing.XSmall)
                    .testTag(CORRECTION_SHEET_DELTA_TAG),
                label = { Text(stringResource(R.string.collection_correction_delta)) },
                suffix = { Text(stringResource(R.string.collection_purchase_grams_unit)) },
                supportingText = {
                    Text(stringResource(error ?: R.string.collection_correction_delta_hint))
                },
                isError = error != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            DateField(
                label = R.string.collection_correction_date,
                date = day,
                latest = today,
                tag = CORRECTION_SHEET_DATE_TAG,
                alertKey = alertKey,
                confirm = R.string.collection_purchase_date_ok,
                cancel = R.string.collection_purchase_cancel,
                modifier = Modifier.padding(bottom = UmamiSpacing.XSmall),
                error = form.dateError(today),
                onDateChange = { day = it },
            )
            Spacer(Modifier.height(UmamiSpacing.Small))
            StockSheetButtons(
                save = R.string.collection_stock_edit_save,
                saveEnabled = delta != null && !leaving,
                saveTag = CORRECTION_SHEET_SAVE_TAG,
                onSave = {
                    if (delta != null && !leaving) {
                        onSave(delta, day)
                        leave()
                    }
                },
                onCancel = { if (!leaving) leave() },
                onDelete = {
                    if (!leaving) {
                        leave()
                        onDelete()
                    }
                },
                deleteTag = CORRECTION_SHEET_DELETE_TAG,
            )
        }
    }
}

/**
 * What „Korrektur bearbeiten" holds: the signed difference as typed and the day. „+5", „5", „-3" and „−3" are read,
 * with [GermanNumber.parseGrams]'s rules for the amount; 0, a blank field or anything else is no difference.
 */
internal data class CorrectionForm(val delta: String, val day: LocalDate) {
    private val parsed: Double? = parse(delta)

    @get:StringRes
    val deltaError: Int?
        get() = R.string.collection_correction_delta_invalid.takeIf { parsed == null }

    @StringRes
    fun dateError(today: LocalDate): Int? = R.string.collection_purchase_date_future.takeIf { day.isAfter(today) }

    /** The signed difference to save, or null while a field is wrong. */
    fun delta(today: LocalDate): Double? = parsed?.takeIf { dateError(today) == null }

    companion object {
        /** „+5", „−3", „+37,5" („+37.5" in English): [grams] to 0,1 g with its sign for the field. */
        fun format(grams: Double, language: AppLanguage = AppLanguage.current()): String =
            (if (grams < 0) "−" else "+") + editableGrams(abs(grams), language)

        /** The signed grams of [text]; null for 0, blank or not a weight. */
        fun parse(text: String): Double? {
            val trimmed = text.trim()
            val signed = trimmed.firstOrNull()?.let { it in SIGNS } == true
            val sign = if (signed && trimmed.first() != '+') -1.0 else 1.0
            val amount = if (signed) trimmed.drop(1).trimStart() else trimmed
            return GermanNumber.parseGrams(amount)?.let { it * sign }
        }

        /** Plus, the hyphen-minus a keyboard types and the minus sign the history shows. */
        private const val SIGNS = "+-−"
    }
}

/** Long enough for „−10.000"; [GermanNumber] refuses anything longer anyway. */
private const val FIELD_MAX = 12
