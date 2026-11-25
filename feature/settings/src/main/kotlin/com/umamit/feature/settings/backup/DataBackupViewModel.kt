package com.umamit.feature.settings.backup

import android.content.res.Resources
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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

    /** „Alle Daten löschen?" with the typed LÖSCHEN (SET-33); [safetyCopy] adds its sentence (#346 answer 4). */
    data class Wipe(val safetyCopy: Boolean) : BackupDialog

    /** „Daten werden gelöscht. UmamiT schließt sich, …" for about a second, then the wipe (#346 answer 2). */
    data object Wiping : BackupDialog
}

/** A system picker the screen opens (BAK-01 AC1, BAK-02 AC1, BAK-04). */
sealed interface BackupPick {
    data class CreateBackup(val fileName: String) : BackupPick

    data object OpenBackup : BackupPick

    data class CreateJournal(val fileName: String) : BackupPick
}

/**
 * The words to type before *Ersetzen* ([replace], BAK-02 AC3) and *Löschen* ([wipe], SET-33), in the language of the
 * resources the screen shows (GEN-13): the hints name these same words, so what the hint asks for is what enables the
 * button, whatever the JVM's locale is (under *System* with a phone list such as [Français, English] the resources
 * are English while the JVM's locale is French).
 */
data class ConfirmWords(val replace: String, val wipe: String) {
    companion object {
        /** The German words, until the screen hands over its own ([DataBackupViewModel.useConfirmWords]). */
        val GERMAN = ConfirmWords(replace = "ERSETZEN", wipe = "LÖSCHEN")

        /** The words of [resources]: `backup_confirm_word` and `backup_wipe_word`. */
        fun of(resources: Resources): ConfirmWords = ConfirmWords(
            replace = resources.getString(R.string.backup_confirm_word),
            wipe = resources.getString(R.string.backup_wipe_word),
        )
    }
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
    val confirmWords: ConfirmWords = ConfirmWords.GERMAN,
) {
    /** *Ersetzen* is enabled only by exactly [ConfirmWords.replace], „ERSETZEN" / „REPLACE" (BAK-02 AC3). */
    val canReplace: Boolean get() = confirmText == confirmWords.replace

    /**
     * *Alle Daten löschen* is offered: no timer (D-131 Q2), no export, no import, check or undo (#346 answer 3). Its
     * dialog's *Löschen* needs exactly „LÖSCHEN" besides ([canWipe]).
     */
    val canAskWipe: Boolean get() = !timerActive && export == null && busy == null

    /** *Löschen* is enabled only by exactly [ConfirmWords.wipe], „LÖSCHEN": no trimming, no case folding (SET-33). */
    val canWipe: Boolean get() = dialog is BackupDialog.Wipe && confirmText == confirmWords.wipe && canAskWipe

    companion object {
        /** How long „Daten werden gelöscht. …" shows before the wipe (#346 answer 2). */
        const val WIPE_NOTICE_MS = 1_000L

        /** A check shorter than this shows no cover (D-133 Q6). */
        const val CHECK_COVER_DELAY_MS = 250L

        /** Once shown, the check's cover stays at least this long (D-133 Q6). */
        const val CHECK_COVER_MIN_MS = 400L

        /** The import's cover goes by itself this long after Start took the snackbar, if the screen is still shown. */
        const val IMPORT_COVER_FALLBACK_MS = 2_000L
    }
}

/**
 * *Daten & Sicherung* (SCR-26): export (BAK-01), import and its undo (BAK-02), the journal CSV (BAK-04) and SET-20
 * through [DataBackup], and *Alle Daten löschen* (SET-33) through [AppDataWipe]. An export runs while the screen is
 * shown: leaving it, or *Abbrechen*, stops the write and deletes the incomplete document. A checked file's staging
 * is closed on every way out.
 */
@HiltViewModel
@Suppress("TooManyFunctions") // one function per action of SCR-26 and its pickers' results
class DataBackupViewModel @Inject constructor(
    private val backup: DataBackup,
    private val clock: WallClock,
    private val wipe: AppDataWipe,
) : ViewModel() {
    private val mutable = MutableStateFlow(DataBackupUiState())
    val state: StateFlow<DataBackupUiState> = mutable.asStateFlow()

    private var exportJob: Job? = null
    private var checkJob: Job? = null
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
        if (checking) return
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

    /**
     * The picked file, or null when the picker was left (then nothing is checked). „Sicherung wird geprüft …" covers
     * the screen only once the check has taken [DataBackupUiState.CHECK_COVER_DELAY_MS], then for at least
     * [DataBackupUiState.CHECK_COVER_MIN_MS], so a quick check never flashes it (D-133 Q6).
     */
    fun onImportDocument(document: String?) {
        if (document == null || checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            val check = async { backup.check(document) }
            val checked = withTimeoutOrNull(DataBackupUiState.CHECK_COVER_DELAY_MS) { check.await() } ?: run {
                mutable.update { it.copy(busy = BackupBusy.CHECKING) }
                coroutineScope {
                    launch { delay(DataBackupUiState.CHECK_COVER_MIN_MS) }
                    check.await()
                }
            }
            when (checked) {
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

    /** A check runs, its cover not shown yet: nothing else opens over the dialog it brings. */
    private val checking: Boolean get() = checkJob?.isActive == true

    fun typeConfirm(text: String) {
        mutable.update { it.copy(confirmText = text) }
    }

    /** The words the screen's hints name, from its resources (GEN-13): the ones [replace] and [confirmWipe] need. */
    fun useConfirmWords(words: ConfirmWords) {
        mutable.update { it.copy(confirmWords = words) }
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
                // The cover stays until Start shows: the host leaves this screen under it (D-133 Q6).
                is RestoreResult.Done -> mutable.update {
                    it.copy(imported = ImportedBackup(result.preview.sessions, result.preview.teas))
                }

                is RestoreResult.Refused -> mutable.update { it.copy(busy = null, message = result.refusal.messageRes) }

                RestoreResult.NoSafetyCopy -> mutable.update { it.copy(busy = null) }
            }
            readUndo()
        }
    }

    /**
     * Start has taken the import's snackbar; the screen is left under the cover. Should it stay on screen after all,
     * the cover goes after [DataBackupUiState.IMPORT_COVER_FALLBACK_MS], so it never stays without an end.
     */
    fun importHandled() {
        mutable.update { it.copy(imported = null) }
        viewModelScope.launch {
            delay(DataBackupUiState.IMPORT_COVER_FALLBACK_MS)
            mutable.update { if (it.busy == BackupBusy.IMPORTING) it.copy(busy = null) else it }
        }
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

    // --- Alle Daten löschen (SET-33, D-131) ---

    /** The dialog, with the safety copy's sentence while *Import rückgängig machen* is offered (#346 answer 4). */
    fun askWipe() {
        if (!mutable.value.canAskWipe || mutable.value.dialog != null || checking) return
        viewModelScope.launch {
            val safetyCopy = readUndo() != null
            mutable.update {
                if (!it.canAskWipe || it.dialog != null) {
                    it
                } else {
                    it.copy(dialog = BackupDialog.Wipe(safetyCopy), confirmText = "")
                }
            }
        }
    }

    /**
     * *Löschen*: only with exactly „LÖSCHEN" typed and nothing running; „Daten werden gelöscht. …" for about a second,
     * then the wipe, once. Done, the system ends the app and the notice stays; refused, the dialog closes with a
     * snackbar.
     */
    fun confirmWipe() {
        if (!mutable.value.canWipe) return
        mutable.update { it.copy(dialog = BackupDialog.Wiping, confirmText = "") }
        viewModelScope.launch {
            delay(DataBackupUiState.WIPE_NOTICE_MS)
            // A timer started meanwhile wins (D-131 Q2); the seam checks again at the moment of the wipe.
            val result = if (mutable.value.timerActive) WipeResult.TimerActive else wipe.wipe()
            when (result) {
                WipeResult.Wiped -> Unit

                WipeResult.TimerActive -> mutable.update {
                    it.copy(dialog = null, message = R.string.backup_timer_active)
                }

                WipeResult.Failed -> mutable.update { it.copy(dialog = null, message = R.string.backup_wipe_failed) }
            }
        }
    }

    // --- The screen's one-shot parts ---

    /** Closes the open dialog; a checked file's staging is deleted. The wipe's notice cannot be closed. */
    fun dismissDialog() {
        if (mutable.value.dialog == BackupDialog.Wiping) return
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
