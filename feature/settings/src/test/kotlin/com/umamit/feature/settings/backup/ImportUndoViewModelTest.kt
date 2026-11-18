package com.umamit.feature.settings.backup

import com.google.common.truth.Truth.assertThat
import com.umamit.core.backup.restore.RestoreRefusal
import com.umamit.core.backup.restore.RestoreResult
import com.umamit.feature.settings.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.umamit.core.backup.R as BackupR

/** *Rückgängig machen* in Start's snackbar after an import (BAK-02 AC6, AC7). */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportUndoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val backup = FakeDataBackup()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a done undo says so and the result clears once shown`() = runTest(dispatcher) {
        val viewModel = ImportUndoViewModel(backup)

        viewModel.undo()
        advanceUntilIdle()

        assertThat(backup.undoCalls).isEqualTo(1)
        assertThat(viewModel.result.value).isEqualTo(UndoResult(R.string.backup_undone))
        viewModel.resultShown()
        assertThat(viewModel.result.value).isNull()
    }

    @Test
    fun `a refused undo carries the refusal and a missing copy no text`() = runTest(dispatcher) {
        backup.undoResult = RestoreResult.Refused(RestoreRefusal.TIMER_ACTIVE)
        val viewModel = ImportUndoViewModel(backup)

        viewModel.undo()
        advanceUntilIdle()
        assertThat(viewModel.result.value).isEqualTo(UndoResult(BackupR.string.backup_refused_timer_active))

        backup.undoResult = RestoreResult.NoSafetyCopy
        viewModel.undo()
        advanceUntilIdle()
        assertThat(viewModel.result.value).isEqualTo(UndoResult(null))
    }
}
