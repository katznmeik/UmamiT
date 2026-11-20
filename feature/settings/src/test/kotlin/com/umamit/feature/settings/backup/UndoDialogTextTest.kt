package com.umamit.feature.settings.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.umamit.core.backup.restore.UndoPreview
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SCR-26's undo dialog drops a zero count from its sentence (BAK-02 AC7, PO 2026-11-18). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UndoDialogTextTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(sessions: Int, teas: Int) {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                UndoDialog(UndoPreview(sessions, teas, availableUntil = 0L), DataBackupActions())
            }
        }
    }

    private fun assertBody(tail: String) {
        compose.onNodeWithText("Deine Daten von vor dem Import kommen zurück. $tail").assertIsDisplayed()
    }

    @Test
    fun `new sessions and no changed teas drop the teas`() {
        show(sessions = 3, teas = 0)
        assertBody("Seit dem Import: 3 neue Sessions und alle anderen Änderungen – sie gehen verloren.")
    }

    @Test
    fun `changed teas and no new sessions drop the sessions`() {
        show(sessions = 0, teas = 2)
        assertBody("Seit dem Import: 2 geänderte Tees und alle anderen Änderungen – sie gehen verloren.")
    }

    @Test
    fun `singular forms stay`() {
        show(sessions = 1, teas = 1)
        assertBody(
            "Seit dem Import: 1 neue Session, 1 geänderter Tee und alle anderen Änderungen – sie gehen verloren.",
        )
    }

    @Test
    fun `neither keeps the sentence about no changes`() {
        show(sessions = 0, teas = 0)
        assertBody("Seit dem Import gibt es keine neuen Sessions und keine geänderten Tees.")
    }
}
