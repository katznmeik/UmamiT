package com.umamit.feature.journal.session

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.umamit.feature.journal.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** *Datum* (SCR-17): the system date picker, offering no day after [latest] (a session is never in the future). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionDateDialog(date: LocalDate, latest: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val latestMillis = latest.utcMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date.utcMillis().coerceAtMost(latestMillis),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis

            override fun isSelectableYear(year: Int): Boolean = year <= latest.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.session_editor_pick_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.session_editor_pick_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

/** *Uhrzeit* (SCR-17): the system time picker on the 24 h dial, opened at [hour]:[minute]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SessionTimeDialog(hour: Int, minute: Int, onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_editor_time)) },
        text = { TimePicker(state = state, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(state.hour, state.minute)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.session_editor_pick_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.session_editor_pick_cancel)) }
        },
    )
}

/** Material's date picker counts days as UTC midnights. */
private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
