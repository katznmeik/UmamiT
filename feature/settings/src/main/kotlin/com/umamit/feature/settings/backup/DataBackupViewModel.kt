package com.umamit.feature.settings.backup

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umamit.core.backup.archive.BackupEstimate
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.backup.archive.BackupFormat
import com.umamit.core.backup.archive.BackupPreview
import com.umamit.core.backup.archive.BackupProgress
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.common.WallClock
import com.umamit.core.common.today
import com.umamit.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The export's progress bar (BAK-01 AC2): [done] of [total] photos, both null while the rows are read. */
data class ExportProgress(val done: Int?, val total: Int?)

/** The dialogs of SCR-26. */
sealed interface BackupDialog {
    /** „Etwa 18 MB · 142 Sessions, 23 Tees, 61 Fotos …" with *Weiter* (BAK-01 AC1). */
    data class SizeNote(val estimate: BackupEstimate) : BackupDialog

    /** „Sicherung einspielen?" with the typed ERSETZEN (BAK-02 AC3). */
    data class Replace(val preview: BackupPreview) : BackupDialog

    /** „Import rückgängig machen?" with the counts (BAK-02 AC7). */
    data class Undo(val preview: UndoPreview) : BackupDialog
}

/** A system picker the screen opens (BAK-01 AC1, BAK-02 AC1, BAK-04). */
sealed interface BackupPick {
    data class CreateBackup(val fileName: String) : BackupPick

    data object OpenBackup : BackupPick

    data class CreateJournal(val fileName: String) : BackupPick
}

/** A step that covers the screen with no back (03 SCR-26). */
enum class BackupBusy { CHECKING, IMPORTING, UNDOING }

/**
 * SCR-26's state.
 *
 * @property undo the valid safety copy's counts and end; null hides *Import rückgängig machen*.
 * @property message a snackbar's text, until [DataBackupViewModel.messageShown].
 * @property imported an import is done: Start shows its snackbar (until [DataBackupViewModel.importHandled]).
 */
data class DataBackupUiState(
    val lastBackup: LastBackup? = null,
    val reminderEnabled: Boolean = true,
    val timerActive: Boolean = false,
    val undo: UndoPreview? = null,
    val dialog: BackupDialog? = null,
    val confirmText: String = "",
    val export: ExportProgress? = null,
    val busy: BackupBusy? = null,
    val pick: BackupPick? = null,
    @param:StringRes val message: Int? = null,
    val saveFailed: Boolean = false,
    val imported: ImportedBackup? = null,
) {
    /** *Ersetzen* is enabled only by exactly „ERSETZEN" (BAK-02 AC3, like SET-33's „LÖSCHEN"). */
    val canReplace: Boolean get() = confirmText == CONFIRM_WORD

    companion object {
        const val CONFIRM_WORD = "ERSETZEN"
    }
}

/**
 * *Daten & Sicherung* (SCR-26): export (BAK-01), import and its undo (BAK-02), the journal CSV (BAK-04) and SET-20,
 * through [DataBackup] only. An export runs while the screen is shown: leaving it, or *Abbrechen*, stops the write
 * and deletes the incomplete document. A checked file's staging is closed on every way out.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // one function per action of SCR-26 and its pickers' results
class DataBackupViewModel @Inject constructor(private val backup: DataBackup, private val clock: WallClock) :
    ViewModel() {
    private val mutable = MutableStateFlow(DataBackupUiState())
    val state: StateFlow<DataBackupUiState> = mutable.asStateFlow()

    private var exportJob: Job? = null
    private var staged: StagedBackup? = null

    init {
        viewModelScope.launch { backup.lastBackup.collect { last -> mutable.update { it.copy(lastBackup = last) } } }
        viewModelScope.launch {
            backup.reminderEnabled.collect { on -> mutable.update { it.copy(reminderEnabled = on) } }
        }
        viewModelScope.launch { backup.timerActive.collect { on -> mutable.update { it.copy(timerActive = on) } } }
        refresh()
    }

    /** Reads the safety copy's counts again: on every resume, and after an import or an undo. */
    fun refresh() {
        viewModelScope.launch { readUndo() }
    }

    private suspend fun readUndo(): UndoPreview? = backup.undoPreview().also { preview ->
        mutable.update { it.copy(undo = preview) }
    }

    // --- Sicherung erstellen (BAK-01) ---

    fun createBackup() {
        viewModelScope.launch {
            val estimate = backup.estimate()
            mutable.update {
                if (estimate == null) {
                    it.copy(message = R.string.backup_failed)
                } else {
                    it.copy(dialog = BackupDialog.SizeNote(estimate))
                }
            }
        }
    }

    /** *Weiter* on the size note: the picker with `UmamiT-Sicherung-2026-11-18.umamit`. */
    fun confirmSizeNote() {
        val name = "UmamiT-Sicherung-${clock.today()}.${BackupFormat.EXTENSION}"
        mutable.update { it.copy(dialog = null, pick = BackupPick.CreateBackup(name)) }
    }

    /** The picker's document, or null when it was left without one (then nothing happens). */
    fun onBackupDocument(document: String?) {
        if (document == null || exportJob?.isActive == true) return
        mutable.update { it.copy(export = ExportProgress(null, null)) }
        exportJob = viewModelScope.launch {
            var done = false
            try {
                backup.export(document).collect { event ->
                    when (event) {
                        is BackupExport.Progress -> mutable.update { it.copy(export = event.progress.toBar()) }

                        is BackupExport.Done -> {
                            done = true
                            mutable.update { it.copy(message = R.string.backup_saved) }
                        }

                        is BackupExport.Failed -> mutable.update { it.copy(message = R.string.backup_failed) }
                    }
                }
            } finally {
                mutable.update { it.copy(export = null) }
                if (!done) withContext(NonCancellable) { backup.delete(document) }
            }
        }
    }

    /** *Abbrechen* under the progress bar: „Sicherung abgebrochen" (BAK-01 AC2). */
    fun cancelExport() {
        val job = exportJob?.takeIf { it.isActive } ?: return
        job.cancel()
        mutable.update { it.copy(export = null, message = R.string.backup_cancelled) }
    }

    private fun BackupProgress.toBar(): ExportProgress = when (this) {
        is BackupProgress.Photos -> ExportProgress(done, total)
        BackupProgress.Reading, BackupProgress.Finishing -> mutable.value.export ?: ExportProgress(null, null)
    }

    // --- Sicherung einspielen (BAK-02) ---

    fun importBackup() {
        mutable.update { it.copy(pick = BackupPick.OpenBackup) }
    }

    /** The picked file, or null when the picker was left (then nothing is checked). */
    fun onImportDocument(document: String?) {
        if (document == null) return
        mutable.update { it.copy(busy = BackupBusy.CHECKING) }
        viewModelScope.launch {
            when (val checked = backup.check(document)) {
                is BackupCheck.Refused -> mutable.update { it.copy(busy = null, message = checked.messageRes) }

                is BackupCheck.Valid -> {
                    staged?.close()
                    staged = checked.staged
                    mutable.update {
                        it.copy(busy = null, dialog = BackupDialog.Replace(checked.preview), confirmText = "")
                    }
                }
            }
        }
    }

    fun typeConfirm(text: String) {
        mutable.update { it.copy(confirmText = text) }
    }

    /** *Ersetzen*: only with exactly „ERSETZEN" typed; then the full-screen „Sicherung wird eingespielt …". */
    fun replace() {
        val current = mutable.value
        val toRestore = staged
        if (current.dialog !is BackupDialog.Replace || !current.canReplace || toRestore == null) return
        staged = null
        mutable.update { it.copy(dialog = null, confirmText = "", busy = BackupBusy.IMPORTING) }
        viewModelScope.launch {
            val result = try {
                toRestore.restore()
            } finally {
                toRestore.close()
            }
            when (result) {
                is RestoreResult.Done -> mutable.update {
                    it.copy(busy = null, imported = ImportedBackup(result.preview.sessions, result.preview.teas))
                }

                is RestoreResult.Refused -> mutable.update { it.copy(busy = null, message = result.refusal.messageRes) }

                RestoreResult.NoSafetyCopy -> mutable.update { it.copy(busy = null) }
            }
            readUndo()
        }
    }

    /** Start has taken the import's snackbar. */
    fun importHandled() {
        mutable.update { it.copy(imported = null) }
    }

    // --- Import rückgängig machen (BAK-02 AC7) ---

    /** The dialog with fresh counts; a copy that expired meanwhile hides the row instead. */
    fun askUndo() {
        viewModelScope.launch {
            val preview = readUndo() ?: return@launch
            mutable.update { it.copy(dialog = BackupDialog.Undo(preview)) }
        }
    }

    fun confirmUndo() {
        if (mutable.value.dialog !is BackupDialog.Undo) return
        mutable.update { it.copy(dialog = null, busy = BackupBusy.UNDOING) }
        viewModelScope.launch {
            val message = when (val result = backup.undo()) {
                is RestoreResult.Done -> R.string.backup_undone
                is RestoreResult.Refused -> result.refusal.messageRes
                RestoreResult.NoSafetyCopy -> null
            }
            mutable.update { it.copy(busy = null, message = message ?: it.message) }
            readUndo()
        }
    }

    // --- Journal als CSV (BAK-04) ---

    fun exportJournal() {
        mutable.update { it.copy(pick = BackupPick.CreateJournal("UmamiT-Journal-${clock.today()}.csv")) }
    }

    /** The picker's document, or null when it was left; a failed write deletes it. */
    fun onJournalDocument(document: String?) {
        if (document == null) return
        viewModelScope.launch {
            val ok = backup.exportJournal(document)
            if (!ok) withContext(NonCancellable) { backup.delete(document) }
            mutable.update { it.copy(message = if (ok) R.string.backup_csv_saved else R.string.backup_csv_failed) }
        }
    }

    // --- An Sicherung erinnern (SET-20) ---

    /** Stores the switch; the schedule follows the stored value (BAK-06, #339). */
    fun setReminder(on: Boolean) {
        viewModelScope.launch {
            val ok = backup.setReminderEnabled(on)
            mutable.update { it.copy(saveFailed = !ok) }
        }
    }

    // --- The screen's one-shot parts ---

    /** Closes the open dialog; a checked file's staging is deleted. */
    fun dismissDialog() {
        if (mutable.value.dialog is BackupDialog.Replace) closeStaged()
        mutable.update { it.copy(dialog = null, confirmText = "") }
    }

    fun pickHandled() {
        mutable.update { it.copy(pick = null) }
    }

    fun messageShown() {
        mutable.update { it.copy(message = null) }
    }

    private fun closeStaged() {
        staged?.close()
        staged = null
    }

    override fun onCleared() {
        closeStaged()
    }
}
