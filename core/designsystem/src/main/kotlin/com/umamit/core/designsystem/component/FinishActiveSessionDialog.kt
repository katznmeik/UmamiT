package com.umamit.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.umamit.core.designsystem.R

/**
 * The second-session question (TMR-21, 03 §5): „Erst die *Hisui*-Sitzung
 * beenden?" with *Beenden & neu starten* and *Abbrechen*; for a second quick
 * timer ([teaName] null) „Schnelltimer beenden und neu starten?" (D-098). Back and a tap
 * outside cancel. Hosts remove it at once when an alert starts (rule 8).
 */
@Composable
fun FinishActiveSessionDialog(
    teaName: String?,
    onRestart: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (teaName == null) {
        stringResource(R.string.ds_finish_active_quick_title)
    } else {
        stringResource(R.string.ds_finish_active_title, teaName)
    }
    // The tea's name in italics, as in 03 §5.
    val start = teaName?.let { text.indexOf(it) } ?: -1
    val title = buildAnnotatedString {
        append(text)
        if (start >= 0) addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, start + teaName.orEmpty().length)
    }
    AlertDialog(
        onDismissRequest = onCancel,
        modifier = modifier,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onRestart) { Text(stringResource(R.string.ds_finish_active_restart)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.ds_finish_active_cancel)) }
        },
    )
}
