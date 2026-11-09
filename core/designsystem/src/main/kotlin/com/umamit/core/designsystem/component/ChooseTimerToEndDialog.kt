package com.umamit.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.umamit.core.designsystem.R
import com.umamit.core.designsystem.theme.UmamiSpacing

/**
 * „Welchen Timer beenden?" (TMR-21, D-108): while two timers are active, a third start asks which one ends. One button
 * per active timer in the slot order, named by its tea ([timers]; null: „Schnelltimer"), and *Abbrechen*. The host
 * ends the chosen one as by *Beenden & neu starten* ([onEnd] with its index), then starts the new timer. Back and a
 * tap outside cancel. Hosts remove it at once when an alert starts (rule 8).
 *
 * TalkBack reads each button as „*Hisui* beenden" („Schnelltimer beenden").
 */
@Composable
fun ChooseTimerToEndDialog(
    timers: List<String?>,
    onEnd: (index: Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier,
        title = { Text(stringResource(R.string.ds_choose_timer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Small)) {
                Text(
                    text = stringResource(R.string.ds_choose_timer_text),
                    modifier = Modifier.padding(bottom = UmamiSpacing.XSmall),
                )
                timers.forEachIndexed { index, teaName ->
                    val name = teaName ?: stringResource(R.string.ds_choose_timer_quick)
                    val description = stringResource(R.string.ds_choose_timer_end_description, name)
                    // A full-width button per timer: a long tea name wraps instead of crowding the row.
                    OutlinedButton(
                        onClick = { onEnd(index) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = description },
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ds_finish_active_cancel)) }
        },
    )
}
