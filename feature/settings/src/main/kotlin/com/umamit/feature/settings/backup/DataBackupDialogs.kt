package com.umamit.feature.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupPreview
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.settings.R
import java.time.ZoneId
import kotlin.math.roundToLong

/** The open dialog of SCR-26, if any. */
@Composable
internal fun BackupDialogs(state: DataBackupUiState, actions: DataBackupActions) {
    when (val dialog = state.dialog) {
        null -> Unit

        is BackupDialog.SizeNote -> SizeNoteDialog(dialog.estimate, actions)

        is BackupDialog.Replace -> ReplaceDialog(dialog.preview, state, actions)

        is BackupDialog.Undo -> UndoDialog(dialog.preview, actions)

        // One branch, so the notice takes the delete dialog's window (D-133 Q7).
        is BackupDialog.Wipe, BackupDialog.Wiping -> WipeDialog(dialog, state, actions)
    }
}

/** „Sicherung erstellen" · „Etwa 18 MB · 142 Sessions, 23 Tees, 61 Fotos. …" · *Abbrechen* / *Weiter* (BAK-01 AC1). */
@Composable
private fun SizeNoteDialog(estimate: BackupEstimate, actions: DataBackupActions) {
    val megabytes = (estimate.bytes.toDouble() / BYTES_PER_MB).roundToLong().coerceAtLeast(1L).toInt()
    BackupAlert(
        title = stringResource(R.string.backup_size_title),
        body = stringResource(
            R.string.backup_size_body,
            megabytes,
            countsLine(estimate.sessions, estimate.teas, estimate.photos),
        ),
        confirm = stringResource(R.string.backup_continue),
        onConfirm = actions.confirmSizeNote,
        onDismiss = actions.dismissDialog,
    )
}

/**
 * „Sicherung einspielen?" with the file's date in its own zone and its counts, the typed „ERSETZEN" and *Ersetzen*,
 * enabled only by exactly that word (BAK-02 AC3).
 */
@Composable
private fun ReplaceDialog(preview: BackupPreview, state: DataBackupUiState, actions: DataBackupActions) {
    val zone = runCatching { ZoneId.of(preview.zoneId) }.getOrDefault(ZoneId.systemDefault())
    val talkBack = stringResource(R.string.backup_confirm_talkback, state.confirmWords.replace)
    BackupAlert(
        title = stringResource(R.string.backup_preview_title),
        body = stringResource(
            R.string.backup_preview_body,
            GermanFormat.day(preview.createdAt, zone),
            countsLine(preview.sessions, preview.teas, preview.photos),
        ),
        confirm = stringResource(R.string.backup_replace),
        confirmEnabled = state.canReplace,
        fullWidthWindow = true,
        onConfirm = actions.replace,
        onDismiss = actions.dismissDialog,
    ) {
        ConfirmWordField(
            value = state.confirmText,
            onValueChange = actions.typeConfirm,
            hint = stringResource(R.string.backup_confirm_hint, state.confirmWords.replace),
            talkBack = talkBack,
            tag = DataBackupTags.CONFIRM_FIELD,
        )
    }
}

/**
 * The field for a typed confirmation word („ERSETZEN", „LÖSCHEN"): capitals, no autocorrect. [hint] („Tippe … zur
 * Bestätigung") is its own small line above a field with no label, at every font scale (D-133 Q1).
 */
@Composable
internal fun ConfirmWordField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    talkBack: String,
    tag: String,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = UmamiSpacing.Small)) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // The field's own description says the same to TalkBack.
            modifier = Modifier.clearAndSetSemantics {},
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = UmamiSpacing.XSmall)
                .semantics { contentDescription = talkBack }
                .testTag(tag),
        )
    }
}

/** „Import rückgängig machen?" with what is lost since the import (BAK-02 AC7, D-130 Q6). */
@Composable
internal fun UndoDialog(preview: UndoPreview, actions: DataBackupActions) {
    // A zero count is dropped from the sentence (PO, 2026-11-18); with neither, the sentence says so.
    val sessions = pluralStringResource(R.plurals.backup_new_sessions, preview.sessions, preview.sessions)
    val teas = pluralStringResource(R.plurals.backup_undo_teas, preview.teas, preview.teas)
    val body = when {
        preview.sessions == 0 && preview.teas == 0 -> stringResource(R.string.backup_undo_body_none)
        preview.teas == 0 -> stringResource(R.string.backup_undo_body_one, sessions)
        preview.sessions == 0 -> stringResource(R.string.backup_undo_body_one, teas)
        else -> stringResource(R.string.backup_undo_body, sessions, teas)
    }
    BackupAlert(
        title = stringResource(R.string.backup_undo_title),
        body = body,
        confirm = stringResource(R.string.backup_undo_confirm),
        onConfirm = actions.confirmUndo,
        onDismiss = actions.dismissDialog,
    )
}

/** A dialog whose text scrolls, so the buttons stay reachable at font scale 2.0 (03 §6). */
@Composable
internal fun BackupAlert(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    fullWidthWindow: Boolean = false,
    extra: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // A text field filling a wrap-content dialog window never settles its measure in Robolectric; a full-width
        // window with the dialog's own margins looks the same on the phone (Material's 280–560 dp).
        modifier = if (fullWidthWindow) Modifier.padding(horizontal = UmamiSpacing.Large) else Modifier,
        properties = DialogProperties(usePlatformDefaultWidth = !fullWidthWindow),
        title = { Text(title) },
        text = {
            // Only the text scrolls: the typed field below it stays in view.
            Column {
                Text(
                    text = body,
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                extra()
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
    )
}

/** The full-screen step with no back and no way past it (03 SCR-26): it outlasts a tap on the navigation bar. */
@Composable
internal fun BusyDialog(text: String, modifier: Modifier = Modifier) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
            Box(contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(UmamiSpacing.Medium),
                    modifier = Modifier.padding(UmamiSpacing.SideMargin),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = text,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private const val BYTES_PER_MB = 1024.0 * 1024.0
