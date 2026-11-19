package com.umamit.feature.settings.backup

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.archive.BackupEntryCounts
import com.umamit.core.backup.archive.BackupExport
import com.umamit.core.backup.archive.BackupManifest
import com.umamit.core.backup.archive.BackupProgress
import com.umamit.core.backup.archive.BackupSummary
import com.umamit.core.backup.restore.RestoreRefusal
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.testing.FakeClock
import com.umamit.feature.settings.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.umamit.core.backup.R as BackupR

/** SCR-26's flows (BAK-01, BAK-02, BAK-04, SET-20) against a fake of the use cases, cancel included. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataBackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backup = FakeDataBackup()
    private val wipe = FakeAppDataWipe()

    /** 18 Nov 2026, 10:00 in Berlin. */
    private val clock = FakeClock(wall = 1_794_992_400_000L)

    private companion object {
        val MANIFEST = BackupManifest(
            createdAt = 1L,
            zoneId = "Europe/Berlin",
            appVersionName = "0.9.0",
            appVersionCode = 90_099L,
            schemaVersion = 9,
            settingsSchemaVersion = 3,
            counts = BackupEntryCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            checksums = emptyMap(),
        )
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The view model's owner: clearing it is the screen being left for good. */
    private val store = ViewModelStore()

    private fun TestScope.viewModel(): DataBackupViewModel {
        val factory = viewModelFactory { initializer { DataBackupViewModel(backup, clock, wipe) } }
        return ViewModelProvider.create(store, factory)[DataBackupViewModel::class].also { advanceUntilIdle() }
    }

    private fun clear() = store.clear()

    private val DataBackupViewModel.now get() = state.value

    @Test
    fun `the screen shows the last backup the switch and the timer state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertThat(viewModel.now.lastBackup).isNull()
        assertThat(viewModel.now.reminderEnabled).isTrue()

        backup.lastBackup.value = LastBackup(atMs = 1_793_696_400_000L, newSessions = 12)
        backup.timerActive.value = true
        advanceUntilIdle()

        assertThat(viewModel.now.lastBackup).isEqualTo(LastBackup(1_793_696_400_000L, 12))
        assertThat(viewModel.now.timerActive).isTrue()
    }

    @Test
    fun `create shows the size note then asks for a document with the dated name`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.createBackup()
        advanceUntilIdle()
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.SizeNote(backup.estimate!!))

        viewModel.confirmSizeNote()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.pick).isEqualTo(BackupPick.CreateBackup("UmamiT-Sicherung-2026-11-18.umamit"))
        viewModel.pickHandled()
        assertThat(viewModel.now.pick).isNull()
    }

    @Test
    fun `an unreadable estimate says the backup was not saved`() = runTest(dispatcher) {
        backup.estimate = null
        val viewModel = viewModel()

        viewModel.createBackup()
        advanceUntilIdle()

        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_failed)
    }

    @Test
    fun `cancelling the size note or the picker writes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.createBackup()
        advanceUntilIdle()

        viewModel.dismissDialog()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.pick).isNull()

        viewModel.onBackupDocument(null)
        advanceUntilIdle()
        assertThat(backup.exported).isEmpty()
        assertThat(viewModel.now.message).isNull()
    }

    @Test
    fun `an export shows its photo progress and then saved`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onBackupDocument("content://doc/1")
        advanceUntilIdle()
        assertThat(viewModel.now.export).isEqualTo(ExportProgress(done = null, total = null))
        backup.exportEvents.send(BackupExport.Progress(BackupProgress.Photos(done = 12, total = 61)))
        advanceUntilIdle()
        assertThat(viewModel.now.export).isEqualTo(ExportProgress(done = 12, total = 61))

        backup.exportEvents.send(BackupExport.Done(BackupSummary(MANIFEST, bytes = 1L)))
        backup.exportEvents.close()
        advanceUntilIdle()

        assertThat(viewModel.now.export).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_saved)
        assertThat(backup.deleted).isEmpty()
        viewModel.messageShown()
        assertThat(viewModel.now.message).isNull()
    }

    @Test
    fun `cancelling an export stops it deletes the document and says so`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onBackupDocument("content://doc/2")
        advanceUntilIdle()
        backup.exportEvents.send(BackupExport.Progress(BackupProgress.Photos(done = 3, total = 61)))
        advanceUntilIdle()

        viewModel.cancelExport()
        advanceUntilIdle()

        assertThat(viewModel.now.export).isNull()
        assertThat(backup.deleted).containsExactly("content://doc/2")
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_cancelled)
    }

    @Test
    fun `a failed export deletes the document and says it was not saved`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onBackupDocument("content://doc/3")
        advanceUntilIdle()

        backup.exportEvents.send(BackupExport.Failed(java.io.IOException("full")))
        backup.exportEvents.close()
        advanceUntilIdle()

        assertThat(viewModel.now.export).isNull()
        assertThat(backup.deleted).containsExactly("content://doc/3")
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_failed)
    }

    @Test
    fun `leaving the screen during an export deletes the document`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onBackupDocument("content://doc/4")
        advanceUntilIdle()

        clear()
        advanceUntilIdle()

        assertThat(backup.deleted).containsExactly("content://doc/4")
    }

    @Test
    fun `a refused file shows its message and opens nothing`() = runTest(dispatcher) {
        backup.checkResult = BackupCheck.Refused(BackupR.string.backup_refused_not_a_backup)
        val viewModel = viewModel()

        viewModel.importBackup()
        assertThat(viewModel.now.pick).isEqualTo(BackupPick.OpenBackup)
        viewModel.pickHandled()
        viewModel.onImportDocument("content://file/a")
        assertThat(viewModel.now.busy).isEqualTo(BackupBusy.CHECKING)
        advanceUntilIdle()

        assertThat(backup.checked).containsExactly("content://file/a")
        assertThat(viewModel.now.busy).isNull()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.message).isEqualTo(BackupR.string.backup_refused_not_a_backup)
    }

    @Test
    fun `the preview enables replace only for exactly ERSETZEN`() = runTest(dispatcher) {
        val staged = FakeStagedBackup()
        backup.checkResult = BackupCheck.Valid(FakeDataBackup.PREVIEW, staged)
        val viewModel = viewModel()
        viewModel.onImportDocument("content://file/b")
        advanceUntilIdle()
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Replace(FakeDataBackup.PREVIEW))

        for (typed in listOf("", "ersetzen", "ERSETZEN ", "ERSETZE")) {
            viewModel.typeConfirm(typed)
            assertThat(viewModel.now.canReplace).isFalse()
            viewModel.replace()
            advanceUntilIdle()
        }
        assertThat(staged.restores).isEqualTo(0)

        viewModel.typeConfirm("ERSETZEN")
        assertThat(viewModel.now.canReplace).isTrue()
    }

    @Test
    fun `cancelling the preview closes the staged backup`() = runTest(dispatcher) {
        val staged = FakeStagedBackup()
        backup.checkResult = BackupCheck.Valid(FakeDataBackup.PREVIEW, staged)
        val viewModel = viewModel()
        viewModel.onImportDocument("content://file/c")
        advanceUntilIdle()
        viewModel.typeConfirm("ERSET")

        viewModel.dismissDialog()

        assertThat(staged.closed).isEqualTo(1)
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.confirmText).isEmpty()
        assertThat(staged.restores).isEqualTo(0)
    }

    @Test
    fun `cancelling the import picker checks nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onImportDocument(null)
        advanceUntilIdle()

        assertThat(backup.checked).isEmpty()
        assertThat(viewModel.now.busy).isNull()
    }

    @Test
    fun `replace imports closes the staging and hands Start the counts`() = runTest(dispatcher) {
        val staged = FakeStagedBackup()
        backup.checkResult = BackupCheck.Valid(FakeDataBackup.PREVIEW, staged)
        val viewModel = viewModel()
        viewModel.onImportDocument("content://file/d")
        advanceUntilIdle()
        viewModel.typeConfirm("ERSETZEN")
        backup.undoPreview = UndoPreview(sessions = 0, teas = 0, availableUntil = 1_795_591_800_000L)

        viewModel.replace()
        assertThat(viewModel.now.busy).isEqualTo(BackupBusy.IMPORTING)
        assertThat(viewModel.now.dialog).isNull()
        advanceUntilIdle()

        assertThat(staged.restores).isEqualTo(1)
        assertThat(staged.closed).isEqualTo(1)
        assertThat(viewModel.now.busy).isNull()
        assertThat(viewModel.now.imported).isEqualTo(ImportedBackup(sessions = 142, teas = 23))
        assertThat(viewModel.now.undo).isEqualTo(backup.undoPreview)
        viewModel.importHandled()
        assertThat(viewModel.now.imported).isNull()
    }

    @Test
    fun `a refused replace shows its message and stays`() = runTest(dispatcher) {
        val staged = FakeStagedBackup(RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE))
        backup.checkResult = BackupCheck.Valid(FakeDataBackup.PREVIEW, staged)
        val viewModel = viewModel()
        viewModel.onImportDocument("content://file/e")
        advanceUntilIdle()
        viewModel.typeConfirm("ERSETZEN")

        viewModel.replace()
        advanceUntilIdle()

        assertThat(staged.closed).isEqualTo(1)
        assertThat(viewModel.now.imported).isNull()
        assertThat(viewModel.now.busy).isNull()
        assertThat(viewModel.now.message).isEqualTo(BackupR.string.backup_refused_timer_active)
    }

    @Test
    fun `leaving the screen with a preview open closes the staged backup`() = runTest(dispatcher) {
        val staged = FakeStagedBackup()
        backup.checkResult = BackupCheck.Valid(FakeDataBackup.PREVIEW, staged)
        val viewModel = viewModel()
        viewModel.onImportDocument("content://file/f")
        advanceUntilIdle()

        clear()

        assertThat(staged.closed).isEqualTo(1)
    }

    @Test
    fun `the undo row shows while a safety copy is valid and its dialog has the counts`() = runTest(dispatcher) {
        val preview = UndoPreview(sessions = 3, teas = 1, availableUntil = 1_795_591_800_000L)
        backup.undoPreview = preview
        val viewModel = viewModel()
        assertThat(viewModel.now.undo).isEqualTo(preview)

        viewModel.askUndo()
        advanceUntilIdle()
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Undo(preview))

        viewModel.dismissDialog()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(backup.undoCalls).isEqualTo(0)
    }

    @Test
    fun `an undo whose copy expired meanwhile hides the row`() = runTest(dispatcher) {
        backup.undoPreview = UndoPreview(sessions = 0, teas = 0, availableUntil = 1L)
        val viewModel = viewModel()
        backup.undoPreview = null

        viewModel.askUndo()
        advanceUntilIdle()

        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.undo).isNull()
    }

    @Test
    fun `confirming the undo runs it and says it is done`() = runTest(dispatcher) {
        backup.undoPreview = UndoPreview(sessions = 0, teas = 0, availableUntil = 1_795_591_800_000L)
        val gate = CompletableDeferred<Unit>()
        backup.undoGate = gate
        val viewModel = viewModel()
        viewModel.askUndo()
        advanceUntilIdle()

        viewModel.confirmUndo()
        advanceUntilIdle()
        assertThat(viewModel.now.busy).isEqualTo(BackupBusy.UNDOING)
        backup.undoPreview = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(backup.undoCalls).isEqualTo(1)
        assertThat(viewModel.now.busy).isNull()
        assertThat(viewModel.now.undo).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_undone)
    }

    @Test
    fun `a refused undo shows its message`() = runTest(dispatcher) {
        backup.undoPreview = UndoPreview(sessions = 0, teas = 0, availableUntil = 1_795_591_800_000L)
        backup.undoResult = RestoreResult.Refused(RestoreRefusal.CATALOG_REFRESH)
        val viewModel = viewModel()
        viewModel.askUndo()
        advanceUntilIdle()

        viewModel.confirmUndo()
        advanceUntilIdle()

        assertThat(viewModel.now.message).isEqualTo(BackupR.string.backup_refused_catalog_refresh)
        assertThat(viewModel.now.undo).isNotNull()
    }

    @Test
    fun `the journal csv asks for its dated name and says saved or failed`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.exportJournal()
        assertThat(viewModel.now.pick).isEqualTo(BackupPick.CreateJournal("UmamiT-Journal-2026-11-18.csv"))
        viewModel.pickHandled()
        viewModel.onJournalDocument("content://csv/1")
        advanceUntilIdle()
        assertThat(backup.journals).containsExactly("content://csv/1")
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_csv_saved)
        viewModel.messageShown()

        backup.journalOk = false
        viewModel.onJournalDocument("content://csv/2")
        advanceUntilIdle()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_csv_failed)
        assertThat(backup.deleted).containsExactly("content://csv/2")

        viewModel.messageShown()
        viewModel.onJournalDocument(null)
        advanceUntilIdle()
        assertThat(backup.journals).hasSize(2)
        assertThat(viewModel.now.message).isNull()
    }

    @Test
    fun `the reminder switch only stores the value`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setReminder(false)
        advanceUntilIdle()
        assertThat(viewModel.now.reminderEnabled).isFalse()
        assertThat(viewModel.now.saveFailed).isFalse()

        backup.reminderWriteOk = false
        viewModel.setReminder(true)
        advanceUntilIdle()
        assertThat(viewModel.now.reminderEnabled).isFalse()
        assertThat(viewModel.now.saveFailed).isTrue()
    }
}
