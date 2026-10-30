package com.umamit.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.AlertLength
import com.umamit.core.timer.alert.AlertSound
import com.umamit.core.timer.alert.PreAlertSound
import kotlin.math.roundToInt

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

/** SET-16's range: 0–500 g in steps of 5 (D-097). */
private const val LOW_STOCK_STEP_GRAMS = 5
private const val LOW_STOCK_MAX_GRAMS = 500

/**
 * „Knapp ab" (SET-16, D-097): the threshold below which a tea is *Knapp* (INV-05), with − and + in steps of
 * 5 g between 0 and 500 g; each step is stored at once, like the other pickers.
 */
@Composable
internal fun LowStockDialog(grams: Double, onChange: (Double) -> Unit, onDismiss: () -> Unit) {
    // On the 5 g grid, as the repository stores it (SET-16).
    val current = ((grams / LOW_STOCK_STEP_GRAMS).roundToInt() * LOW_STOCK_STEP_GRAMS).coerceIn(0, LOW_STOCK_MAX_GRAMS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_low_stock)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_low_stock_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = UmamiSpacing.Small),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onChange((current - LOW_STOCK_STEP_GRAMS).coerceAtLeast(0).toDouble()) },
                        enabled = current > 0,
                    ) {
                        Icon(
                            painterResource(UmamiIcons.Remove),
                            contentDescription = stringResource(R.string.settings_low_stock_less),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_low_stock_grams, current),
                        modifier = Modifier
                            .widthIn(min = LOW_STOCK_VALUE_MIN_WIDTH)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = {
                            onChange((current + LOW_STOCK_STEP_GRAMS).coerceAtMost(LOW_STOCK_MAX_GRAMS).toDouble())
                        },
                        enabled = current < LOW_STOCK_MAX_GRAMS,
                    ) {
                        Icon(
                            painterResource(UmamiIcons.Add),
                            contentDescription = stringResource(R.string.settings_low_stock_more),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

private val LOW_STOCK_VALUE_MIN_WIDTH = 96.dp
