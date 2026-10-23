package com.umamit.feature.brew.quick

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.text.clockText
import com.umamit.core.designsystem.text.spokenDuration
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.brew.R
import java.util.Locale

/** Test tag of the picker's total, „1:30". */
const val QUICK_PICKER_TOTAL_TAG = "quick_picker_total"

/**
 * The duration picker of SCR-07: hours, minutes and seconds, each with − and
 * +, within 5 s – 24 h and in the PRF-04 increments ([QuickDuration.step]).
 * The draft survives a configuration change; *Übernehmen* applies it.
 */
@Composable
internal fun QuickDurationPicker(initialSeconds: Int, onApply: (Int) -> Unit, onDismiss: () -> Unit) {
    var seconds by rememberSaveable { mutableIntStateOf(initialSeconds) }
    val (h, m, s) = QuickDuration.parts(seconds)
    val total = spokenDuration(seconds)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.brew_quick_picker_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = clockText(seconds),
                    style = MaterialTheme.typography.headlineMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .semantics {
                            contentDescription = total
                            liveRegion = LiveRegionMode.Polite
                        }
                        .testTag(QUICK_PICKER_TOTAL_TAG),
                )
                Spacer(Modifier.height(UmamiSpacing.Small))
                listOf(
                    PickerField(
                        QuickDuration.Field.HOURS,
                        R.string.brew_quick_picker_hours,
                        h.toString(),
                        R.string.brew_quick_picker_hours_less,
                        R.string.brew_quick_picker_hours_more,
                    ),
                    PickerField(
                        QuickDuration.Field.MINUTES,
                        R.string.brew_quick_picker_minutes,
                        "%02d".format(Locale.GERMAN, m),
                        R.string.brew_quick_picker_minutes_less,
                        R.string.brew_quick_picker_minutes_more,
                    ),
                    PickerField(
                        QuickDuration.Field.SECONDS,
                        R.string.brew_quick_picker_seconds,
                        "%02d".format(Locale.GERMAN, s),
                        R.string.brew_quick_picker_seconds_less,
                        R.string.brew_quick_picker_seconds_more,
                    ),
                ).forEach { field ->
                    PickerRow(field, seconds) { seconds = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(seconds) }) { Text(stringResource(R.string.brew_quick_picker_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.brew_quick_picker_cancel)) }
        },
    )
}

private class PickerField(
    val field: QuickDuration.Field,
    val label: Int,
    val value: String,
    val less: Int,
    val more: Int,
)

@Composable
private fun PickerRow(field: PickerField, seconds: Int, onChange: (Int) -> Unit) {
    val down = QuickDuration.step(seconds, field.field, up = false)
    val up = QuickDuration.step(seconds, field.field, up = true)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(field.label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { down?.let(onChange) }, enabled = down != null) {
            Icon(painterResource(UmamiIcons.Remove), contentDescription = stringResource(field.less))
        }
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodyLarge.tabular(),
            textAlign = TextAlign.Center,
            // The total above is the spoken value.
            modifier = Modifier
                .widthIn(min = 40.dp)
                .clearAndSetSemantics {},
        )
        IconButton(onClick = { up?.let(onChange) }, enabled = up != null) {
            Icon(painterResource(UmamiIcons.Add), contentDescription = stringResource(field.more))
        }
    }
}

/** Lining, tabular figures: the digits do not jitter while they change. */
private fun androidx.compose.ui.text.TextStyle.tabular() = copy(fontFeatureSettings = "lnum, tnum")
