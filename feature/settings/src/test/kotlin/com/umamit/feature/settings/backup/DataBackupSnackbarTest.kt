package com.umamit.feature.settings.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.feature.settings.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.umamit.core.backup.R as BackupR

/** SCR-26's snackbars stay until they end, and only then are marked shown; Start's undo reports its result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataBackupSnackbarTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `a message stays on screen while the state clears it only after it ends`() {
        var state by mutableStateOf(DataBackupUiState(message = R.string.backup_saved))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                DataBackupScreen(
                    state = state,
                    alerting = false,
                    onBack = {},
                    actions = DataBackupActions(messageShown = { state = state.copy(message = null) }),
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText(str(R.string.backup_saved)).assertIsDisplayed()
        assertThat(state.message).isEqualTo(R.string.backup_saved)

        compose.mainClock.advanceTimeBy(10_000)
        assertThat(state.message).isNull()
    }

    @Test
    fun `the undo on Start shows its result before the snackbar is done`() {
        var imported by mutableStateOf<ImportedBackup?>(ImportedBackup(sessions = 142, teas = 23))
        var result by mutableStateOf<UndoResult?>(null)
        var done = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                imported?.let { now ->
                    ImportedBackupSnackbar(
                        imported = now,
                        onUndo = { result = UndoResult(R.string.backup_undone) },
                        onDone = {
                            done++
                            imported = null
                        },
                        result = result,
                        onResultEnd = { result = null },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Sicherung eingespielt · 142 Sessions, 23 Tees").assertIsDisplayed()

        compose.onNodeWithText(str(R.string.backup_undo_confirm)).performClick()
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText(str(R.string.backup_undone)).assertIsDisplayed()
        assertThat(done).isEqualTo(0)
        compose.mainClock.advanceTimeBy(10_000)
        assertThat(done).isEqualTo(1)
        assertThat(result).isNull()
    }

    @Test
    fun `a refused undo on Start shows the refusal`() {
        var result by mutableStateOf<UndoResult?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                ImportedBackupSnackbar(
                    imported = ImportedBackup(sessions = 1, teas = 1),
                    onUndo = { result = UndoResult(BackupR.string.backup_refused_timer_active) },
                    onDone = {},
                    result = result,
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Sicherung eingespielt · 1 Session, 1 Tee").assertIsDisplayed()

        compose.onNodeWithText(str(R.string.backup_undo_confirm)).performClick()
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText(str(BackupR.string.backup_refused_timer_active)).assertIsDisplayed()
    }
}
