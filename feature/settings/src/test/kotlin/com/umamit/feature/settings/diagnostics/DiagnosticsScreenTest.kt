package com.umamit.feature.settings.diagnostics

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.umamit.core.data.diagnostics.DiagnosticsEntry
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * *Einstellungen › Diagnose* (SCR-27, 06 §5.6, M1-20): the log newest first,
 * *Kopieren* puts the whole text on the clipboard, an empty log says so, and
 * *Über* leads there (AboutTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class DiagnosticsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun show(state: DiagnosticsUiState) {
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                DiagnosticsScreen(state = state, onBack = {})
            }
        }
    }

    @Test
    fun `the entries are shown in the order of the log, newest first, with their times`() {
        show(DiagnosticsUiState(entries = ENTRIES, header = HEADER))

        compose.onNodeWithText("Diagnose").assertIsDisplayed()
        val messages = compose.onAllNodesWithTag(DiagnosticsTags.MESSAGE).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertThat(messages).containsExactly(ENTRIES[0].message, ENTRIES[1].message).inOrder()
        compose.onNodeWithText(ENTRIES[0].time).assertIsDisplayed()
    }

    @Test
    fun `Kopieren puts the header and every entry on the clipboard`() {
        show(DiagnosticsUiState(entries = ENTRIES, header = HEADER))

        compose.onNodeWithText("Kopieren").performClick()
        compose.waitForIdle()

        val clip = checkNotNull(context.getSystemService(ClipboardManager::class.java).primaryClip)
        assertThat(clip.getItemAt(0).text.toString()).isEqualTo(
            "UmamiT 0.1.0-rc.1 (10001) · Android 16 (API 36) · Samsung SM-S931B\n" +
                "2026-10-22 10:01:00.040 +02:00  event DeadlineReached from ALARM\n" +
                "2026-10-22 10:00:50.080 +02:00  event PreAlertReached from ALARM",
        )
    }

    @Test
    fun `an empty log says so and has nothing to copy`() {
        show(DiagnosticsUiState(entries = emptyList(), header = HEADER))

        compose.onNodeWithText("Noch keine Einträge").assertIsDisplayed()
        compose.onNodeWithText("Kopieren").assertIsNotEnabled()
    }

    private companion object {
        const val HEADER = "UmamiT 0.1.0-rc.1 (10001) · Android 16 (API 36) · Samsung SM-S931B"
        val ENTRIES = listOf(
            DiagnosticsEntry("2026-10-22 10:01:00.040 +02:00", "event DeadlineReached from ALARM"),
            DiagnosticsEntry("2026-10-22 10:00:50.080 +02:00", "event PreAlertReached from ALARM"),
        )
    }
}
