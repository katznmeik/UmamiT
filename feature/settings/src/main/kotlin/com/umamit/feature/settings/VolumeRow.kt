package com.umamit.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.UserSettings
import kotlin.math.roundToInt

/**
 * SET-27 *Lautstärke* (SCR-25, D-098): the title with the value („100 %"), a slider of 10–100 % in 10 % steps
 * and the hint. While a finger drags, the slider and the value follow it; the setting is written once, when the
 * drag ends (TalkBack's adjustments end at once), so a drag is one write, not ten. A stored value outside
 * 10–100 shows clamped, as the player plays it.
 */
@Composable
internal fun VolumeRow(percent: Int, onChange: (Int) -> Unit) {
    var dragged by remember { mutableStateOf<Float?>(null) }
    // The value of the drag that ended, shown until the stored value changes: without it the slider showed
    // the old value for the moment of the write and jumped back and forth (v0.3.0-rc.1).
    var written by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(percent) { written = null }
    val shown = dragged?.roundToInt() ?: written ?: percent.coerceIn(MIN_VOLUME, MAX_VOLUME)
    val valueText = stringResource(R.string.settings_volume_percent, shown)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_volume),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = dragged ?: shown.toFloat(),
            onValueChange = { dragged = it },
            onValueChangeFinished = {
                dragged?.roundToInt()?.let { value ->
                    written = value.takeIf { it != percent }
                    onChange(value)
                }
                dragged = null
            },
            valueRange = MIN_VOLUME.toFloat()..MAX_VOLUME.toFloat(),
            steps = VOLUME_STEPS,
            // A quiet inactive track: the washi container stands out like a warning on the dark theme.
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SettingsTags.VOLUME)
                .semantics { stateDescription = valueText },
        )
        Text(
            text = stringResource(R.string.settings_volume_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MIN_VOLUME = UserSettings.MIN_ALERT_VOLUME_PERCENT
private const val MAX_VOLUME = UserSettings.MAX_ALERT_VOLUME_PERCENT

/** The values between the ends: 20, 30 … 90 (SET-27). */
private const val VOLUME_STEPS = (MAX_VOLUME - MIN_VOLUME) / UserSettings.ALERT_VOLUME_STEP_PERCENT - 1
