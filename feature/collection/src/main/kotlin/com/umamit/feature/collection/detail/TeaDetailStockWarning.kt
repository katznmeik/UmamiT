package com.umamit.feature.collection.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.Tea
import com.umamit.feature.collection.R

/**
 * *Warnung* (one row, one switch: TalkBack reads „Warnung", its detail and its state) and *Knapp ab*: the tea's own
 * threshold in whole grams, empty for SET-16's, which the field's hint names. Each valid input is written at once
 * (empty, or 1–500 g); anything else says so at the field and keeps the stored value (D-108). Both stay while the
 * warning is off: the threshold still sets *Knapp* (INV-05) and the setup's marker (BRW-08).
 */
@Composable
internal fun StockWarning(tea: TeaDetail, onWarning: (Boolean) -> Unit, onThreshold: (grams: Int?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.XSmall)) {
        HorizontalDivider(thickness = UmamiSpacing.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
        WarningRow(tea.stockWarning, onWarning)
        ThresholdField(tea, onThreshold)
    }
}

/** *Warnung*: the row is the switch, one node with the role Switch and its state. */
@Composable
private fun WarningRow(on: Boolean, onWarning: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(value = on, role = Role.Switch, onValueChange = onWarning)
            .padding(vertical = UmamiSpacing.XSmall)
            .testTag(TEA_DETAIL_STOCK_WARNING_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.collection_tea_stock_warning),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.collection_tea_stock_warning_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(UmamiSpacing.Small))
        Switch(checked = on, onCheckedChange = null)
    }
}

/** *Knapp ab*: whole grams, each valid input written at once, anything else said at the field. */
@Composable
private fun ThresholdField(tea: TeaDetail, onThreshold: (grams: Int?) -> Unit) {
    var text by rememberSaveable(tea.id) { mutableStateOf(tea.ownLowStockGrams?.toString().orEmpty()) }
    val invalid = text.isNotEmpty() && text.toIntOrNull() !in Tea.OWN_LOW_STOCK_GRAMS
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(THRESHOLD_DIGITS)
            if (digits != text) {
                text = digits
                val grams = digits.toIntOrNull()
                if (digits.isEmpty()) {
                    onThreshold(null)
                } else if (grams != null && grams in Tea.OWN_LOW_STOCK_GRAMS) {
                    onThreshold(grams)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UmamiSpacing.XSmall)
            .testTag(TEA_DETAIL_STOCK_THRESHOLD_TAG),
        label = { Text(stringResource(R.string.collection_tea_stock_threshold)) },
        placeholder = { Text(tea.globalLowStockGrams.toString()) },
        suffix = { Text(stringResource(R.string.collection_tea_stock_threshold_unit)) },
        supportingText = {
            Text(
                if (invalid) {
                    stringResource(R.string.collection_tea_stock_threshold_error)
                } else {
                    stringResource(R.string.collection_tea_stock_threshold_hint, tea.globalLowStockGrams)
                },
            )
        },
        isError = invalid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
    )
}

/** „500": the longest valid *Knapp ab*; a fourth digit is never taken. */
private const val THRESHOLD_DIGITS = 3

/** The switch row's minimum height, as REZEPTE's rows. */
private val ROW_MIN_HEIGHT = 56.dp
