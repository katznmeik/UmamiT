package com.umamit.feature.brew.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.component.PillButton
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.brew.R
import java.math.BigDecimal
import java.math.RoundingMode

const val STOCK_SHEET_FIELD_TAG = "stock_sheet_field"
const val STOCK_SHEET_SAVE_TAG = "stock_sheet_save"

/**
 * „Wie viel ist noch da?" (BRW-02, INV-03, D-093 point 7; 03 SCR-04): the new total of the tea in g, with
 * ± 0,1 g steppers and a number field, opened from the setup's „… g übrig". It opens on the current stock
 * ([currentGrams], at least 0). 0 is allowed (leer); a negative, non-numeric or finer than 0,1 g input
 * is refused: *Speichern* stays off and the field says why. *Speichern* calls [onSave] with the new total
 * and the host closes the sheet in that frame; *Abbrechen*, back, the scrim or a drag close it without
 * saving ([onDismissRequest]).
 *
 * Like every sheet it never covers an alert: the host composes it inside [com.umamit.feature.brew.picker]'s
 * `AlertGate` (rule 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockSheet(currentGrams: Double, onSave: (Double) -> Unit, onDismissRequest: () -> Unit) {
    val sheetState = rememberStockSheetState(UmamiTheme.reduceMotion)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        StockSheetContent(currentGrams, onSave, onDismissRequest)
    }
}

@Composable
private fun StockSheetContent(currentGrams: Double, onSave: (Double) -> Unit, onCancel: () -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = StockInput.format(currentGrams)
        mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
    }
    val total = StockInput.parse(value.text)
    val invalid = total == null && value.text.isNotBlank()
    val setText = { text: String -> value = TextFieldValue(text, selection = TextRange(text.length)) }
    val save = { total?.let(onSave) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin)
            .padding(bottom = UmamiSpacing.Medium)
            .navigationBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.brew_setup_stock_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(UmamiSpacing.Medium))
        Row(verticalAlignment = Alignment.Top) {
            IconButton(
                onClick = { StockInput.step(value.text, up = false)?.let(setText) },
                enabled = StockInput.step(value.text, up = false) != null,
                modifier = Modifier.padding(top = STEPPER_TOP),
            ) {
                Icon(
                    painterResource(UmamiIcons.Remove),
                    contentDescription = stringResource(R.string.brew_setup_stock_less),
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag(STOCK_SHEET_FIELD_TAG),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                label = { Text(stringResource(R.string.brew_setup_stock_field)) },
                suffix = { Text(stringResource(R.string.brew_setup_stock_unit)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (invalid) R.string.brew_setup_stock_invalid else R.string.brew_setup_stock_hint,
                        ),
                    )
                },
                isError = invalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            IconButton(
                onClick = { StockInput.step(value.text, up = true)?.let(setText) },
                enabled = StockInput.step(value.text, up = true) != null,
                modifier = Modifier.padding(top = STEPPER_TOP),
            ) {
                Icon(
                    painterResource(UmamiIcons.Add),
                    contentDescription = stringResource(R.string.brew_setup_stock_more),
                )
            }
        }
        Spacer(Modifier.height(UmamiSpacing.Small))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.brew_setup_stock_cancel)) }
            Spacer(Modifier.width(UmamiSpacing.XSmall))
            PillButton(
                text = stringResource(R.string.brew_setup_stock_save),
                onClick = { save() },
                enabled = total != null,
                modifier = Modifier.testTag(STOCK_SHEET_SAVE_TAG),
            )
        }
    }
}

/**
 * The total typed into the sheet: German „23,5" (a dot works too), at most one decimal (0,1 g steps,
 * BRW-02), 0 to [MAX_GRAMS] g. Pure, so the field, the steppers and *Speichern* agree.
 */
internal object StockInput {
    /** Four digits before the comma: the largest total the field takes. */
    const val MAX_GRAMS = 9_999.9

    private val PATTERN = Regex("""\d{1,4}([.,]\d?)?""")
    private val STEP = BigDecimal("0.1")

    /** The total in g, or null when [text] is not one. */
    fun parse(text: String): Double? = text.trim().takeIf(PATTERN::matches)?.replace(',', '.')?.toDouble()

    /** „23,5": [grams] to 0,1 g, never below 0 (a stock below 0 opens as leer). */
    fun format(grams: Double): String = grams(grams.coerceIn(0.0, MAX_GRAMS))

    /**
     * The text one 0,1 g step [up] or down from [text] (from 0 when [text] is not a total), or null when that
     * step would leave 0 to [MAX_GRAMS] g.
     */
    fun step(text: String, up: Boolean): String? {
        val current = parse(text)?.toBigDecimal()?.setScale(1, RoundingMode.HALF_UP) ?: BigDecimal.ZERO.setScale(1)
        val next = if (up) current + STEP else current - STEP
        return next.takeIf { it >= BigDecimal.ZERO && it.toDouble() <= MAX_GRAMS }?.let { format(it.toDouble()) }
    }
}

/** Always fully expanded; under reduce motion it starts expanded, so it appears without the slide (03 §3.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberStockSheetState(reduceMotion: Boolean): SheetState {
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

/** Centres a 48 dp stepper on the 56 dp field below the outlined field's 8 dp label space. */
private val STEPPER_TOP = 12.dp
