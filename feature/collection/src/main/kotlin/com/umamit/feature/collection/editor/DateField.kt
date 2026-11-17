package com.umamit.feature.collection.editor

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.AlertGated
import com.umamit.core.ui.GermanFormat
import com.umamit.core.ui.PastDatePickerDialog
import com.umamit.core.ui.rememberAlertGate
import com.umamit.feature.collection.R
import java.time.LocalDate

/**
 * A day field with Material's date picker (the editor's *Kaufdatum*, *Geöffnet am*, *Haltbar bis* and the purchase
 * sheet's *Kaufdatum*): the day on a button, the picker offering no day after [latest] when set. [onClear] adds an
 * *Entfernen* ([tag]`Clear`) for an optional day; [error] shows at the field in its colour, else [hint]. The
 * picker gives way to an alert other than [alertKey]'s (rule 8).
 */
@Suppress("LongParameterList") // a date field's parts
@Composable
internal fun DateField(
    @StringRes label: Int,
    date: LocalDate?,
    latest: LocalDate?,
    tag: String,
    alertKey: String?,
    @StringRes confirm: Int,
    @StringRes cancel: Int,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
    @StringRes hint: Int? = null,
    onClear: (() -> Unit)? = null,
) {
    val picking = rememberAlertGate(alertKey)
    Column(modifier) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UmamiSpacing.XSmall),
        ) {
            OutlinedButton(onClick = picking::open, modifier = Modifier.weight(1f).testTag(tag)) {
                Text(date?.let(GermanFormat::day) ?: stringResource(R.string.tea_editor_date_unset))
            }
            if (date != null && onClear != null) {
                TextButton(onClick = onClear, modifier = Modifier.testTag(tag + CLEAR_SUFFIX)) {
                    Text(stringResource(R.string.tea_editor_date_clear))
                }
            }
        }
        (error ?: hint)?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    AlertGated(picking) {
        PastDatePickerDialog(
            initial = date,
            latest = latest,
            confirmLabel = stringResource(confirm),
            cancelLabel = stringResource(cancel),
            onPick = onDateChange,
            onDismiss = picking::close,
        )
    }
}

internal const val CLEAR_SUFFIX = "Clear"
