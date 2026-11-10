package com.umamit.feature.collection.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.Tea
import com.umamit.feature.collection.R

/**
 * *Warnung* (one row, one switch: TalkBack reads „Warnung", its detail and its state) and *Knapp ab*: the tea's own
 * threshold in whole grams, empty for SET-16's, which the field then shows (D-110) and its hint names. The input is
 * written when it is done (*Fertig*, the field losing focus or the detail closing), and only a valid one (empty, or
 * 1–500 g); anything else says so at the field and keeps the stored value (D-108). Both stay while the warning is
 * off: the threshold still sets *Knapp* (INV-05) and the setup's marker (BRW-08).
 */
@Composable
internal fun StockWarning(tea: TeaDetail, onWarning: (Boolean) -> Unit, onThreshold: (grams: Int?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.XSmall)) {
        HorizontalDivider(thickness = UmamiSpacing.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
        WarningRow(tea.stockWarning, onWarning)
        ThresholdField(tea, onThreshold)
    }
}

/**
 * *Warnung*: the row is the switch, one node with the role Switch and its state. The switch moves at the tap, and the
 * stored value [on] corrects it once it comes back (motion review v0.5.0 N3).
 */
@Composable
private fun WarningRow(on: Boolean, onWarning: (Boolean) -> Unit) {
    var checked by remember(on) { mutableStateOf(on) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_MIN_HEIGHT)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    checked = it
                    onWarning(it)
                },
            )
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
        Switch(checked = checked, onCheckedChange = null)
    }
}

/**
 * *Knapp ab*: whole grams, written once the input is done and only when valid, so „12" never stores „1" on the way
 * and a final „600" keeps the stored value, not the „60" typed before it (D-108); anything else said at the field.
 * The field always shows the value in effect (D-110): without one of the tea's own, SET-16's in the quieter
 * colour while the field is not focused; focused, it is empty with the greyed placeholder, as before.
 */
@Composable
private fun ThresholdField(tea: TeaDetail, onThreshold: (grams: Int?) -> Unit) {
    var text by rememberSaveable(tea.id) { mutableStateOf(tea.ownLowStockGrams?.toString().orEmpty()) }
    // The input not yet written: set by a keystroke, cleared once it is committed.
    var pending by rememberSaveable(tea.id) { mutableStateOf(false) }
    val invalid = text.isNotEmpty() && text.toIntOrNull() !in Tea.OWN_LOW_STOCK_GRAMS
    var focused by remember { mutableStateOf(false) }
    // No threshold of its own: the global one is the value in effect.
    val showsGlobal = text.isEmpty() && !focused
    val focus = LocalFocusManager.current
    val transformation = rememberGlobalTransformation(showsGlobal, tea.globalLowStockGrams)
    val commit by rememberUpdatedState {
        if (pending) {
            pending = false
            val grams = text.toIntOrNull()
            when {
                text.isEmpty() -> onThreshold(null)
                grams != null && grams in Tea.OWN_LOW_STOCK_GRAMS -> onThreshold(grams)
                else -> Unit // refused: the stored value stays
            }
        }
    }
    DisposableEffect(Unit) { onDispose { commit() } }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(THRESHOLD_DIGITS)
            if (digits != text) {
                text = digits
                pending = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = UmamiSpacing.XSmall)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) commit()
            }
            .defaultValueState(showsGlobal)
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
        visualTransformation = transformation,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedTextColor = with(MaterialTheme.colorScheme) { if (showsGlobal) onSurfaceVariant else onSurface },
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                commit()
                focus.clearFocus()
            },
        ),
    )
}

/** SET-16's value in the quieter colour while [showsGlobal], else none; allocated once per value and colour. */
@Composable
private fun rememberGlobalTransformation(showsGlobal: Boolean, globalGrams: Int): VisualTransformation {
    val quieter = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = remember(globalGrams, quieter) { ShowsGlobal(globalGrams.toString(), quieter) }
    return if (showsGlobal) shown else VisualTransformation.None
}

/** TalkBack tells the shown default from an own value: the state „Vorgabe" while [showsGlobal] (D-110). */
@Composable
private fun Modifier.defaultValueState(showsGlobal: Boolean): Modifier {
    val state = stringResource(R.string.collection_tea_stock_threshold_default_state)
    return semantics { if (showsGlobal) stateDescription = state }
}

/**
 * Shows SET-16's value in an empty, unfocused field without ever making it the field's text: the input stays what was
 * typed, so „25" is never „1525" (D-110).
 */
internal data class ShowsGlobal(private val shown: String, private val colour: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(
        AnnotatedString(shown, SpanStyle(color = colour)),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = 0

            override fun transformedToOriginal(offset: Int) = 0
        },
    )
}

/** „500": the longest valid *Knapp ab*; a fourth digit is never taken. */
private const val THRESHOLD_DIGITS = 3

/** The switch row's minimum height, as REZEPTE's rows. */
private val ROW_MIN_HEIGHT = 56.dp
