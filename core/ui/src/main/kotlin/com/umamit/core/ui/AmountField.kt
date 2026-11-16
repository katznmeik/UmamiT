package com.umamit.core.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType

/**
 * An amount field of an editor: the leaf in grams ([decimal]: digits, comma and point, up to „100,0") or the water in
 * ml (digits only, up to „3000"), with its [unit] as a suffix and an [error] under it. The input is filtered as typed.
 */
@Suppress("LongParameterList") // an amount field's parts
@Composable
fun AmountField(
    value: String,
    @StringRes label: Int,
    @StringRes unit: Int,
    @StringRes error: Int?,
    decimal: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            onValueChange(
                if (decimal) {
                    typed.filter { it.isDigit() || it == ',' || it == '.' }.take(LEAF_CHARS)
                } else {
                    typed.filter(Char::isDigit).take(WATER_DIGITS)
                },
            )
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

/** „100,0" has five characters; „3000" four digits. */
private const val LEAF_CHARS = 5
private const val WATER_DIGITS = 4
