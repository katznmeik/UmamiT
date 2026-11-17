package com.umamit.core.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material's date picker for a day that is never after [latest] (D-123): the catalog sheet's and the purchase
 * sheet's *Kaufdatum*, the tea editor's days, the session's *Datum*. `latest == null` sets no limit. It opens at
 * [initial], else at [latest], never after [latest]. [confirmLabel] calls [onPick] with the chosen day, then
 * [onDismiss]; the labels are the caller's strings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDatePickerDialog(
    initial: LocalDate?,
    latest: LocalDate?,
    confirmLabel: String,
    cancelLabel: String,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestMillis = latest?.utcMillis()
    val initialMillis = (initial ?: latest)?.utcMillis()?.let { millis ->
        if (latestMillis == null) millis else millis.coerceAtMost(latestMillis)
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                latestMillis == null || utcTimeMillis <= latestMillis

            override fun isSelectableYear(year: Int): Boolean = latest == null || year <= latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(it.utcDate()) }
                    onDismiss()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    ) {
        DatePicker(state = state)
    }
}

/** Material's date picker counts days as UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.utcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
