package com.umamit.feature.settings.backup

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * „Alle Daten löschen?" with the field „Tippe LÖSCHEN zur Bestätigung"; *Löschen* only by exactly „LÖSCHEN" (SET-33).
 * While a safety copy exists, its own paragraph says it goes too (#346 answer 4).
 */
@Composable
internal fun WipeDialog(dialog: BackupDialog.Wipe, state: DataBackupUiState, actions: DataBackupActions) {
    val body = stringResource(R.string.backup_wipe_body)
    val talkBack = stringResource(R.string.backup_wipe_confirm_talkback)
    BackupAlert(
        title = stringResource(R.string.backup_wipe_title),
        body = if (dialog.safetyCopy) "$body\n\n${stringResource(R.string.backup_wipe_safety_copy)}" else body,
        confirm = stringResource(R.string.backup_wipe_confirm),
        confirmEnabled = state.canWipe,
        fullWidthWindow = true,
        onConfirm = actions.confirmWipe,
        onDismiss = actions.dismissDialog,
    ) {
        ConfirmWordField(
            value = state.confirmText,
            onValueChange = actions.typeConfirm,
            hint = stringResource(R.string.backup_wipe_confirm_hint),
            talkBack = talkBack,
            tag = DataBackupTags.WIPE_FIELD,
        )
    }
}

/** „Daten werden gelöscht. UmamiT schließt sich, …" for about a second: no buttons, no back (#346 answer 2). */
@Composable
internal fun WipingNotice() {
    AlertDialog(
        onDismissRequest = {},
        // The same window as the delete dialog it replaces: full width with the dialog's own margins.
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
        modifier = Modifier.padding(horizontal = UmamiSpacing.Large).testTag(DataBackupTags.WIPING),
        confirmButton = {},
        text = {
            Text(
                text = stringResource(R.string.backup_wipe_notice),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}
