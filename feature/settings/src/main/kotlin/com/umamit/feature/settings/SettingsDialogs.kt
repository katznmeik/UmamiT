package com.umamit.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.AlertLength
import com.umamit.core.timer.alert.AlertSound
import com.umamit.core.timer.alert.PreAlertSound

/** The SET-10 choices: off, then 5 to 30 s in steps of 5. */
private val PRE_ALERT_CHOICES = listOf(0, 5, 10, 15, 20, 25, 30)

/**
 * Alarmton (SET-09): the five tones by their German names, the Klangschale first (D-045). A pick
 * is stored at once; *Anhören* plays the picked tone once ([com.umamit.core.timer.alert.TonePreview]).
 */
@Composable
internal fun ToneDialog(selected: String, actions: SettingsActions, onDismiss: () -> Unit) {
    val current = AlertSound.tone(selected)
    // Closed by *Fertig*, outside or by an alert (rule 8): the preview ends with the picker.
    DisposableEffect(Unit) { onDispose(actions.stopPreview) }
    ChoiceDialog(
        title = stringResource(R.string.settings_tone),
        onDismiss = onDismiss,
        extraButton = {
            TextButton(onClick = actions.previewTone) { Text(stringResource(R.string.settings_tone_preview)) }
        },
    ) {
        AlertSound.TONES.forEach { tone ->
            ChoiceRow(
                stringResource(tone.label()),
                selected = tone == current,
                onClick = {
                    actions.stopPreview()
                    actions.setTone(tone.key)
                },
            )
        }
    }
}

/**
 * Vorwarnton (SET-26): the four pre-alert sounds and „Ting (leise)" by their German names, in the
 * order of 04 §9.2. A pick is stored at once; *Anhören* plays the picked sound once, as long as it
 * rings ([com.umamit.core.timer.alert.TonePreview]).
 */
@Composable
internal fun PreAlertSoundDialog(selected: String, actions: SettingsActions, onDismiss: () -> Unit) {
    val current = PreAlertSound.of(selected)
    // Closed by *Fertig*, outside or by an alert (rule 8): the preview ends with the picker.
    DisposableEffect(Unit) { onDispose(actions.stopPreview) }
    ChoiceDialog(
        title = stringResource(R.string.settings_pre_alert_sound),
        onDismiss = onDismiss,
        extraButton = {
            TextButton(onClick = actions.previewPreAlertSound) { Text(stringResource(R.string.settings_tone_preview)) }
        },
    ) {
        PreAlertSound.entries.forEach { choice ->
            ChoiceRow(
                stringResource(choice.sound.label()),
                selected = choice == current,
                onClick = {
                    actions.stopPreview()
                    actions.setPreAlertSound(choice.key)
                },
            )
        }
    }
}

/** Vorwarnung (SET-10). */
@Composable
internal fun PreAlertDialog(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(title = stringResource(R.string.settings_pre_alert), onDismiss = onDismiss) {
        PRE_ALERT_CHOICES.forEach { seconds ->
            val label = if (seconds == 0) {
                stringResource(R.string.settings_pre_alert_off)
            } else {
                stringResource(R.string.settings_pre_alert_seconds, seconds)
            }
            ChoiceRow(label, selected = seconds == selected, onClick = { onSelect(seconds) })
        }
    }
}

/** Alarmdauer (SET-14): Kurz, Normal, Lang with what each means. */
@Composable
internal fun AlertLengthDialog(selected: AlertLength, onSelect: (AlertLength) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(title = stringResource(R.string.settings_alert_length), onDismiss = onDismiss) {
        AlertLength.entries.forEach { length ->
            val detail = when (length) {
                AlertLength.SHORT -> R.string.settings_alert_length_short_detail
                AlertLength.NORMAL -> R.string.settings_alert_length_normal_detail
                AlertLength.LONG -> R.string.settings_alert_length_long_detail
            }
            ChoiceRow(
                label = stringResource(length.label()),
                detail = stringResource(detail),
                selected = length == selected,
                onClick = { onSelect(length) },
            )
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    onDismiss: () -> Unit,
    extraButton: @Composable () -> Unit = {},
    choices: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .verticalScroll(rememberScrollState()),
            ) { choices() }
        },
        dismissButton = extraButton,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = UmamiSpacing.MinTouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = UmamiSpacing.Small)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
