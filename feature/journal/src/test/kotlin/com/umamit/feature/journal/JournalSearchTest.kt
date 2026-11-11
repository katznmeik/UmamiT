package com.umamit.feature.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.umamit.core.data.JournalRepository
import com.umamit.core.data.JournalSession
import com.umamit.core.designsystem.theme.UmamiTheme
import com.umamit.core.model.SessionStatus
import com.umamit.core.model.TeaRating
import com.umamit.core.model.TeaTasting
import com.umamit.core.testing.FakeClock
import com.umamit.core.testing.FakeCollection
import com.umamit.core.testing.FakeSessions
import com.umamit.core.timer.controller.SavedSheetRequests
import com.umamit.feature.journal.JournalSessions.TODAY
import com.umamit.feature.journal.JournalSessions.session
import com.umamit.feature.journal.JournalSessions.step
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The journal's rating in rows, search and filters (SCR-15, JRN-08, JRN-09, D-112) through the screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class JournalSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private fun at(date: LocalDate, hour: Int) = LocalDateTime.of(date, LocalTime.of(hour, 0))

    private val sessions = listOf(
        session(
            "running",
            at(TODAY, 10),
            teaName = "Sencha Tanabata",
            status = SessionStatus.IN_PROGRESS,
            steps = listOf(step(0, 60, actualSeconds = null)),
        ),
        session("hisui", at(TODAY, 9), teaName = "Bio Sencha Hisui", rating = 4.5, notes = "Sehr frisch"),
        session("gyokuro", at(TODAY.minusDays(1), 18), teaName = "Gyokuro Asahi", rating = 3.0),
        session("plain", at(TODAY.minusDays(2), 7), teaName = "Bancha Kuki"),
    )

    private var alerting by mutableStateOf(false)

    private fun show(list: JournalList = journalList(sessions, TODAY), initial: JournalFilter = JournalFilter()) =
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                var filter by rememberSaveable(stateSaver = JournalFilter.Saver) { mutableStateOf(initial) }
                JournalScreen(
                    list = list,
                    onOpenSettings = {},
                    onOpenTimer = {},
                    filter = filter,
                    onFilterChange = { filter = it },
                    alerting = alerting,
                )
            }
        }

    @Test
    fun `a rated row shows the stars and TalkBack hears the rating`() {
        show()

        compose.onNodeWithText("★ 4,5").assertIsDisplayed()
        compose.onNodeWithText("★ 3,0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bewertung 4,5 von 5").assertExists()
    }

    @Test
    fun `an unrated session and one without notes carry no mark`() {
        show(journalList(listOf(sessions[3]), TODAY))

        compose.onNodeWithText("Bancha Kuki").assertIsDisplayed()
        compose.onNodeWithText("★", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(JOURNAL_FILTER_DOT_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `typing narrows the list and keeps the day groups`() {
        show()

        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("gyok")

        compose.onNodeWithText("Gyokuro Asahi").assertIsDisplayed()
        compose.onNodeWithText("GESTERN").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
        compose.onNodeWithText("HEUTE").assertDoesNotExist()
    }

    @Test
    fun `the search finds a note and ignores accents and case`() {
        show()

        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("SEHR FRISCH")

        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Gyokuro Asahi").assertDoesNotExist()
    }

    @Test
    fun `the pinned session hides while a search is active and comes back after`() {
        show()
        compose.onNodeWithText("LÄUFT GERADE").assertIsDisplayed()

        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("sencha")
        compose.onNodeWithText("LÄUFT GERADE").assertDoesNotExist()
        compose.onNodeWithText("Sencha Tanabata").assertDoesNotExist()

        compose.onNodeWithContentDescription("Suche leeren").performClick()
        compose.onNodeWithText("LÄUFT GERADE").assertIsDisplayed()
    }

    @Test
    fun `no match shows the empty state and Filter zurücksetzen restores the list`() {
        show()

        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("zzzz")
        compose.onNodeWithText("Keine Sitzung passt zu deiner Suche.").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()

        compose.onNodeWithText("Filter zurücksetzen").performClick()

        compose.onNodeWithText("Keine Sitzung passt zu deiner Suche.").assertDoesNotExist()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("LÄUFT GERADE").assertIsDisplayed()
    }

    @Test
    fun `the filter sheet sets a rating, shows a chip and a dot, and the chip removes it`() {
        show()

        compose.onNodeWithTag(JOURNAL_FILTER_BUTTON_TAG).performClick()
        compose.onNodeWithTag(JOURNAL_FILTER_SHEET_TAG).assertIsDisplayed()
        compose.onNode(hasText("ab 4 ★") and hasAnyAncestor(hasTestTag(JOURNAL_FILTER_SHEET_TAG))).performClick()
        compose.onNodeWithText("Fertig").performClick()

        compose.onNodeWithTag(JOURNAL_FILTER_SHEET_TAG).assertDoesNotExist()
        compose.onNodeWithTag(JOURNAL_FILTER_DOT_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Gyokuro Asahi").assertDoesNotExist()
        compose.onNodeWithText("LÄUFT GERADE").assertDoesNotExist()

        compose.onNodeWithContentDescription("Filter entfernen: ab 4 ★").performClick()

        compose.onNodeWithTag(JOURNAL_FILTER_DOT_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Gyokuro Asahi").assertIsDisplayed()
        compose.onNodeWithText("LÄUFT GERADE").assertIsDisplayed()
    }

    @Test
    fun `the sheet offers the teas that have sessions and Mit Notizen`() {
        show()

        compose.onNodeWithTag(JOURNAL_FILTER_BUTTON_TAG).performClick()
        val inSheet = hasAnyAncestor(hasTestTag(JOURNAL_FILTER_SHEET_TAG))
        compose.onNode(hasText("Gyokuro Asahi") and inSheet).assertIsDisplayed()
        compose.onNode(hasText("Bancha Kuki") and inSheet).assertIsDisplayed()
        compose.onNode(hasText("Sencha Tanabata") and inSheet).assertDoesNotExist()

        compose.onNode(hasText("Mit Notizen") and inSheet and hasClickAction()).performClick()
        compose.onNodeWithText("Fertig").performClick()

        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Gyokuro Asahi").assertDoesNotExist()
        compose.onNodeWithContentDescription("Filter entfernen: Mit Notizen").assertExists()
    }

    @Test
    fun `Zurücksetzen in the sheet clears the filters and keeps the search`() {
        show(initial = JournalFilter(search = "sencha", withNotes = true, minRating = 4.0))
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()

        compose.onNodeWithTag(JOURNAL_FILTER_BUTTON_TAG).performClick()
        compose.onNodeWithText("Zurücksetzen").performClick()
        compose.onNodeWithText("Fertig").performClick()

        compose.onNodeWithTag(JOURNAL_FILTER_DOT_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Bio Sencha Hisui").assertIsDisplayed()
        compose.onNodeWithText("Gyokuro Asahi").assertDoesNotExist()
    }

    @Test
    fun `a tea filter set from outside shows as a chip`() {
        show(initial = JournalFilter(teaKey = "t-gyokuro"))

        compose.onNodeWithContentDescription("Filter entfernen: Gyokuro Asahi").assertExists()
        // The chip and the one row.
        compose.onAllNodesWithText("Gyokuro Asahi").assertCountEquals(2)
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }

    @Test
    fun `the empty journal has no search field`() {
        show(JournalList.EMPTY)

        compose.onNodeWithText("Deine erste Tasse wartet.").assertIsDisplayed()
        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun `the filter sheet gives way to an alert`() {
        show()
        compose.onNodeWithTag(JOURNAL_FILTER_BUTTON_TAG).performClick()
        compose.onNodeWithTag(JOURNAL_FILTER_SHEET_TAG).assertIsDisplayed()

        alerting = true
        compose.waitForIdle()

        compose.onNodeWithTag(JOURNAL_FILTER_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun `search and filters survive the journal leaving the composition`() {
        var show by mutableStateOf(true)
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                val holder = rememberSaveableStateHolder()
                if (show) {
                    holder.SaveableStateProvider("journal") {
                        var filter by rememberSaveable(stateSaver = JournalFilter.Saver) {
                            mutableStateOf(JournalFilter())
                        }
                        JournalScreen(
                            list = journalList(sessions, TODAY),
                            onOpenSettings = {},
                            onOpenTimer = {},
                            filter = filter,
                            onFilterChange = { filter = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(JOURNAL_SEARCH_TAG).performTextInput("gyok")

        show = false
        compose.waitForIdle()
        show = true
        compose.waitForIdle()

        compose.onNodeWithText("Gyokuro Asahi").assertIsDisplayed()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }

    @Test
    fun `the journal tab opens with the tea filter from its argument`() {
        val clock = FakeClock(wall = JournalSessions.ms(LocalDateTime.of(2026, 10, 22, 12, 0)))
        val journal = object : JournalRepository {
            override fun observeSessions(): Flow<List<JournalSession>> = flowOf(sessions)

            override suspend fun deleteSession(sessionId: String) = Unit

            override fun observeTeaRatings(): Flow<Map<String, TeaRating>> = flowOf(emptyMap())

            override fun observeTeaTasting(teaId: String): Flow<TeaTasting> = flowOf(TeaTasting.EMPTY)
        }
        val sources = JournalSources(
            journal,
            FakeSessions().sessions,
            SavedSheetRequests(clock),
            clock,
            FakeCollection().teas,
        )
        compose.setContent {
            UmamiTheme(darkTheme = false, reduceMotion = true) {
                JournalRoute(sources = sources, onOpenSettings = {}, onOpenTimer = {}, initialTeaKey = "t-gyokuro")
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Filter entfernen: Gyokuro Asahi").assertExists()
        compose.onNodeWithText("Bio Sencha Hisui").assertDoesNotExist()
    }
}
