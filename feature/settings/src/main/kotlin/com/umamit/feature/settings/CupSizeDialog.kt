package com.umamit.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umamit.core.designsystem.icon.UmamiIcons
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.model.UserSettings

private const val CUP_SIZE_STEP_ML = UserSettings.CUP_SIZE_STEP_ML
private val CUP_SIZE_VALUE_MIN_WIDTH = 96.dp

/**
 * *Tassengröße* (INV-08, D-115 Amendment 1): the water of one cup, with − and + in steps of 10 ml between 50 and
 * 500 ml; each step is stored at once, like the other pickers.
 */
@Composable
internal fun CupSizeDialog(ml: Int, onChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val current = ml.coerceIn(UserSettings.MIN_CUP_SIZE_ML, UserSettings.MAX_CUP_SIZE_ML)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cup_size)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_cup_size_detail),
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
                        onClick = {
                            onChange((current - CUP_SIZE_STEP_ML).coerceAtLeast(UserSettings.MIN_CUP_SIZE_ML))
                        },
                        enabled = current > UserSettings.MIN_CUP_SIZE_ML,
                    ) {
                        Icon(
                            painterResource(UmamiIcons.Remove),
                            contentDescription = stringResource(R.string.settings_cup_size_less),
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_cup_size_ml, current),
                        modifier = Modifier
                            .widthIn(min = CUP_SIZE_VALUE_MIN_WIDTH)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(
                        onClick = { onChange((current + CUP_SIZE_STEP_ML).coerceAtMost(UserSettings.MAX_CUP_SIZE_ML)) },
                        enabled = current < UserSettings.MAX_CUP_SIZE_ML,
                    ) {
                        Icon(
                            painterResource(UmamiIcons.Add),
                            contentDescription = stringResource(R.string.settings_cup_size_more),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}
