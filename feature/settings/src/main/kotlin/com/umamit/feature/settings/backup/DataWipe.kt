package com.umamit.feature.settings.backup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.feature.settings.R

/**
 * SCR-26's last row, ***Alle Daten löschen*** (SET-33, destructive text button). Greyed out while a timer is active
 * (D-131 Q2) or an export runs (#346 answer 3); an import, its check and its undo cover the whole screen anyway.
 */
@Composable
internal fun WipeRow(state: DataBackupUiState, actions: DataBackupActions) {
    val detail = when {
        state.timerActive -> stringResource(R.string.backup_timer_active)
        state.export != null -> stringResource(R.string.backup_wipe_export_running)
        else -> null
    }
    ButtonBlock(detail = detail, modifier = Modifier.testTag(DataBackupTags.WIPE_ROW)) {
        TextButton(
            onClick = actions.askWipe,
            enabled = state.canAskWipe,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag(DataBackupTags.WIPE),
        ) {
            Text(stringResource(R.string.backup_wipe))
        }
    }
}

/**
 * „Alle Daten löschen?" with the hint „Tippe LÖSCHEN zur Bestätigung" and its field; *Löschen* only by exactly
 * „LÖSCHEN" (SET-33). While a safety copy exists, its own paragraph says it goes too (#346 answer 4). After *Löschen*
 * ([BackupDialog.Wiping]) the same window shows „Daten werden gelöscht. UmamiT schließt sich, …" for about a second:
 * no buttons, no back (#346 answer 2), and no second window, so the scrim does not blink (D-133 Q7). The keyboard is
 * closed before the swap.
 */
@Composable
internal fun WipeDialog(dialog: BackupDialog, state: DataBackupUiState, actions: DataBackupActions) {
    val wiping = dialog == BackupDialog.Wiping
    val body = stringResource(R.string.backup_wipe_body)
    val text = if ((dialog as? BackupDialog.Wipe)?.safetyCopy == true) {
        "$body\n\n${stringResource(R.string.backup_wipe_safety_copy)}"
    } else {
        body
    }
    AlertDialog(
        onDismissRequest = if (wiping) ({}) else actions.dismissDialog,
        // Full width with the dialog's own margins, as [BackupAlert]'s typed-word dialogs; the same while it wipes.
        properties = DialogProperties(
            dismissOnBackPress = !wiping,
            dismissOnClickOutside = !wiping,
            usePlatformDefaultWidth = false,
        ),
        modifier = Modifier.padding(horizontal = UmamiSpacing.Large),
        title = if (wiping) null else ({ Text(stringResource(R.string.backup_wipe_title)) }),
        text = {
            if (wiping) {
                Text(
                    text = stringResource(R.string.backup_wipe_notice),
                    modifier = Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(DataBackupTags.WIPING),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column {
                    Text(
                        text = text,
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ConfirmWordField(
                        value = state.confirmText,
                        onValueChange = actions.typeConfirm,
                        hint = stringResource(R.string.backup_wipe_confirm_hint, state.confirmWords.wipe),
                        talkBack = stringResource(R.string.backup_wipe_confirm_talkback, state.confirmWords.wipe),
                        tag = DataBackupTags.WIPE_FIELD,
                    )
                }
            }
        },
        confirmButton = {
            if (!wiping) {
                val focus = LocalFocusManager.current
                TextButton(
                    onClick = {
                        // The keyboard leaves before the notice takes the window.
                        focus.clearFocus(force = true)
                        actions.confirmWipe()
                    },
                    enabled = state.canWipe,
                ) {
                    Text(stringResource(R.string.backup_wipe_confirm))
                }
            }
        },
        dismissButton = if (wiping) {
            null
        } else {
            { TextButton(onClick = actions.dismissDialog) { Text(stringResource(R.string.backup_cancel)) } }
        },
    )
}
