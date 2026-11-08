package com.umamit.feature.collection.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.google.common.truth.Truth.assertThat
import com.umamit.core.designsystem.theme.UmamiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** VORRAT's stock history (INV-03, SCR-29, D-104; spec #171 seam 2): rows, the delete question, the brew link. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class TeaDetailHistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val deleted = mutableListOf<String>()
    private val opened = mutableListOf<String>()
    private val actions = TeaDetailActions(
        deleteStockEntry = { deleted += it },
        openSession = { opened += it },
    )
    private var alertKey by mutableStateOf<String?>(null)
    private var tea by mutableStateOf(TeaDetailScreenTest.sample().copy(history = HISTORY))

    private fun show() {
        compose.setContent {
            UmamiTheme(reduceMotion = true) {
                TeaDetailScreen(TeaDetailUiState.Ready(tea), actions, alertKey = alertKey, photo = { null })
            }
        }
        compose.waitForIdle()
    }

    private fun row(id: String) = compose.onNodeWithTag(teaDetailHistoryRowTag(id))

    private fun askDelete(id: String) {
        // By its action: scrolled to the viewport's lower edge, the pencil FAB covers the icon's centre.
        compose.onNodeWithTag(teaDetailHistoryDeleteTag(id)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun `the rows read newest first with kind, day, grams and price`() {
        show()

        val tags = compose.onAllNodes(historyRow, useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertThat(tags).containsExactly(
            teaDetailHistoryRowTag("a1"),
            teaDetailHistoryRowTag("b1"),
            teaDetailHistoryRowTag("p1"),
        ).inOrder()
        compose.onNodeWithText("Bestand korrigiert").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("−15 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("23. Okt. 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Gebrüht").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("−5 g").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Kauf").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+100 g · 18,90 €").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `TalkBack reads each row as one sentence`() {
        show()

        row("p1").assert(hasDescription("Kauf am 20. Okt. 2026: 100 g dazu für 18,90 €"))
        row("a1").assert(hasDescription("Bestand korrigiert am 23. Okt. 2026: 15 g weniger"))
        row("b1").assert(hasDescription("Gebrüht am 21. Okt. 2026: 5 g verbraucht"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }

    @Test
    fun `a purchase is deleted only after the confirmation`() {
        show()

        askDelete("p1")
        compose.onNodeWithText("Kauf löschen?").assertIsDisplayed()
        assertThat(deleted).isEmpty()
        compose.onNodeWithTag(HISTORY_DELETE_CONFIRM_TAG).performClick()
        compose.waitForIdle()

        assertThat(deleted).containsExactly("p1")
        compose.onNodeWithText("Kauf löschen?").assertDoesNotExist()
    }

    @Test
    fun `Abbrechen keeps a correction`() {
        show()

        askDelete("a1")
        compose.onNodeWithText("Korrektur löschen?").assertIsDisplayed()
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()

        assertThat(deleted).isEmpty()
        compose.onNodeWithText("Korrektur löschen?").assertDoesNotExist()
    }

    @Test
    fun `a brew row has no delete and opens its session`() {
        show()

        compose.onNodeWithTag(teaDetailHistoryDeleteTag("b1")).assertDoesNotExist()
        compose.onNodeWithTag(teaDetailHistoryRowTag("b1")).performScrollTo().assertHasClickAction().performClick()
        compose.waitForIdle()

        assertThat(opened).containsExactly("s1")
    }

    @Test
    fun `purchase and correction rows open nothing`() {
        show()

        compose.onNodeWithTag(teaDetailHistoryRowTag("p1")).assertHasNoClickAction()
        compose.onNodeWithTag(teaDetailHistoryRowTag("a1")).assertHasNoClickAction()
    }

    @Test
    fun `an alert removes the delete question for good (rule 8)`() {
        show()
        askDelete("p1")

        alertKey = "brew-1"
        compose.waitForIdle()
        compose.onNodeWithText("Kauf löschen?").assertDoesNotExist()
        alertKey = null
        compose.waitForIdle()

        compose.onNodeWithText("Kauf löschen?").assertDoesNotExist()
        assertThat(deleted).isEmpty()
    }

    @Test
    fun `a long history shows the newest five and the rest on Alle anzeigen`() {
        tea = tea.copy(
            history = (1..7).map {
                StockEntry("c$it", StockEntryKind.CORRECTION, -1.0, null, LocalDate.of(2026, 10, it))
            },
        )
        show()

        compose.onNodeWithTag(teaDetailHistoryRowTag("c5"), useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(teaDetailHistoryRowTag("c6"), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Alle 7 Einträge anzeigen").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(teaDetailHistoryRowTag("c7"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Alle 7 Einträge anzeigen").assertDoesNotExist()
    }

    @Test
    fun `without stock events there is no history`() {
        tea = tea.copy(history = emptyList(), hasStock = false)
        show()

        compose.onAllNodes(historyRow, useUnmergedTree = true)
            .fetchSemanticsNodes().let { assertThat(it).isEmpty() }
    }

    private val historyRow = SemanticsMatcher("a history row") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(HISTORY_ROW_PREFIX) == true
    }

    private fun hasDescription(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text))

    companion object {
        private const val HISTORY_ROW_PREFIX = "tea_detail_history_row_"

        /** A correction, a brew and a purchase, newest first (the view model's order). */
        val HISTORY = listOf(
            StockEntry("a1", StockEntryKind.CORRECTION, -15.0, null, LocalDate.of(2026, 10, 23)),
            StockEntry("b1", StockEntryKind.BREW, -5.0, null, LocalDate.of(2026, 10, 21), sessionId = "s1"),
            StockEntry("p1", StockEntryKind.PURCHASE, 100.0, 1890, LocalDate.of(2026, 10, 20)),
        )
    }
}
