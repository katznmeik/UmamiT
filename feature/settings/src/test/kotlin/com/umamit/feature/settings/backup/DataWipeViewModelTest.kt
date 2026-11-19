package com.umamit.feature.settings.backup

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.testing.FakeClock
import com.umamit.feature.settings.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** *Alle Daten löschen* (SET-33, D-131 and its #346 build notes) against fakes: nothing is ever deleted. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataWipeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backup = FakeDataBackup()
    private val wipe = FakeAppDataWipe()
    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): DataBackupViewModel {
        val factory = viewModelFactory { initializer { DataBackupViewModel(backup, FakeClock(wall = 0L), wipe) } }
        return ViewModelProvider.create(store, factory)[DataBackupViewModel::class].also { advanceUntilIdle() }
    }

    private val DataBackupViewModel.now get() = state.value

    private fun TestScope.askAndType(viewModel: DataBackupViewModel, text: String) {
        viewModel.askWipe()
        advanceUntilIdle()
        viewModel.typeConfirm(text)
    }

    @Test
    fun `the dialog opens with an empty field and Abbrechen deletes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        askAndType(viewModel, "")
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wipe(safetyCopy = false))
        assertThat(viewModel.now.canWipe).isFalse()

        viewModel.typeConfirm("LÖSCHEN")
        viewModel.dismissDialog()
        advanceUntilIdle()

        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.confirmText).isEmpty()
        viewModel.confirmWipe()
        advanceUntilIdle()
        assertThat(wipe.calls).isEqualTo(0)
    }

    @Test
    fun `only exactly LOESCHEN enables Loeschen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        askAndType(viewModel, "")

        for (wrong in listOf("löschen", "LÖSCHEN ", " LÖSCHEN", "LOESCHEN", "ERSETZEN", "LÖSCH")) {
            viewModel.typeConfirm(wrong)
            assertThat(viewModel.now.canWipe).isFalse()
            viewModel.confirmWipe()
            advanceUntilIdle()
            assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wipe(safetyCopy = false))
        }
        assertThat(wipe.calls).isEqualTo(0)

        viewModel.typeConfirm("LÖSCHEN")
        assertThat(viewModel.now.canWipe).isTrue()
    }

    @Test
    fun `confirming shows the notice for a second, then wipes once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        askAndType(viewModel, "LÖSCHEN")

        viewModel.confirmWipe()
        viewModel.confirmWipe()
        runCurrent()
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wiping)
        viewModel.dismissDialog()
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wiping)

        advanceTimeBy(DataBackupUiState.WIPE_NOTICE_MS - 1)
        runCurrent()
        assertThat(wipe.calls).isEqualTo(0)

        advanceUntilIdle()
        assertThat(wipe.calls).isEqualTo(1)
        // The system ends the app: the notice stays until then.
        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wiping)
        assertThat(viewModel.now.message).isNull()
    }

    @Test
    fun `a failed wipe closes the dialog with its snackbar`() = runTest(dispatcher) {
        wipe.result = WipeResult.Failed
        val viewModel = viewModel()
        askAndType(viewModel, "LÖSCHEN")

        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(wipe.calls).isEqualTo(1)
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_wipe_failed)
    }

    @Test
    fun `a timer the seam finds closes the dialog with the timer text`() = runTest(dispatcher) {
        wipe.result = WipeResult.TimerActive
        val viewModel = viewModel()
        askAndType(viewModel, "LÖSCHEN")

        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_timer_active)
    }

    @Test
    fun `while a timer is active nothing opens and the wipe is never called`() = runTest(dispatcher) {
        backup.timerActive.value = true
        val viewModel = viewModel()

        askAndType(viewModel, "LÖSCHEN")
        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(viewModel.now.canAskWipe).isFalse()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(wipe.calls).isEqualTo(0)
    }

    @Test
    fun `a timer started while the dialog is open blocks Loeschen`() = runTest(dispatcher) {
        val viewModel = viewModel()
        askAndType(viewModel, "LÖSCHEN")

        backup.timerActive.value = true
        advanceUntilIdle()
        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(viewModel.now.canWipe).isFalse()
        assertThat(wipe.calls).isEqualTo(0)
    }

    @Test
    fun `a timer started during the notice wins over the wipe`() = runTest(dispatcher) {
        val viewModel = viewModel()
        askAndType(viewModel, "LÖSCHEN")

        viewModel.confirmWipe()
        runCurrent()
        backup.timerActive.value = true
        advanceUntilIdle()

        assertThat(wipe.calls).isEqualTo(0)
        assertThat(viewModel.now.dialog).isNull()
        assertThat(viewModel.now.message).isEqualTo(R.string.backup_timer_active)
    }

    @Test
    fun `while an export runs the row is greyed out`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onBackupDocument("content://doc/1")
        advanceUntilIdle()
        assertThat(viewModel.now.export).isNotNull()

        askAndType(viewModel, "LÖSCHEN")
        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(viewModel.now.canAskWipe).isFalse()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(wipe.calls).isEqualTo(0)
    }

    @Test
    fun `while an import or an undo runs the row is greyed out`() = runTest(dispatcher) {
        backup.undoPreview = UndoPreview(sessions = 0, teas = 0, availableUntil = Long.MAX_VALUE)
        backup.undoGate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.askUndo()
        advanceUntilIdle()
        viewModel.confirmUndo()
        runCurrent()
        assertThat(viewModel.now.busy).isEqualTo(BackupBusy.UNDOING)

        askAndType(viewModel, "LÖSCHEN")
        viewModel.confirmWipe()
        advanceUntilIdle()

        assertThat(viewModel.now.canAskWipe).isFalse()
        assertThat(viewModel.now.dialog).isNull()
        assertThat(wipe.calls).isEqualTo(0)
        backup.undoGate?.complete(Unit)
    }

    @Test
    fun `with a safety copy the dialog names it`() = runTest(dispatcher) {
        val viewModel = viewModel()
        // The copy appears after the screen read it: the dialog reads it again.
        backup.undoPreview = UndoPreview(sessions = 3, teas = 1, availableUntil = Long.MAX_VALUE)

        askAndType(viewModel, "")

        assertThat(viewModel.now.dialog).isEqualTo(BackupDialog.Wipe(safetyCopy = true))
    }
}
