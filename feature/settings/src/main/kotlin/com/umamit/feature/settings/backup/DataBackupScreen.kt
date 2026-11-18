package com.umamit.feature.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umamit.core.backup.archive.BackupFormat
import com.umamit.core.designsystem.component.BackAction
import com.umamit.core.designsystem.component.ScreenScaffold
import com.umamit.core.designsystem.component.SectionHeader
import com.umamit.core.designsystem.theme.UmamiSpacing
import com.umamit.core.ui.GermanFormat
import com.umamit.feature.settings.R
import com.umamit.feature.settings.SwitchRow
import java.time.ZoneId

/** Test tags of SCR-26. */
object DataBackupTags {
    const val CREATE = "BackupCreate"
    const val PROGRESS = "BackupProgress"
    const val IMPORT = "BackupImport"
    const val UNDO = "BackupUndo"
    const val CSV = "BackupCsv"
    const val CONFIRM_FIELD = "BackupConfirmField"
    const val BUSY = "BackupBusy"
}

/** What the controls of SCR-26 do. */
class DataBackupActions(
    val createBackup: () -> Unit = {},
    val confirmSizeNote: () -> Unit = {},
    val cancelExport: () -> Unit = {},
    val importBackup: () -> Unit = {},
    val typeConfirm: (String) -> Unit = {},
    val replace: () -> Unit = {},
    val askUndo: () -> Unit = {},
    val confirmUndo: () -> Unit = {},
    val exportJournal: () -> Unit = {},
    val setReminder: (Boolean) -> Unit = {},
    val dismissDialog: () -> Unit = {},
    val messageShown: () -> Unit = {},
)

/**
 * *Daten & Sicherung* (SCR-26) with its view model and the three system pickers (BAK-01 AC1, BAK-02 AC1, BAK-04).
 * [onImportFinish] gets a finished import: the host opens Start with its snackbar (BAK-02 AC6). [alertKey] is the alert
 * playing now: no dialog is composed over it (rule 8).
 */
@Composable
fun DataBackupRoute(
    onBack: () -> Unit,
    onImportFinish: (ImportedBackup) -> Unit,
    alertKey: String?,
    modifier: Modifier = Modifier,
    viewModel: DataBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE),
    ) { uri -> viewModel.onBackupDocument(uri?.toString()) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportDocument(uri?.toString())
    }
    val createJournal = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME_TYPE)) {
        viewModel.onJournalDocument(it?.toString())
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(state.pick) {
        val pick = state.pick ?: return@LaunchedEffect
        viewModel.pickHandled()
        when (pick) {
            is BackupPick.CreateBackup -> createBackup.launch(pick.fileName)

            // Any type: a provider rarely knows `.umamit`; the reader checks the content (05 §8).
            BackupPick.OpenBackup -> openBackup.launch(arrayOf(ANY_TYPE))

            is BackupPick.CreateJournal -> createJournal.launch(pick.fileName)
        }
    }
    val currentOnImportFinish by rememberUpdatedState(onImportFinish)
    LaunchedEffect(state.imported) {
        val imported = state.imported ?: return@LaunchedEffect
        viewModel.importHandled()
        currentOnImportFinish(imported)
    }
    DataBackupScreen(
        state = state,
        alerting = alertKey != null,
        onBack = onBack,
        modifier = modifier,
        actions = DataBackupActions(
            createBackup = viewModel::createBackup,
            confirmSizeNote = viewModel::confirmSizeNote,
            cancelExport = viewModel::cancelExport,
            importBackup = viewModel::importBackup,
            typeConfirm = viewModel::typeConfirm,
            replace = viewModel::replace,
            askUndo = viewModel::askUndo,
            confirmUndo = viewModel::confirmUndo,
            exportJournal = viewModel::exportJournal,
            setReminder = viewModel::setReminder,
            dismissDialog = viewModel::dismissDialog,
            messageShown = viewModel::messageShown,
        ),
    )
}

/**
 * SCR-26 without state: SICHERUNG (the last backup, *Sicherung erstellen* or its progress, *Sicherung einspielen*,
 * *Import rückgängig machen*, *An Sicherung erinnern*), EXPORT (*Journal als CSV*) and GERÄTE-BACKUP. [zone] formats
 * the device's dates (a backup's own date uses the file's zone).
 */
@Composable
fun DataBackupScreen(
    state: DataBackupUiState,
    alerting: Boolean,
    onBack: () -> Unit,
    actions: DataBackupActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    val currentMessageShown by rememberUpdatedState(actions.messageShown)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        currentMessageShown()
        snackbar.showSnackbar(message)
    }
    Box(modifier) {
        ScreenScaffold(
            title = stringResource(R.string.backup_title),
            navigationIcon = { BackAction(onBack) },
            verticalArrangement = Arrangement.Top,
        ) {
            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.settings_save_failed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            BackupSection(state, actions, zone)
            SectionHeader(title = stringResource(R.string.backup_section_export), modifier = Modifier.fillMaxWidth())
            ButtonBlock(detail = stringResource(R.string.backup_csv_detail)) {
                OutlinedButton(onClick = actions.exportJournal, modifier = Modifier.testTag(DataBackupTags.CSV)) {
                    Text(stringResource(R.string.backup_csv))
                }
            }
            SectionHeader(title = stringResource(R.string.backup_section_device), modifier = Modifier.fillMaxWidth())
            Text(
                text = stringResource(R.string.backup_device_body),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    // Rule 8: an alert removes an open dialog in its first frame; the view model keeps it for afterwards.
    if (!alerting) BackupDialogs(state, actions)
    state.busy?.let { BusyCover(it) }
}

@Composable
private fun BackupSection(state: DataBackupUiState, actions: DataBackupActions, zone: ZoneId) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = stringResource(R.string.backup_section_backup), modifier = Modifier.fillMaxWidth())
        Text(
            text = lastBackupLine(state.lastBackup, zone),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        ButtonBlock {
            val export = state.export
            if (export == null) {
                Button(onClick = actions.createBackup, modifier = Modifier.testTag(DataBackupTags.CREATE)) {
                    Text(stringResource(R.string.backup_create))
                }
            } else {
                ExportProgressBar(export, onCancel = actions.cancelExport)
            }
        }
        val timerDetail = stringResource(R.string.backup_timer_active).takeIf { state.timerActive }
        ButtonBlock(detail = timerDetail) {
            OutlinedButton(
                onClick = actions.importBackup,
                enabled = !state.timerActive,
                modifier = Modifier.testTag(DataBackupTags.IMPORT),
            ) {
                Text(stringResource(R.string.backup_import))
            }
        }
        state.undo?.let { undo ->
            val until = stringResource(
                R.string.backup_undo_until,
                GermanFormat.day(undo.availableUntil, zone),
                GermanFormat.time(java.time.Instant.ofEpochMilli(undo.availableUntil).atZone(zone)),
            )
            ButtonBlock(detail = timerDetail ?: until) {
                TextButton(
                    onClick = actions.askUndo,
                    enabled = !state.timerActive,
                    modifier = Modifier.testTag(DataBackupTags.UNDO),
                ) {
                    Text(stringResource(R.string.backup_undo))
                }
            }
        }
        SwitchRow(
            title = stringResource(R.string.backup_reminder),
            checked = state.reminderEnabled,
            onCheckedChange = actions.setReminder,
            detail = stringResource(R.string.backup_reminder_detail),
        )
    }
}

/** „Letzte Sicherung: 3. Nov. 2026 · 12 neue Sessions" or „Noch keine Sicherung" (D-130 Q12). */
@Composable
private fun lastBackupLine(last: LastBackup?, zone: ZoneId): String = if (last == null) {
    stringResource(R.string.backup_never)
} else {
    stringResource(
        R.string.backup_last,
        GermanFormat.day(last.atMs, zone),
        pluralStringResource(R.plurals.backup_new_sessions, last.newSessions, last.newSessions),
    )
}

/** The progress bar in place of *Sicherung erstellen*, with *Abbrechen* (BAK-01 AC2). */
@Composable
private fun ExportProgressBar(progress: ExportProgress, onCancel: () -> Unit) {
    val done = progress.done
    val total = progress.total
    val line = if (done != null && total != null) {
        stringResource(R.string.backup_progress_photos, done, total)
    } else {
        stringResource(R.string.backup_progress)
    }
    val talkBack = if (done != null && total != null) {
        stringResource(R.string.backup_progress_photos_talkback, done, total)
    } else {
        line
    }
    Column(modifier = Modifier.fillMaxWidth().testTag(DataBackupTags.PROGRESS)) {
        // One node for TalkBack: „Sicherung wird erstellt, 12 von 61 Fotos" (03 SCR-26), not one per count.
        val fraction = if (done != null && total != null && total > 0) done.toFloat() / total else null
        val barModifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = talkBack
                progressBarRangeInfo = fraction?.let { ProgressBarRangeInfo(it, 0f..1f) }
                    ?: ProgressBarRangeInfo.Indeterminate
            }
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction }, modifier = barModifier)
        } else {
            LinearProgressIndicator(modifier = barModifier)
        }
        Text(
            text = line,
            modifier = Modifier.padding(top = UmamiSpacing.XSmall).clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onCancel) { Text(stringResource(R.string.backup_cancel)) }
    }
}

/** A button (or the progress) with an optional line below it. */
@Composable
private fun ButtonBlock(detail: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UmamiSpacing.SideMargin, vertical = UmamiSpacing.XSmall),
    ) {
        content()
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „142 Sessions, 23 Tees, 61 Fotos". */
@Composable
internal fun countsLine(sessions: Int, teas: Int, photos: Int): String = stringResource(
    R.string.backup_counts,
    pluralStringResource(R.plurals.backup_sessions, sessions, sessions),
    pluralStringResource(R.plurals.backup_teas, teas, teas),
    pluralStringResource(R.plurals.backup_photos, photos, photos),
)

/** The import's snackbar text: „Sicherung eingespielt · 142 Sessions, 23 Tees" (BAK-02 AC6). */
@Composable
internal fun importedMessage(imported: ImportedBackup): String {
    val resources = LocalResources.current
    return stringResource(
        R.string.backup_imported,
        stringResource(
            R.string.backup_counts_short,
            resources.getQuantityString(R.plurals.backup_sessions, imported.sessions, imported.sessions),
            resources.getQuantityString(R.plurals.backup_teas, imported.teas, imported.teas),
        ),
    )
}

/** „Sicherung wird geprüft …", „… eingespielt …" or the undo's: the whole screen, no back (03 SCR-26). */
@Composable
private fun BusyCover(busy: BackupBusy) {
    BusyDialog(
        text = stringResource(
            when (busy) {
                BackupBusy.CHECKING -> R.string.backup_checking
                BackupBusy.IMPORTING -> R.string.backup_importing
                BackupBusy.UNDOING -> R.string.backup_undoing
            },
        ),
        modifier = Modifier.fillMaxSize().testTag(DataBackupTags.BUSY),
    )
}

private const val CSV_MIME_TYPE = "text/csv"
private const val ANY_TYPE = "*/*"
