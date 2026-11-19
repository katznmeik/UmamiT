package com.umamit.feature.settings.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.testing.FakeClock
import com.umamit.feature.settings.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SCR-26's overlays (D-133): a dialog gives way to an alert it was not opened in and stays closed after it (Q2);
 * Start's undo covers the screen while it runs (Q5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataBackupOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `a dialog closes for good when an alert starts`() {
        val viewModel = DataBackupViewModel(FakeDataBackup(), FakeClock(), FakeAppDataWipe())
        var alertKey by mutableStateOf<String?>(null)
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                DataBackupRoute(onBack = {}, onImportFinish = {}, alertKey = alertKey, viewModel = viewModel)
            }
        }
        viewModel.askWipe()
        compose.waitForIdle()
        compose.onNodeWithTag(DataBackupTags.WIPE_FIELD).assertExists()

        alertKey = "brew-1"
        compose.waitForIdle()
        compose.onNodeWithTag(DataBackupTags.WIPE_FIELD).assertDoesNotExist()

        alertKey = null
        compose.waitForIdle()
        compose.onNodeWithTag(DataBackupTags.WIPE_FIELD).assertDoesNotExist()
        assertThat(viewModel.state.value.dialog).isNull()
    }

    @Test
    fun `a dialog opened during an alert stays for that alert`() {
        val viewModel = DataBackupViewModel(FakeDataBackup(), FakeClock(), FakeAppDataWipe())
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                DataBackupRoute(onBack = {}, onImportFinish = {}, alertKey = "brew-1", viewModel = viewModel)
            }
        }
        viewModel.askWipe()
        compose.waitForIdle()

        compose.onNodeWithTag(DataBackupTags.WIPE_FIELD).assertExists()
    }

    @Test
    fun `the undo on Start covers the screen until its result`() {
        var undoing by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                ImportedBackupSnackbar(
                    imported = ImportedBackup(sessions = 1, teas = 1),
                    onUndo = {},
                    onDone = {},
                    undoing = undoing,
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(str(R.string.backup_undoing)).assertIsDisplayed()

        undoing = false
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(str(R.string.backup_undoing)).assertDoesNotExist()
    }
}
