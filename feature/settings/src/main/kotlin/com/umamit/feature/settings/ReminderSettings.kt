package com.umamit.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.umamit.core.model.UserSettings

/**
 * The daily low-stock reminder in *Brühen & Vorrat* (SET-28, SET-29, INV-11, D-098): the switch, and while it
 * is on the time („18:00"), which opens the time picker.
 */
@Composable
internal fun ReminderRows(settings: UserSettings, actions: SettingsActions, openDialog: (SettingsDialog) -> Unit) {
    SwitchRow(
        title = stringResource(R.string.settings_low_stock_reminder),
        checked = settings.lowStockReminder,
        onCheckedChange = actions.setLowStockReminder,
        detail = stringResource(R.string.settings_low_stock_reminder_detail),
    )
    if (settings.lowStockReminder) {
        val minute = settings.lowStockReminderTime
        SettingsRow(
            title = stringResource(R.string.settings_low_stock_reminder_time),
            value = stringResource(
                R.string.settings_low_stock_reminder_time_value,
                minute / MINUTES_PER_HOUR,
                minute % MINUTES_PER_HOUR,
            ),
            onClick = { openDialog(SettingsDialog.ReminderTime) },
            modifier = Modifier.testTag(SettingsTags.REMINDER_TIME),
        )
    }
}

/**
 * SET-29 *Uhrzeit* of the daily low-stock reminder (INV-11, D-098): Material 3's time picker on the 24 h dial,
 * opened at [minuteOfDay]; *OK* stores the time picked, *Abbrechen* keeps the old one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(minuteOfDay: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / MINUTES_PER_HOUR,
        initialMinute = minuteOfDay % MINUTES_PER_HOUR,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_low_stock_reminder_time)) },
        text = { TimePicker(state = state, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(stringResource(R.string.settings_time_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_time_cancel)) }
        },
    )
}

private const val MINUTES_PER_HOUR = 60
